package com.segovia.tv

import android.content.ComponentName
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.ajustes.PlayerSettingsScreen
import com.segovia.tv.data.ContentRepository
import com.segovia.tv.detalles.MovieDetailsScreen
import com.segovia.tv.inicio.HomeScreen
import com.segovia.tv.kids.KidsScreen
import com.segovia.tv.model.Capitulo
import com.segovia.tv.model.PeliculaDrive
import com.segovia.tv.model.SeguirViendoItem
import com.segovia.tv.model.SerieDrive
import com.segovia.tv.model.Temporada
import com.segovia.tv.peliculas.PeliculasScreen
import com.segovia.tv.playback.ExternalPlayerLauncher
import com.segovia.tv.series.SeriesScreen
import com.segovia.tv.ui.NavigationUi
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "SegoviaVLCProgress"
        private const val INTERVAL = 2000L
    }

    private lateinit var repo: ContentRepository
    private lateinit var nav: NavigationUi
    private lateinit var player: ExternalPlayerLauncher
    private lateinit var home: HomeScreen
    private lateinit var peliculas: PeliculasScreen
    private lateinit var kids: KidsScreen
    private lateinit var series: SeriesScreen
    private lateinit var movieDetails: MovieDetailsScreen
    private lateinit var settings: PlayerSettingsScreen

    private val seguir = mutableListOf<SeguirViendoItem>()
    private var currentScreen = "Inicio"
    private var previousScreen = "Inicio"
    private var peliculaSeleccionada: PeliculaDrive? = null
    private var serieSeleccionada: SerieDrive? = null

    private var browser: MediaBrowserCompat? = null
    private var controller: MediaControllerCompat? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var polling = false
    private var lastUrl = ""
    private var lastPersist = 0L

    private val connection = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val b = browser ?: return
            try {
                controller = MediaControllerCompat(this@MainActivity, b.sessionToken)
                controller?.registerCallback(controllerCallback)
                iniciarPolling()
            } catch (e: Exception) {
                Log.e(TAG, "Error conectando MediaController", e)
            }
        }

        override fun onConnectionSuspended() {
            polling = false
        }

        override fun onConnectionFailed() {
            polling = false
        }
    }

    private val controllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            capturarProgreso(true)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            capturarProgreso(true)
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            capturarProgreso(true)
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!polling) return
            capturarProgreso(false)
            handler.postDelayed(this, INTERVAL)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iniciarVlc()
        player = ExternalPlayerLauncher(this)
        cargarSeguir()

        repo = ContentRepository(this) {
            runOnUiThread {
                when (currentScreen) {
                    "Inicio" -> showHome()
                    "Peliculas" -> showMovies()
                    "Series" -> showSeries()
                    "Kids" -> showKids()
                }
            }
        }

        nav = NavigationUi(this, { navigate(it) }, { finishAffinity() })

        home = HomeScreen(this, nav, { null }, repo::displayName, { repo.peliculas }, { seguir.toList() }, { openContinue(it) })
        peliculas = PeliculasScreen(this, nav, grid = null)
        kids = KidsScreen(this, nav, grid = null)
        series = SeriesScreen(this, nav, { null }, repo::displayName, player, { seguir.toList() }) { s, t, n, c -> guardarCapitulo(s, t, n, c) }
        movieDetails = MovieDetailsScreen(this, nav, { null }, repo::displayName, player) { guardarPelicula(it) }
        settings = PlayerSettingsScreen(this, nav, player) { showPrevious() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        repo.loadCache()
        showHome()
        repo.refreshAsync()
    }

    private fun iniciarVlc() {
        try {
            val component = ComponentName(this, "org.videolan.vlc.PlaybackService")
            browser = MediaBrowserCompat(this, component, connection, null)
            browser?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando VLC", e)
        }
    }

    private fun iniciarPolling() {
        if (polling) return
        polling = true
        handler.post(poll)
    }

    private fun detenerVlc() {
        polling = false
        handler.removeCallbacks(poll)
        try { controller?.unregisterCallback(controllerCallback) } catch (_: Exception) {}
        controller = null
        try { browser?.disconnect() } catch (_: Exception) {}
        browser = null
    }

    private fun capturarProgreso(forzar: Boolean) {
        val c = controller ?: return
        val state = c.playbackState ?: return
        val queue = c.queue ?: return
        val id = state.activeQueueItemId
        if (id < 0) return
        val item = queue.firstOrNull { it.queueId == id } ?: return
        val uri = item.description?.mediaUri?.toString()?.trim().orEmpty()
        if (uri.isBlank()) return

        val url = normalizeUrl(uri)
        val position = state.position.coerceAtLeast(0L)
        val duration = c.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val now = System.currentTimeMillis()
        val changed = url != lastUrl

        if (!forzar && !changed && now - lastPersist < 5000L) return
        lastUrl = url

        guardarProgreso(url, position, duration)
    }

    private fun normalizeUrl(raw: String): String {
        val u = raw.trim()
        if (u.isBlank()) return ""
        if (u.startsWith("/video?")) return "https://segovia-tv-proxy.guadianesgalaxi.workers.dev$u"
        if (u.startsWith("video?")) return "https://segovia-tv-proxy.guadianesgalaxi.workers.dev/$u"
        return u
    }

    private fun guardarProgreso(url: String, position: Long, duration: Long) {
        var encontrado: Triple<SerieDrive, Temporada, Pair<Int, Capitulo>>? = null

        for (s in repo.series) {
            for (t in s.temporadas) {
                t.capitulos.forEachIndexed { index, c ->
                    if (normalizeUrl(c.streamUrl) == url) {
                        encontrado = Triple(s, t, index to c)
                        return@forEachIndexed
                    }
                }
                if (encontrado != null) break
            }
            if (encontrado != null) break
        }

        val e = encontrado ?: return
        val s = e.first
        val t = e.second
        val n = e.third.first + 1
        val cap = e.third.second
        val progreso = if (duration > 0 && position >= duration - 3000L) duration else position
        val old = seguir.indexOfFirst { normalizeUrl(it.streamUrl) == url }

        val item = SeguirViendoItem(
            tipo = "serie",
            titulo = s.titulo,
            subtitulo = "${t.titulo} • Capítulo $n",
            posterUrl = s.posterUrl,
            bannerUrl = s.bannerUrl,
            sinopsis = cap.sinopsis,
            streamUrl = cap.streamUrl,
            temporada = t.numero,
            capitulo = n,
            progreso = progreso.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )

        if (old >= 0) seguir[old] = item else seguir.add(0, item)
        while (seguir.size > 12) seguir.removeAt(seguir.lastIndex)
        guardarLista()
        lastPersist = System.currentTimeMillis()
    }

    private fun cargarSeguir() {
        try {
            val json = getSharedPreferences("seguir_viendo", MODE_PRIVATE).getString("items", "[]") ?: "[]"
            val a = JSONArray(json)
            seguir.clear()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                seguir.add(SeguirViendoItem(o.optString("tipo"), o.optString("titulo"), o.optString("subtitulo"), o.optString("posterUrl"), o.optString("bannerUrl"), o.optString("sinopsis"), o.optString("streamUrl"), o.optInt("temporada"), o.optInt("capitulo"), o.optInt("progreso")))
            }
        } catch (_: Exception) {}
    }

    private fun guardarLista() {
        val a = JSONArray()
        seguir.forEach {
            a.put(JSONObject().apply {
                put("tipo", it.tipo); put("titulo", it.titulo); put("subtitulo", it.subtitulo)
                put("posterUrl", it.posterUrl); put("bannerUrl", it.bannerUrl); put("sinopsis", it.sinopsis)
                put("streamUrl", it.streamUrl); put("temporada", it.temporada); put("capitulo", it.capitulo); put("progreso", it.progreso)
            })
        }
        getSharedPreferences("seguir_viendo", MODE_PRIVATE).edit().putString("items", a.toString()).apply()
    }

    private fun guardarPelicula(p: PeliculaDrive) {
        seguir.removeAll { it.streamUrl == p.streamUrl }
        seguir.add(0, SeguirViendoItem("pelicula", p.titulo, "", p.posterUrl, p.bannerUrl, p.sinopsis, p.streamUrl, 0, 0, p.progreso))
        while (seguir.size > 12) seguir.removeAt(seguir.lastIndex)
        guardarLista()
    }

    private fun guardarCapitulo(s: SerieDrive, t: Temporada, n: Int, c: Capitulo) {
        val old = seguir.firstOrNull { normalizeUrl(it.streamUrl) == normalizeUrl(c.streamUrl) }?.progreso ?: 0
        seguir.removeAll { normalizeUrl(it.streamUrl) == normalizeUrl(c.streamUrl) }
        seguir.add(0, SeguirViendoItem("serie", s.titulo, "${t.titulo} • Capítulo $n", s.posterUrl, s.bannerUrl, c.sinopsis, c.streamUrl, t.numero, n, old))
        while (seguir.size > 12) seguir.removeAt(seguir.lastIndex)
        guardarLista()
    }

    private fun openContinue(item: SeguirViendoItem) {
        if (item.tipo == "serie") {
            val s = repo.series.firstOrNull { it.titulo.equals(item.titulo, true) }
            if (s != null) {
                serieSeleccionada = s
                previousScreen = currentScreen
                currentScreen = "DetallesSerie"
                series.showDetail(s)
                return
            }
        }

        val p = repo.peliculas.firstOrNull { it.streamUrl == item.streamUrl }
        if (p != null) {
            peliculaSeleccionada = p
            previousScreen = currentScreen
            currentScreen = "DetallesPeli"
            movieDetails.show(p)
            return
        }

        player.play(item.streamUrl, item.titulo)
    }

    private fun navigate(route: String) {
        when (route) {
            "Inicio" -> showHome()
            "Peliculas" -> showMovies()
            "Series" -> showSeries()
            "Kids" -> showKids()
            "Ajustes" -> showSettings()
        }
    }

    private fun showHome() { previousScreen = currentScreen; currentScreen = "Inicio"; home.show() }
    private fun showMovies() { previousScreen = currentScreen; currentScreen = "Peliculas"; peliculas.show(repo.peliculas) }
    private fun showKids() { previousScreen = currentScreen; currentScreen = "Kids"; kids.show(repo.kidsItems) }
    private fun showSeries() { previousScreen = currentScreen; currentScreen = "Series"; series.showGrid(repo.series) { openSeries(it) } }

    private fun openMovie(p: PeliculaDrive) {
        peliculaSeleccionada = p; serieSeleccionada = null; previousScreen = currentScreen; currentScreen = "DetallesPeli"; movieDetails.show(p)
    }

    private fun openSeries(s: SerieDrive) {
        serieSeleccionada = s; peliculaSeleccionada = null; previousScreen = currentScreen; currentScreen = "DetallesSerie"; series.showDetail(s)
    }

    private fun showSettings() { previousScreen = currentScreen; currentScreen = "Ajustes"; settings.show() }

    private fun showPrevious() {
        when (previousScreen) {
            "Peliculas" -> showMovies()
            "Series" -> showSeries()
            "Kids" -> showKids()
            "DetallesPeli" -> peliculaSeleccionada?.let { movieDetails.show(it) } ?: showHome()
            "DetallesSerie" -> serieSeleccionada?.let { series.showDetail(it) } ?: showHome()
            else -> showHome()
        }
    }

    private fun goBack() {
        when (currentScreen) {
            "Inicio" -> finish()
            "DetallesPeli" -> if (previousScreen == "Peliculas") showMovies() else showHome()
            "DetallesSerie" -> if (previousScreen == "Series") showSeries() else showHome()
            "Peliculas", "Series", "Kids", "Ajustes" -> showHome()
            else -> showHome()
        }
    }

    override fun onResume() {
        super.onResume()
        capturarProgreso(true)
    }

    override fun onDestroy() {
        capturarProgreso(true)
        detenerVlc()
        super.onDestroy()
    }
}
