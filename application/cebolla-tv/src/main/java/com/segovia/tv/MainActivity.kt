package com.segovia.tv

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.ajustes.PlayerSettingsScreen
import com.segovia.tv.data.ContentRepository
import com.segovia.tv.detalles.MovieDetailsScreen
import com.segovia.tv.grilla.GridScreen
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
import org.videolan.vlc.PlaybackService

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG_VLC = "SegoviaVLCProgress"
        private const val VLC_PROGRESS_INTERVAL = 2000L
        private const val WORKER = "https://segovia-tv-proxy.guadianesgalaxi.workers.dev"
    }

    // ============================================================
    // VLC INTEGRADO
    // ============================================================

    private var vlcBrowser: MediaBrowserCompat? = null
    private var vlcController: MediaControllerCompat? = null
    private var vlcConnected = false
    private val vlcHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var vlcPolling = false
    private var ultimaUrlVlc = ""
    private var ultimoGuardadoUrl = ""
    private var ultimoGuardadoMs = 0L
    private var ultimaPersistencia = 0L

    private val vlcConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val browser = vlcBrowser ?: return
            try {
                vlcController = MediaControllerCompat(this@MainActivity, browser.sessionToken)
                vlcController?.registerCallback(vlcControllerCallback)
                vlcConnected = true
                Log.d(TAG_VLC, "VLC integrado conectado correctamente")
                iniciarPollingVlc()
            } catch (e: Exception) {
                Log.e(TAG_VLC, "Error creando MediaController VLC", e)
            }
        }

        override fun onConnectionSuspended() {
            vlcConnected = false
            Log.d(TAG_VLC, "Conexión VLC suspendida")
        }

        override fun onConnectionFailed() {
            vlcConnected = false
            Log.d(TAG_VLC, "No se pudo conectar con PlaybackService VLC")
        }
    }

    private val vlcControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            capturarProgresoVlc(false)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            capturarProgresoVlc(true)
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            capturarProgresoVlc(true)
        }
    }

    private val vlcPollRunnable = object : Runnable {
        override fun run() {
            if (!vlcConnected) {
                vlcPolling = false
                return
            }
            capturarProgresoVlc(false)
            vlcHandler.postDelayed(this, VLC_PROGRESS_INTERVAL)
        }
    }

    // ============================================================
    // UI / DATOS
    // ============================================================

    private var currentScreen = "Inicio"
    private var previousScreen = "Inicio"
    private var peliculaSeleccionada: PeliculaDrive? = null
    private var serieSeleccionada: SerieDrive? = null
    private lateinit var repo: ContentRepository
    private lateinit var nav: NavigationUi
    private lateinit var player: ExternalPlayerLauncher
    private lateinit var grid: GridScreen
    private lateinit var home: HomeScreen
    private lateinit var peliculas: PeliculasScreen
    private lateinit var kids: KidsScreen
    private lateinit var series: SeriesScreen
    private lateinit var movieDetails: MovieDetailsScreen
    private lateinit var settings: PlayerSettingsScreen
    private val seguir = mutableListOf<SeguirViendoItem>()
    private var touchTarget: View? = null

    // ============================================================
    // CICLO DE VIDA
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iniciarVlcIntegrado()
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

        nav = NavigationUi(this, { route -> navigate(route) }, { finishAffinity() })

        grid = GridScreen(
            this,
            nav,
            { null },
            repo::displayName,
            { openMovie(it) },
            { openSeries(it) }
        )

        home = HomeScreen(
            this,
            nav,
            { null },
            repo::displayName,
            { seguir.toList() },
            { openContinue(it) }
        )

        peliculas = PeliculasScreen(this, nav, grid)
        kids = KidsScreen(this, nav, grid)

        series = SeriesScreen(
            this,
            nav,
            { null },
            repo::displayName,
            player,
            { seguir.toList() }
        ) { s, t, n, c ->
            guardarCapitulo(s, t, n, c)
        }

        movieDetails = MovieDetailsScreen(
            this,
            nav,
            { null },
            repo::displayName,
            player
        ) {
            guardarPelicula(it)
        }

        settings = PlayerSettingsScreen(this, nav, player) {
            showPrevious()
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goBack()
                }
            }
        )

        repo.loadCache()
        showHome()
        repo.refreshAsync()
    }

    override fun onResume() {
        super.onResume()
        capturarProgresoVlc(true)
        refrescarPantallaActual()
    }

    override fun onDestroy() {
        capturarProgresoVlc(true)
        detenerVlcIntegrado()
        super.onDestroy()
    }

    // ============================================================
    // VLC INTEGRADO
    // ============================================================

    private fun iniciarVlcIntegrado() {
        if (vlcBrowser != null) return

        try {
            val component = ComponentName(this, PlaybackService::class.java)
            val browser = MediaBrowserCompat(this, component, vlcConnectionCallback, null)
            vlcBrowser = browser
            browser.connect()
            Log.d(TAG_VLC, "Intentando conectar con PlaybackService integrado")
        } catch (e: Exception) {
            Log.e(TAG_VLC, "ERROR conectando con PlaybackService integrado", e)
        }
    }

    private fun iniciarPollingVlc() {
        if (vlcPolling) return
        vlcPolling = true
        vlcHandler.post(vlcPollRunnable)
    }

    private fun detenerVlcIntegrado() {
        vlcPolling = false
        vlcHandler.removeCallbacks(vlcPollRunnable)

        try {
            vlcController?.unregisterCallback(vlcControllerCallback)
        } catch (_: Exception) {
        }

        vlcController = null

        try {
            vlcBrowser?.disconnect()
        } catch (_: Exception) {
        }

        vlcBrowser = null
        vlcConnected = false
    }

    // ============================================================
    // CAPTURA DE PROGRESO
    // ============================================================

    private fun capturarProgresoVlc(forzar: Boolean) {
        val controller = vlcController ?: return
        val state = controller.playbackState ?: return
        val metadata = controller.metadata
        val queue = controller.queue

        var uri = ""
        val activeId = state.activeQueueItemId

        if (queue != null && activeId >= 0) {
            val item = queue.firstOrNull { it.queueId == activeId }
            if (item != null) {
                uri = item.description?.mediaUri?.toString()?.trim().orEmpty()
            }
        }

        if (uri.isBlank()) {
            uri = metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI)?.trim().orEmpty()
        }

        if (uri.isBlank()) return

        val position = state.position.coerceAtLeast(0L)
        val duration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L

        if (position <= 0L && duration <= 0L) return

        val urlNormalizada = normalizeProgressUrl(uri)
        val ahora = System.currentTimeMillis()
        val cambioDeMedia = urlNormalizada != ultimaUrlVlc

        if (!forzar && !cambioDeMedia && ahora - ultimaPersistencia < 5000L) return

        ultimaUrlVlc = urlNormalizada

        Log.d(
            TAG_VLC,
            "VLC actual: uri=$urlNormalizada position=$position duration=$duration"
        )

        guardarProgresoCapitulo(uri, position, duration, forzar)
    }

    private fun normalizeProgressUrl(raw: String): String {
        val url = raw.trim()
        if (url.isBlank()) return ""

        if (url.startsWith("/video?")) return WORKER + url
        if (url.startsWith("video?")) return "$WORKER/$url"

        if (url.startsWith("undefined/video?", ignoreCase = true)) {
            return WORKER + "/" + url.substringAfter("undefined/")
        }

        if (url.startsWith("null/video?", ignoreCase = true)) {
            return WORKER + "/" + url.substringAfter("null/")
        }

        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.contains("/")) {
            return "$WORKER/video?id=$url"
        }

        return url
    }

    // ============================================================
    // BÚSQUEDA DEL CAPÍTULO
    // ============================================================

    private data class CapituloEncontrado(
        val serie: SerieDrive,
        val temporada: Temporada,
        val numero: Int,
        val capitulo: Capitulo
    )

    private fun buscarCapituloPorUrl(uri: String): CapituloEncontrado? {
        val objetivo = normalizeProgressUrl(uri)
        if (objetivo.isBlank()) return null

        for (serie in repo.series) {
            for (temporada in serie.temporadas) {
                for ((index, capitulo) in temporada.capitulos.withIndex()) {
                    val capUrl = normalizeProgressUrl(capitulo.streamUrl)

                    if (capUrl.isNotBlank() && capUrl == objetivo) {
                        return CapituloEncontrado(
                            serie,
                            temporada,
                            index + 1,
                            capitulo
                        )
                    }
                }
            }
        }

        return null
    }

    // ============================================================
    // GUARDAR PROGRESO
    // ============================================================

    private fun guardarProgresoCapitulo(
        uri: String,
        positionMs: Long,
        durationMs: Long,
        forzar: Boolean
    ) {
        val encontrado = buscarCapituloPorUrl(uri)

        if (encontrado == null) {
            Log.d(TAG_VLC, "No se encontró capítulo para URI: $uri")
            return
        }

        val serie = encontrado.serie
        val temporada = encontrado.temporada
        val numero = encontrado.numero
        val capitulo = encontrado.capitulo

        val progresoFinal = if (
            durationMs > 0L &&
            positionMs >= durationMs - 3000L
        ) {
            durationMs
        } else {
            positionMs
        }

        val progresoInt = progresoFinal
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        val urlCatalogo = capitulo.streamUrl

        var index = seguir.indexOfFirst {
            it.tipo == "serie" &&
                normalizeProgressUrl(it.streamUrl) ==
                normalizeProgressUrl(urlCatalogo)
        }

        if (index == -1) {
            index = seguir.indexOfFirst {
                it.tipo == "serie" &&
                    it.titulo.equals(serie.titulo, ignoreCase = true) &&
                    it.temporada == temporada.numero &&
                    it.capitulo == numero
            }
        }

        val subtitulo = if (numero > 0) {
            "${temporada.titulo} • Capítulo $numero"
        } else {
            temporada.titulo
        }

        if (index >= 0) {
            val viejo = seguir[index]

            seguir[index] = viejo.copy(
                tipo = "serie",
                titulo = serie.titulo,
                subtitulo = subtitulo,
                posterUrl = if (serie.posterUrl.isNotBlank()) serie.posterUrl else viejo.posterUrl,
                bannerUrl = if (serie.bannerUrl.isNotBlank()) serie.bannerUrl else viejo.bannerUrl,
                sinopsis = if (capitulo.sinopsis.isNotBlank()) capitulo.sinopsis else viejo.sinopsis,
                streamUrl = urlCatalogo,
                temporada = temporada.numero,
                capitulo = numero,
                progreso = progresoInt
            )
        } else {
            seguir.add(
                0,
                SeguirViendoItem(
                    tipo = "serie",
                    titulo = serie.titulo,
                    subtitulo = subtitulo,
                    posterUrl = serie.posterUrl,
                    bannerUrl = serie.bannerUrl,
                    sinopsis = capitulo.sinopsis,
                    streamUrl = urlCatalogo,
                    temporada = temporada.numero,
                    capitulo = numero,
                    progreso = progresoInt
                )
            )
        }

        while (seguir.size > 12) {
            seguir.removeAt(seguir.lastIndex)
        }

        ultimoGuardadoUrl = normalizeProgressUrl(urlCatalogo)
        ultimoGuardadoMs = progresoFinal
        ultimaPersistencia = System.currentTimeMillis()

        guardarLista()

        Log.d(
            TAG_VLC,
            "PROGRESO GUARDADO: ${serie.titulo} T${temporada.numero} C$numero $progresoFinal ms"
        )
    }

    private fun refrescarPantallaActual() {
        runOnUiThread {
            when (currentScreen) {
                "Inicio" -> showHome()
                "DetallesSerie" -> serieSeleccionada?.let { series.showDetail(it) }
                "Series" -> showSeries()
                "DetallesPeli" -> peliculaSeleccionada?.let { movieDetails.show(it) }
            }
        }
    }

    // ============================================================
    // NAVEGACIÓN
    // ============================================================

    fun navigate(route: String) {
        when (route) {
            "Inicio" -> showHome()
            "Peliculas" -> showMovies()
            "Series" -> showSeries()
            "Kids" -> showKids()
            "Ajustes" -> showSettings()
        }
    }

    private fun showLoading() {
        setContentView(
            android.widget.FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.parseColor("#080C14"))
                addView(
                    android.widget.ProgressBar(this@MainActivity),
                    android.widget.FrameLayout.LayoutParams(-2, -2).apply {
                        gravity = android.view.Gravity.CENTER
                    }
                )
            }
        )
    }

    private fun showHome() {
        previousScreen = currentScreen
        currentScreen = "Inicio"
        peliculaSeleccionada = null
        serieSeleccionada = null
        home.show()
    }

    private fun showMovies() {
        previousScreen = currentScreen
        currentScreen = "Peliculas"
        peliculas.show(repo.peliculas)
    }

    private fun showKids() {
        previousScreen = currentScreen
        currentScreen = "Kids"
        kids.show(repo.kidsItems)
    }

    private fun showSeries() {
        previousScreen = currentScreen
        currentScreen = "Series"
        series.showGrid(repo.series) { openSeries(it) }
    }

    private fun openMovie(p: PeliculaDrive) {
        previousScreen = currentScreen
        currentScreen = "DetallesPeli"
        peliculaSeleccionada = p
        serieSeleccionada = null
        movieDetails.show(p)
    }

    private fun openSeries(s: SerieDrive) {
        previousScreen = currentScreen
        currentScreen = "DetallesSerie"
        serieSeleccionada = s
        peliculaSeleccionada = null
        series.showDetail(s)
    }

    private fun showSettings() {
        previousScreen = currentScreen
        currentScreen = "Ajustes"
        settings.show()
    }

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
            "DetallesPeli" -> {
                if (previousScreen == "Kids") showKids()
                else if (previousScreen == "Peliculas") showMovies()
                else showHome()
            }

            "DetallesSerie" -> {
                if (previousScreen == "Series") showSeries()
                else showHome()
            }

            "Peliculas", "Series", "Kids", "Ajustes" -> showHome()
            "Inicio" -> finish()
            else -> showHome()
        }
    }

    // ============================================================
    // SEGUIR VIENDO
    // ============================================================

    private fun cargarSeguir() {
        try {
            val json = getSharedPreferences("seguir_viendo", MODE_PRIVATE)
                .getString("items", "[]") ?: "[]"

            val a = JSONArray(json)
            seguir.clear()

            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)

                seguir.add(
                    SeguirViendoItem(
                        o.optString("tipo"),
                        o.optString("titulo"),
                        o.optString("subtitulo"),
                        o.optString("posterUrl"),
                        o.optString("bannerUrl"),
                        o.optString("sinopsis"),
                        o.optString("streamUrl"),
                        o.optInt("temporada"),
                        o.optInt("capitulo"),
                        o.optInt("progreso")
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun guardarLista() {
        val a = JSONArray()

        seguir.forEach {
            a.put(
                JSONObject().apply {
                    put("tipo", it.tipo)
                    put("titulo", it.titulo)
                    put("subtitulo", it.subtitulo)
                    put("posterUrl", it.posterUrl)
                    put("bannerUrl", it.bannerUrl)
                    put("sinopsis", it.sinopsis)
                    put("streamUrl", it.streamUrl)
                    put("temporada", it.temporada)
                    put("capitulo", it.capitulo)
                    put("progreso", it.progreso)
                }
            )
        }

        getSharedPreferences("seguir_viendo", MODE_PRIVATE)
            .edit()
            .putString("items", a.toString())
            .apply()
    }

    private fun guardarPelicula(p: PeliculaDrive) {
        val item = SeguirViendoItem(
            tipo = "pelicula",
            titulo = p.titulo,
            posterUrl = p.posterUrl,
            bannerUrl = p.bannerUrl,
            sinopsis = p.sinopsis,
            streamUrl = p.streamUrl,
            progreso = p.progreso
        )

        seguir.removeAll { it.streamUrl == p.streamUrl }
        seguir.add(0, item)

        while (seguir.size > 12) {
            seguir.removeAt(seguir.lastIndex)
        }

        guardarLista()
    }

    private fun guardarCapitulo(
        s: SerieDrive,
        t: Temporada,
        n: Int,
        c: Capitulo
    ) {
        val progresoAnterior = seguir.firstOrNull {
            it.tipo == "serie" &&
                (
                    normalizeProgressUrl(it.streamUrl) ==
                    normalizeProgressUrl(c.streamUrl) ||
                    (
                        it.titulo.equals(s.titulo, ignoreCase = true) &&
                            it.temporada == t.numero &&
                            it.capitulo == n
                    )
                )
        }?.progreso ?: 0

        val item = SeguirViendoItem(
            tipo = "serie",
            titulo = s.titulo,
            subtitulo = "${t.titulo} • Capítulo $n",
            posterUrl = s.posterUrl,
            bannerUrl = s.bannerUrl,
            sinopsis = c.sinopsis,
            streamUrl = c.streamUrl,
            temporada = t.numero,
            capitulo = n,
            progreso = progresoAnterior
        )

        seguir.removeAll {
            it.streamUrl == c.streamUrl ||
                (
                    it.tipo == "serie" &&
                        it.titulo.equals(s.titulo, ignoreCase = true) &&
                        it.temporada == t.numero &&
                        it.capitulo == n
                )
        }

        seguir.add(0, item)

        while (seguir.size > 12) {
            seguir.removeAt(seguir.lastIndex)
        }

        guardarLista()
    }

    // ============================================================
    // CONTINUAR VIENDO
    // ============================================================

    private fun openContinue(item: SeguirViendoItem) {
        if (item.tipo == "serie") {
            val s = repo.series.firstOrNull {
                it.titulo == item.titulo
            } ?: repo.kidsItems
                .filterIsInstance<com.segovia.tv.model.KidsItem.Series>()
                .map { it.data }
                .firstOrNull {
                    it.titulo == item.titulo
                }

            if (s != null) {
                serieSeleccionada = s
                previousScreen = "Inicio"
                currentScreen = "DetallesSerie"
                series.showDetail(s)
                return
            }
        }

        val p = repo.peliculas.firstOrNull {
            it.streamUrl == item.streamUrl
        } ?: repo.kidsItems
            .filterIsInstance<com.segovia.tv.model.KidsItem.Movie>()
            .map { it.data }
            .firstOrNull {
                it.streamUrl == item.streamUrl
            }

        if (p != null) {
            previousScreen = "Inicio"
            currentScreen = "DetallesPeli"
            peliculaSeleccionada = p
            movieDetails.show(p)
            return
        }

        player.play(item.streamUrl, item.titulo)
    }

    // ============================================================
    // TOUCH / TV
    // ============================================================

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchTarget = null

                val root = window.decorView as? ViewGroup
                val titles = arrayOf("INICIO", "PELICULAS", "SERIES", "KIDS", "AJUSTES", "SALIR")

                if (root != null) {
                    for (t in titles) {
                        val v = findTabByTitle(root, t) ?: continue
                        val r = Rect()

                        if (
                            v.getGlobalVisibleRect(r) &&
                            event.rawX >= r.left &&
                            event.rawX < r.right &&
                            event.rawY >= r.top &&
                            event.rawY < r.bottom &&
                            v.isShown &&
                            v.isEnabled
                        ) {
                            touchTarget = v
                            return true
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                val v = touchTarget
                touchTarget = null

                if (v != null && v.isShown && v.isEnabled) {
                    v.performClick()
                    return true
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                touchTarget = null
                return true
            }
        }

        return super.dispatchTouchEvent(event)
    }

    private fun findTabByTitle(parent: ViewGroup, title: String): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)

            if (child is ViewGroup) {
                for (j in 0 until child.childCount) {
                    val sub = child.getChildAt(j)

                    if (
                        sub is android.widget.TextView &&
                        sub.text?.toString() == title
                    ) {
                        return child
                    }
                }

                findTabByTitle(child, title)?.let {
                    return it
                }
            }
        }

        return null
    }
}
