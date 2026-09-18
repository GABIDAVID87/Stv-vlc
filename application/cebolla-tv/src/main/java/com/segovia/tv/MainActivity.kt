package com.segovia.tv

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Build
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

class MainActivity : AppCompatActivity() {
    // ============================================================
    // BLOQUE MEDIASESSION — RCTV
    // ============================================================
    private val rctvPackages = arrayOf(
        "com.bls.vlc.simple.debug",
        "com.bls.vlc.simple"
    )
    private val rctvServiceName = "org.videolan.vlc.PlaybackService"
    private var rctvBrowser: MediaBrowserCompat? = null
    private var rctvController: MediaControllerCompat? = null
    private var rctvConnected = false
    private val rctvHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var rctvPolling = false

    private val rctvConnectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            val browser = rctvBrowser ?: return
            try {
                rctvController = MediaControllerCompat(this@MainActivity, browser.sessionToken)
                rctvController?.registerCallback(rctvControllerCallback)
                rctvConnected = true
                Log.d("SegoviaRCTV", "MediaBrowser conectado a RCTV")
                iniciarPollingRctv()
            } catch (e: Exception) {
                Log.e("SegoviaRCTV", "No se pudo crear MediaController: ${e.message}", e)
            }
        }

        override fun onConnectionSuspended() {
            rctvConnected = false
            Log.d("SegoviaRCTV", "MediaBrowser suspendido")
        }

        override fun onConnectionFailed() {
            rctvConnected = false
            Log.d("SegoviaRCTV", "MediaBrowser no pudo conectar")
        }
    }

    private val rctvControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            actualizarProgresoDesdeRctv()
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            actualizarProgresoDesdeRctv()
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            actualizarProgresoDesdeRctv()
        }
    }


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

    override fun onDestroy() {
        super.onDestroy()
        detenerMediaSessionRctv()
    }

    // ============================================================
    // RCTV — RESULTADO DE PROGRESO SIN BROADCAST
    // ============================================================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ExternalPlayerLauncher.REQUEST_RCTV_PROGRESS) return
        val json = data?.getStringExtra(ExternalPlayerLauncher.EXTRA_SEGOVIA_PROGRESS_JSON).orEmpty()
        if (json.isBlank()) return
        recibirProgresoRctv(json)
    }

    private fun recibirProgresoRctv(json: String) {
        try {
            val a = JSONArray(json)
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val uri = o.optString("uri").trim()
                val seriesTitle = o.optString("series_title").trim()
                val season = o.optInt("season", -1)
                val episode = o.optInt("episode", -1)
                val title = o.optString("title").trim()
                val poster = o.optString("poster_url").trim()
                val banner = o.optString("banner_url").trim()
                val positionMs = o.optLong("position_ms", -1L)
                if (positionMs < 0L) continue

                var index = -1

                if (uri.isNotBlank()) {
                    index = seguir.indexOfFirst {
                        it.tipo == "serie" && it.streamUrl.trim() == uri
                    }
                }

                if (index == -1 && seriesTitle.isNotBlank() && season > 0 && episode > 0) {
                    index = seguir.indexOfFirst {
                        it.tipo == "serie" &&
                        it.titulo.equals(seriesTitle, ignoreCase = true) &&
                        it.temporada == season &&
                        it.capitulo == episode
                    }
                }

                if (index == -1 && seriesTitle.isNotBlank() && episode > 0) {
                    index = seguir.indexOfFirst {
                        it.tipo == "serie" &&
                        it.titulo.equals(seriesTitle, ignoreCase = true) &&
                        it.capitulo == episode
                    }
                }

                if (index >= 0) {
                    val viejo = seguir[index]
                    seguir[index] = viejo.copy(
                        titulo = if (seriesTitle.isNotBlank()) seriesTitle else viejo.titulo,
                        streamUrl = if (uri.isNotBlank()) uri else viejo.streamUrl,
                        temporada = if (season > 0) season else viejo.temporada,
                        capitulo = if (episode > 0) episode else viejo.capitulo,
                        progreso = positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        posterUrl = if (poster.isNotBlank()) poster else viejo.posterUrl,
                        bannerUrl = if (banner.isNotBlank()) banner else viejo.bannerUrl,
                        subtitulo = if (episode > 0) {
                            "${viejo.subtitulo.substringBefore("•").trim()} • Capítulo $episode"
                        } else viejo.subtitulo
                    )
                } else if (seriesTitle.isNotBlank() && uri.isNotBlank()) {
                    seguir.add(
                        0,
                        SeguirViendoItem(
                            tipo = "serie",
                            titulo = seriesTitle,
                            subtitulo = if (episode > 0) "Temporada $season • Capítulo $episode" else "",
                            posterUrl = poster,
                            bannerUrl = banner,
                            sinopsis = "",
                            streamUrl = uri,
                            temporada = if (season > 0) season else 0,
                            capitulo = if (episode > 0) episode else 0,
                            progreso = positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                    )
                }
            }

            while (seguir.size > 12) seguir.removeAt(seguir.lastIndex)
            guardarLista()

            // Refrescar inmediatamente la pantalla abierta.
            runOnUiThread {
                refrescarPantallaActual()
            }
        } catch (_: Exception) {
        }
    }

    private fun iniciarMediaSessionRctv() {
        if (rctvBrowser != null) return
        for (pkg in rctvPackages) {
            try {
                val browser = MediaBrowserCompat(
                    this,
                    ComponentName(pkg, rctvServiceName),
                    rctvConnectionCallback,
                    null
                )
                rctvBrowser = browser
                browser.connect()
                Log.d("SegoviaRCTV", "Intentando conectar con $pkg")
                return
            } catch (e: Exception) {
                Log.w("SegoviaRCTV", "No se pudo iniciar conexión con $pkg: ${e.message}")
            }
        }
    }

    private fun iniciarPollingRctv() {
        if (rctvPolling) return
        rctvPolling = true
        rctvHandler.post(rctvPollRunnable)
    }

    private val rctvPollRunnable = object : Runnable {
        override fun run() {
            if (!rctvConnected) {
                rctvPolling = false
                return
            }
            actualizarProgresoDesdeRctv()
            rctvHandler.postDelayed(this, 2000L)
        }
    }

    private fun actualizarProgresoDesdeRctv() {
        val controller = rctvController ?: return
        val state = controller.playbackState ?: return
        val metadata = controller.metadata
        val queue = controller.queue ?: return
        val activeId = state.activeQueueItemId
        if (activeId < 0) return

        val item = queue.firstOrNull { it.queueId == activeId } ?: return
        val uri = item.description?.mediaUri?.toString()?.trim().orEmpty()
        if (uri.isBlank()) return

        val position = state.position.coerceAtLeast(0L)
        val duration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L

        Log.d(
            "SegoviaRCTV",
            "Capítulo activo: ${item.description?.title} posición=$position duración=$duration uri=$uri"
        )

        val index = seguir.indexOfFirst {
            it.streamUrl.trim() == uri
        }

        if (index >= 0 && position > 0L) {
            val viejo = seguir[index]
            seguir[index] = viejo.copy(
                progreso = position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )
            guardarLista()
            refrescarPantallaActual()
        }
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

    private fun detenerMediaSessionRctv() {
        rctvPolling = false
        rctvHandler.removeCallbacks(rctvPollRunnable)
        try {
            rctvController?.unregisterCallback(rctvControllerCallback)
        } catch (_: Exception) {
        }
        rctvController = null
        try {
            rctvBrowser?.disconnect()
        } catch (_: Exception) {
        }
        rctvBrowser = null
        rctvConnected = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iniciarMediaSessionRctv()

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

        nav = NavigationUi(
            this,
            { route -> navigate(route) },
            { finishAffinity() }
        )

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
            { repo.peliculas },
            { seguir.toList() },
            { openContinue(it) }
        )

        peliculas = PeliculasScreen(
            this,
            nav,
            grid
        )

        kids = KidsScreen(
            this,
            nav,
            grid
        )

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

        settings = PlayerSettingsScreen(
            this,
            nav,
            player
        ) {
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
                setBackgroundColor(
                    android.graphics.Color.parseColor("#080C14")
                )

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

        series.showGrid(repo.series) {
            openSeries(it)
        }
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
            "DetallesPeli" -> {
                peliculaSeleccionada?.let {
                    movieDetails.show(it)
                } ?: showHome()
            }
            "DetallesSerie" -> {
                serieSeleccionada?.let {
                    series.showDetail(it)
                } ?: showHome()
            }
            else -> showHome()
        }
    }

    private fun goBack() {
        when (currentScreen) {
            "DetallesPeli" -> {
                if (previousScreen == "Kids") {
                    showKids()
                } else if (previousScreen == "Peliculas") {
                    showMovies()
                } else {
                    showHome()
                }
            }
            "DetallesSerie" -> {
                if (previousScreen == "Series") {
                    showSeries()
                } else {
                    showHome()
                }
            }
            "Peliculas", "Series", "Kids", "Ajustes" -> {
                showHome()
            }
            "Inicio" -> {
                finish()
            }
            else -> {
                showHome()
            }
        }
    }
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

        seguir.removeAll {
            it.streamUrl == p.streamUrl
        }

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
            progreso = seguir.firstOrNull {
                it.tipo == "serie" &&
                (it.streamUrl == c.streamUrl || (
                    it.titulo.equals(s.titulo, ignoreCase = true) &&
                    it.temporada == t.numero &&
                    it.capitulo == n
                ))
            }?.progreso ?: 0
        )

        seguir.removeAll {
            it.streamUrl == c.streamUrl || (
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

    private fun openContinue(item: SeguirViendoItem) {
        if (item.tipo == "serie") {
            val s = repo.series.firstOrNull {
                it.titulo == item.titulo
            } ?: repo.kidsItems
                .filterIsInstance<com.segovia.tv.model.KidsItem.Series>()
                .map { it.data }
                .firstOrNull { it.titulo == item.titulo }

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
            .firstOrNull { it.streamUrl == item.streamUrl }

        if (p != null) {
            previousScreen = "Inicio"
            currentScreen = "DetallesPeli"

            peliculaSeleccionada = p

            movieDetails.show(p)
            return
        }

        player.play(
            item.streamUrl,
            item.titulo
        )
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchTarget = null

                val root = window.decorView as? ViewGroup

                val titles = arrayOf(
                    "INICIO",
                    "PELICULAS",
                    "SERIES",
                    "KIDS",
                    "AJUSTES",
                    "SALIR"
                )

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

                if (
                    v != null &&
                    v.isShown &&
                    v.isEnabled
                ) {
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
