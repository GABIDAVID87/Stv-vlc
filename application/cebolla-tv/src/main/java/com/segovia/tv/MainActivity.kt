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
            } catch (e: Exception) { Log.e("SegoviaRCTV", "No se pudo crear MediaController: ${e.message}", e) }
        }
        override fun onConnectionSuspended() { rctvConnected = false; Log.d("SegoviaRCTV", "MediaBrowser suspendido") }
        override fun onConnectionFailed() { rctvConnected = false; Log.d("SegoviaRCTV", "MediaBrowser no pudo conectar") }
    }
    private val rctvControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) { actualizarProgresoDesdeRctv() }
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) { actualizarProgresoDesdeRctv() }
        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) { actualizarProgresoDesdeRctv() }
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != ExternalPlayerLauncher.REQUEST_RCTV_PROGRESS) return
        val json = data?.getStringExtra(ExternalPlayerLauncher.EXTRA_SEGOVIA_PROGRESS_JSON).orEmpty()
        if (json.isBlank()) return
        recibirProgresoRctv(json)
    }
    private fun normalizeProgressUrl(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        val worker = "https://segovia-tv-proxy.guadianesgalaxi.workers.dev"

        if (value.startsWith("/video?", ignoreCase = true)) {
            return worker + value
        }
        if (value.startsWith("video?", ignoreCase = true)) {
            return "$worker/$value"
        }
        if (value.startsWith("undefined/video?", ignoreCase = true)) {
            return worker + "/" + value.substringAfter("undefined/")
        }
        if (value.startsWith("null/video?", ignoreCase = true)) {
            return worker + "/" + value.substringAfter("null/")
        }
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true) &&
            !value.contains("/")) {
            return "$worker/video?id=$value"
        }
        return value
    }

    private fun recibirProgresoRctv(json: String) {
        try {
            Log.d("SegoviaRCTV", "Progreso recibido desde VLC: ${json.length} caracteres")
            val a = JSONArray(json)

            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue

            // VLC devuelve toda la playlist, pero "Seguir viendo"
            // debe guardar solamente el capítulo que se está viendo.
            if (!o.optBoolean("current", false)) continue

                val rawUri = o.optString("uri").trim()
                val uri = normalizeProgressUrl(rawUri)
                val seriesTitle = o.optString("series_title").trim()
                val season = o.optInt("season", -1)
                val episode = o.optInt("episode", -1)
                val positionMs = o.optLong("position_ms", -1L)

                if (positionMs < 0L) continue

                var index = -1

                if (uri.isNotBlank()) {
                    index = seguir.indexOfFirst {
                        it.tipo == "serie" && normalizeProgressUrl(it.streamUrl) == uri
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

                val serie = if (seriesTitle.isNotBlank()) buscarSerie(seriesTitle) else null
                val info = if (serie != null) {
                    obtenerInfoCapitulo(serie, season, episode, uri)
                } else {
                    null
                }

                if (index >= 0) {
                    val viejo = seguir[index]

                    val serieReal = serie
                    val tituloSerie = serieReal?.titulo ?: viejo.titulo

                    val episodioReal = info?.third
                    val episodioTitulo = if (serieReal != null && episodioReal != null) {
                        limpiarTituloCapitulo(serieReal.titulo, episodioReal)
                    } else {
                        extraerTituloDeSubtitulo(viejo.subtitulo)
                    }

                    val subtitulo = construirSubtitulo(
                        if (season > 0) season else viejo.temporada,
                        if (episode > 0) episode else viejo.capitulo,
                        episodioTitulo
                    )

                    seguir[index] = viejo.copy(
                        titulo = tituloSerie,
                        streamUrl = if (uri.isNotBlank()) uri else viejo.streamUrl,
                        temporada = if (season > 0) season else viejo.temporada,
                        capitulo = if (episode > 0) episode else viejo.capitulo,
                        progreso = positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        posterUrl = serieReal?.posterUrl?.takeIf { it.isNotBlank() } ?: viejo.posterUrl,
                        bannerUrl = serieReal?.bannerUrl?.takeIf { it.isNotBlank() } ?: viejo.bannerUrl,
                        sinopsis = episodioReal?.let { limpiarSinopsis(it.sinopsis) } ?: limpiarSinopsis(viejo.sinopsis),
                        subtitulo = subtitulo
                    )
                } else if (serie != null && info != null && uri.isNotBlank()) {
                    val temporadaReal = info.first.numero
                    val capituloReal = info.second
                    val capitulo = info.third
                    val episodioTitulo = limpiarTituloCapitulo(serie.titulo, capitulo)

                    seguir.add(
                        0,
                        SeguirViendoItem(
                            tipo = "serie",
                            titulo = serie.titulo,
                            subtitulo = construirSubtitulo(temporadaReal, capituloReal, episodioTitulo),
                            posterUrl = serie.posterUrl,
                            bannerUrl = serie.bannerUrl,
                            sinopsis = limpiarSinopsis(capitulo.sinopsis),
                            streamUrl = uri,
                            temporada = temporadaReal,
                            capitulo = capituloReal,
                            progreso = positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )
                    )
                } else if (seriesTitle.isNotBlank() && uri.isNotBlank()) {
                    // Mantener compatibilidad con registros que VLC pueda devolver
                    // antes de que el catálogo haya terminado de cargarse.
                    seguir.add(
                        0,
                        SeguirViendoItem(
                            tipo = "serie",
                            titulo = seriesTitle,
                            subtitulo = construirSubtitulo(season, episode, ""),
                            posterUrl = "",
                            bannerUrl = "",
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
            repararSeguirViendo()
            guardarLista()
            runOnUiThread { refrescarPantallaActual() }
        } catch (e: Exception) {
            Log.e("SegoviaRCTV", "Error procesando progreso recibido desde VLC", e)
        }
    }

    private fun iniciarMediaSessionRctv() {
        if (rctvBrowser != null) return
        try {
            val browser = MediaBrowserCompat(
                this,
                ComponentName(packageName, rctvServiceName),
                rctvConnectionCallback,
                null
            )
            rctvBrowser = browser
            browser.connect()
            Log.d("SegoviaRCTV", "Intentando conectar con VLC integrado")
        } catch (e: Exception) {
            Log.e("SegoviaRCTV", "No se pudo iniciar conexión con VLC integrado: ${e.message}", e)
        }
    }
    private fun iniciarPollingRctv() {
        if (rctvPolling) return
        rctvPolling = true
        rctvHandler.post(rctvPollRunnable)
    }
    private val rctvPollRunnable = object : Runnable {
        override fun run() {
            if (!rctvConnected) { rctvPolling = false; return }
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
        val uri = normalizeProgressUrl(item.description?.mediaUri?.toString().orEmpty())

        if (uri.isBlank()) return

        val position = state.position.coerceAtLeast(0L)
        val duration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) ?: 0L

        Log.d(
            "SegoviaRCTV",
            "Capítulo activo: ${item.description?.title} posición=$position duración=$duration uri=$uri"
        )

        var index = seguir.indexOfFirst {
            it.tipo == "serie" && normalizeProgressUrl(it.streamUrl) == uri
        }

        /*
         * VLC cambia automáticamente al siguiente elemento de la cola.
         * Si ese capítulo todavía no estaba en "Seguir viendo", lo buscamos
         * directamente en el catálogo y lo añadimos con su propio progreso.
         */
        if (index < 0 && ::repo.isInitialized) {
            val encontrado = buscarCapituloPorUri(uri)

            if (encontrado != null) {
                val serie = encontrado.first
                val temporada = encontrado.second
                val numero = encontrado.third
                val capitulo = encontrado.fourth
                val episodioTitulo = limpiarTituloCapitulo(serie.titulo, capitulo)

                seguir.add(
                    0,
                    SeguirViendoItem(
                        tipo = "serie",
                        titulo = serie.titulo,
                        subtitulo = construirSubtitulo(temporada.numero, numero, episodioTitulo),
                        posterUrl = serie.posterUrl,
                        bannerUrl = serie.bannerUrl,
                        sinopsis = limpiarSinopsis(capitulo.sinopsis),
                        streamUrl = normalizeProgressUrl(capitulo.streamUrl),
                        temporada = temporada.numero,
                        capitulo = numero,
                        progreso = position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    )
                )

                while (seguir.size > 12) seguir.removeAt(seguir.lastIndex)
                guardarLista()
                refrescarPantallaActual()
                return
            }
        }

        if (index >= 0) {
            val viejo = seguir[index]

            /*
             * Cuando VLC acaba de saltar al siguiente capítulo puede informar
             * posición 0. No destruimos un progreso anterior con ese cero.
             * En cuanto VLC avance, se guarda la posición real.
             */
            if (position > 0L) {
                seguir[index] = viejo.copy(
                    progreso = position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                )
                guardarLista()
                refrescarPantallaActual()
            }
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
        try { rctvController?.unregisterCallback(rctvControllerCallback) } catch (_: Exception) {}
        rctvController = null
        try { rctvBrowser?.disconnect() } catch (_: Exception) {}
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
                repararSeguirViendo()
                when (currentScreen) { "Inicio" -> showHome(); "Peliculas" -> showMovies(); "Series" -> showSeries(); "Kids" -> showKids() }
            }
        }
        nav = NavigationUi(this, { route -> navigate(route) }, { finishAffinity() })
        grid = GridScreen(this, nav, { null }, repo::displayName, { openMovie(it) }, { openSeries(it) })
        home = HomeScreen(this, nav, { null }, repo::displayName, { repo.peliculas }, { seguir.toList() }, { openContinue(it) }, { openMovie(it) })
        peliculas = PeliculasScreen(this, nav, grid)
        kids = KidsScreen(this, nav, grid)
        series = SeriesScreen(this, nav, { null }, repo::displayName, player, { seguir.toList() }) { s, t, n, c -> guardarCapitulo(s, t, n, c) }
        movieDetails = MovieDetailsScreen(this, nav, { null }, repo::displayName, player) { guardarPelicula(it) }
        settings = PlayerSettingsScreen(this, nav, player) { showPrevious() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })
        repo.loadCache()
        repararSeguirViendo()
        showHome()
        repo.refreshAsync()
    }
    fun navigate(route: String) {
        when (route) { "Inicio" -> showHome(); "Peliculas" -> showMovies(); "Series" -> showSeries(); "Kids" -> showKids(); "Ajustes" -> showSettings() }
    }
    private fun showLoading() {
        setContentView(android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#080C14"))
            addView(android.widget.ProgressBar(this@MainActivity), android.widget.FrameLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER })
        })
    }
    private fun showHome() { previousScreen = currentScreen; currentScreen = "Inicio"; peliculaSeleccionada = null; serieSeleccionada = null; home.show() }
    private fun showMovies() { previousScreen = currentScreen; currentScreen = "Peliculas"; peliculas.show(repo.peliculas) }
    private fun showKids() { previousScreen = currentScreen; currentScreen = "Kids"; kids.show(repo.kidsItems) }
    private fun showSeries() { previousScreen = currentScreen; currentScreen = "Series"; series.showGrid(repo.series) { openSeries(it) } }
    private fun openMovie(p: PeliculaDrive) { previousScreen = currentScreen; currentScreen = "DetallesPeli"; peliculaSeleccionada = p; serieSeleccionada = null; movieDetails.show(p) }
    private fun openSeries(s: SerieDrive) { previousScreen = currentScreen; currentScreen = "DetallesSerie"; serieSeleccionada = s; peliculaSeleccionada = null; series.showDetail(s) }
    private fun showSettings() { previousScreen = currentScreen; currentScreen = "Ajustes"; settings.show() }
    private fun showPrevious() {
        when (previousScreen) {
            "Peliculas" -> showMovies(); "Series" -> showSeries(); "Kids" -> showKids()
            "DetallesPeli" -> peliculaSeleccionada?.let { movieDetails.show(it) } ?: showHome()
            "DetallesSerie" -> serieSeleccionada?.let { series.showDetail(it) } ?: showHome()
            else -> showHome()
        }
    }
    private fun goBack() {
        when (currentScreen) {
            "DetallesPeli" -> if (previousScreen == "Kids") showKids() else if (previousScreen == "Peliculas") showMovies() else showHome()
            "DetallesSerie" -> if (previousScreen == "Series") showSeries() else showHome()
            "Peliculas", "Series", "Kids", "Ajustes" -> showHome()
            "Inicio" -> finish()
            else -> showHome()
        }
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
            a.put(JSONObject().apply { put("tipo", it.tipo); put("titulo", it.titulo); put("subtitulo", it.subtitulo); put("posterUrl", it.posterUrl); put("bannerUrl", it.bannerUrl); put("sinopsis", it.sinopsis); put("streamUrl", it.streamUrl); put("temporada", it.temporada); put("capitulo", it.capitulo); put("progreso", it.progreso) })
        }
        getSharedPreferences("seguir_viendo", MODE_PRIVATE).edit().putString("items", a.toString()).apply()
    }
    private fun guardarPelicula(p: PeliculaDrive) {
        val item = SeguirViendoItem(tipo = "pelicula", titulo = p.titulo, posterUrl = p.posterUrl, bannerUrl = p.bannerUrl, sinopsis = p.sinopsis, streamUrl = p.streamUrl, progreso = p.progreso)
        seguir.removeAll { it.streamUrl == p.streamUrl }
        seguir.add(0, item)
        while (seguir.size > 12) seguir.removeAt(seguir.lastIndex)
        guardarLista()
    }
    private fun guardarCapitulo(s: SerieDrive, t: Temporada, n: Int, c: Capitulo) {
        val episodioTitulo = limpiarTituloCapitulo(s.titulo, c)
        val subtitulo = construirSubtitulo(t.numero, n, episodioTitulo)
        val uri = normalizeProgressUrl(c.streamUrl)

        val progresoExistente = seguir.firstOrNull {
            it.tipo == "serie" &&
                (
                    normalizeProgressUrl(it.streamUrl) == uri ||
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
            subtitulo = subtitulo,
            posterUrl = s.posterUrl,
            bannerUrl = s.bannerUrl,
            sinopsis = limpiarSinopsis(c.sinopsis),
            streamUrl = uri,
            temporada = t.numero,
            capitulo = n,
            progreso = progresoExistente
        )

        seguir.removeAll {
            it.tipo == "serie" &&
                (
                    normalizeProgressUrl(it.streamUrl) == uri ||
                        (
                            it.titulo.equals(s.titulo, ignoreCase = true) &&
                                it.temporada == t.numero &&
                                it.capitulo == n
                            )
                    )
        }

        seguir.add(0, item)

        while (seguir.size > 12) seguir.removeAt(seguir.lastIndex)
        guardarLista()
    }

    private fun buscarSerie(titulo: String): SerieDrive? {
        if (!::repo.isInitialized || titulo.isBlank()) return null

        return repo.series.firstOrNull {
            it.titulo.equals(titulo, ignoreCase = true)
        } ?: repo.kidsItems
            .filterIsInstance<com.segovia.tv.model.KidsItem.Series>()
            .map { it.data }
            .firstOrNull {
                it.titulo.equals(titulo, ignoreCase = true)
            }
    }

    private fun obtenerInfoCapitulo(
        serie: SerieDrive,
        temporadaNumero: Int,
        capituloNumero: Int,
        uri: String
    ): Triple<Temporada, Int, Capitulo>? {
        if (temporadaNumero > 0 && capituloNumero > 0) {
            val temporada = serie.temporadas.firstOrNull { it.numero == temporadaNumero }
            val capitulo = temporada?.capitulos?.getOrNull(capituloNumero - 1)

            if (temporada != null && capitulo != null) {
                return Triple(temporada, capituloNumero, capitulo)
            }
        }

        if (uri.isNotBlank()) {
            for (temporada in serie.temporadas) {
                for ((index, capitulo) in temporada.capitulos.withIndex()) {
                    if (normalizeProgressUrl(capitulo.streamUrl) == uri) {
                        return Triple(temporada, index + 1, capitulo)
                    }
                }
            }
        }

        return null
    }

    private fun buscarCapituloPorUri(
        uri: String
    ): Quadruple<SerieDrive, Temporada, Int, Capitulo>? {
        if (!::repo.isInitialized || uri.isBlank()) return null

        for (serie in repo.series) {
            for (temporada in serie.temporadas) {
                for ((index, capitulo) in temporada.capitulos.withIndex()) {
                    if (normalizeProgressUrl(capitulo.streamUrl) == uri) {
                        return Quadruple(serie, temporada, index + 1, capitulo)
                    }
                }
            }
        }

        for (kidSeries in repo.kidsItems.filterIsInstance<com.segovia.tv.model.KidsItem.Series>()) {
            val serie = kidSeries.data
            for (temporada in serie.temporadas) {
                for ((index, capitulo) in temporada.capitulos.withIndex()) {
                    if (normalizeProgressUrl(capitulo.streamUrl) == uri) {
                        return Quadruple(serie, temporada, index + 1, capitulo)
                    }
                }
            }
        }

        return null
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private fun construirSubtitulo(
        temporada: Int,
        capitulo: Int,
        tituloEpisodio: String
    ): String {
        if (temporada <= 0 && capitulo <= 0) return tituloEpisodio

        val base = buildString {
            if (temporada > 0) append("T$temporada")
            if (capitulo > 0) {
                if (isNotEmpty()) append(" - ")
                append("Capítulo $capitulo")
            }
        }

        return if (tituloEpisodio.isNotBlank()) {
            "$base - $tituloEpisodio"
        } else {
            base
        }
    }

    private fun extraerTituloDeSubtitulo(subtitulo: String): String {
        val raw = subtitulo.trim()
        if (raw.isBlank()) return ""

        return raw
            .replace(Regex("""^T\d+\s*-\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Temporada\s+\d+\s*-\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^Cap[ií]tulo\s+\d+\s*-\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun limpiarTituloCapitulo(serieTitle: String, capitulo: Capitulo): String {
        var title = capitulo.titulo.trim()
        val rawJson = capitulo.sinopsis.trim()

        if (rawJson.startsWith("{")) {
            try {
                val json = JSONObject(rawJson)
                val tituloJson = json.optString("titulo").trim()
                if (tituloJson.isNotBlank()) title = tituloJson
            } catch (_: Exception) {
                // Si no es JSON válido, seguimos con capitulo.titulo.
            }
        }

        title = title
            .replace(serieTitle, "", ignoreCase = true)
            .replace(
                Regex("""(?i)^cap[ií]tulo\s*\d+\s*[:.\-–—]?\s*"""),
                ""
            )
            .replace(Regex("""(?i)^s\d{1,2}e\d{1,3}\s*[-_.:]*\s*"""), "")
            .replace(Regex("""(?i)^s\d{1,2}\s*[-_.:]*\s*"""), "")
            .replace(
                Regex("""(?i)\b(1080p|720p|480p|WEB-DL|WEBRip|HDRip|HDTV|x264|x265|AAC|AC3|AMZN|GDRIVELatinoHD)\b"""),
                ""
            )
            .replace(Regex("""^[\s._\-–—:]+|[\s._\-–—:]+$"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        return title
    }

    private fun limpiarSinopsis(valor: String): String {
        val raw = valor.trim()
        if (raw.isBlank()) return ""

        if (raw.startsWith("{")) {
            try {
                val json = JSONObject(raw)
                val sinopsis = json.optString("sinopsis").trim()
                if (sinopsis.isNotBlank()) return sinopsis

                val descripcion = json.optString("descripcion").trim()
                if (descripcion.isNotBlank()) return descripcion
            } catch (_: Exception) {
                // No es JSON válido; seguimos limpiando el texto normal.
            }
        }

        return raw
            .replace(Regex("""^\s*["']|["']\s*$"""), "")
            .trim()
    }

    private fun repararSeguirViendo() {
        if (!::repo.isInitialized) return

        var cambiado = false

        seguir.forEachIndexed { index, item ->
            if (item.tipo != "serie") return@forEachIndexed

            val serie = buscarSerie(item.titulo) ?: return@forEachIndexed

            val info = obtenerInfoCapitulo(
                serie,
                item.temporada,
                item.capitulo,
                normalizeProgressUrl(item.streamUrl)
            )

            val poster = serie.posterUrl
            val banner = serie.bannerUrl

            val nuevoSubtitulo = if (info != null) {
                val episodioTitulo = limpiarTituloCapitulo(serie.titulo, info.third)
                construirSubtitulo(info.first.numero, info.second, episodioTitulo)
            } else {
                construirSubtitulo(
                    item.temporada,
                    item.capitulo,
                    extraerTituloDeSubtitulo(item.subtitulo)
                )
            }

            val nuevaSinopsis = info?.third?.sinopsis?.let { limpiarSinopsis(it) }
                ?: limpiarSinopsis(item.sinopsis)

            val nuevoStream = if (info != null) {
                normalizeProgressUrl(info.third.streamUrl)
            } else {
                normalizeProgressUrl(item.streamUrl)
            }

            val nuevo = item.copy(
                titulo = serie.titulo,
                subtitulo = nuevoSubtitulo,
                posterUrl = poster,
                bannerUrl = banner,
                sinopsis = nuevaSinopsis,
                streamUrl = nuevoStream
                // IMPORTANTE: no modificamos "progreso".
            )

            if (nuevo != item) {
                seguir[index] = nuevo
                cambiado = true
            }
        }

        if (cambiado) guardarLista()
    }

    private fun openContinue(item: SeguirViendoItem) {
        if (item.tipo == "serie") {
            val s = repo.series.firstOrNull { it.titulo == item.titulo } ?: repo.kidsItems.filterIsInstance<com.segovia.tv.model.KidsItem.Series>().map { it.data }.firstOrNull { it.titulo == item.titulo }
            if (s != null) { serieSeleccionada = s; previousScreen = "Inicio"; currentScreen = "DetallesSerie"; series.showDetail(s); return }
        }
        val p = repo.peliculas.firstOrNull { it.streamUrl == item.streamUrl } ?: repo.kidsItems.filterIsInstance<com.segovia.tv.model.KidsItem.Movie>().map { it.data }.firstOrNull { it.streamUrl == item.streamUrl }
        if (p != null) { previousScreen = "Inicio"; currentScreen = "DetallesPeli"; peliculaSeleccionada = p; movieDetails.show(p); return }
        player.play(item.streamUrl, item.titulo)
    }
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
                        if (v.getGlobalVisibleRect(r) && event.rawX >= r.left && event.rawX < r.right && event.rawY >= r.top && event.rawY < r.bottom && v.isShown && v.isEnabled) { touchTarget = v; return true }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val v = touchTarget; touchTarget = null
                if (v != null && v.isShown && v.isEnabled) { v.performClick(); return true }
            }
            MotionEvent.ACTION_CANCEL -> { touchTarget = null; return true }
        }
        return super.dispatchTouchEvent(event)
    }
    private fun findTabByTitle(parent: ViewGroup, title: String): View? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup) {
                for (j in 0 until child.childCount) {
                    val sub = child.getChildAt(j)
                    if (sub is android.widget.TextView && sub.text?.toString() == title) return child
                }
                findTabByTitle(child, title)?.let { return it }
            }
        }
        return null
    }
}
