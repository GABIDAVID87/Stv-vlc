package com.segovia.tv.playback

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

class ExternalPlayerLauncher(
    private val activity: Activity
) {

    companion object {
        private const val WORKER =
            "https://segovia-tv-proxy.guadianesgalaxi.workers.dev"

        const val EXTRA_FROM_EXTERNAL = "from_external"
        const val EXTRA_CONTENT_TYPE = "content_type"
        const val EXTRA_SERIES_TITLE = "series_title"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"
        const val EXTRA_BANNER_URL = "segovia_banner_url"

        const val EXTRA_NEXT_URL = "next_url"
        const val EXTRA_NEXT_TITLE = "next_title"
        const val EXTRA_NEXT_SEASON = "next_season"
        const val EXTRA_NEXT_EPISODE = "next_episode"

        const val EXTRA_SEGOVIA_URLS = "segovia_direct_urls"
        const val EXTRA_SEGOVIA_TITLES = "segovia_direct_titles"
        const val EXTRA_SEGOVIA_POSTERS = "segovia_direct_posters"
        const val EXTRA_SEGOVIA_START_INDEX = "segovia_direct_start_index"
    }

    /**
     * Devuelve los reproductores externos instalados.
     *
     * Se mantiene porque la pantalla de ajustes de Segovia TV
     * todavía utiliza esta información.
     */
    fun players(): List<ResolveInfoWrapper> {

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("https://example.com/video.mp4"),
                "video/*"
            )
        }

        val pm = activity.packageManager

        return pm.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
            .filter {
                it.activityInfo.packageName != activity.packageName
            }
            .map {
                ResolveInfoWrapper(
                    it.activityInfo.packageName,
                    it.activityInfo.name,
                    it.loadLabel(pm).toString()
                )
            }
    }

    data class ResolveInfoWrapper(
        val packageName: String,
        val activityName: String,
        val label: String
    )

    fun preferredName(): String? {
        val prefs = activity.getSharedPreferences(
            "player_settings",
            Activity.MODE_PRIVATE
        )

        return prefs.getString("player_name", null)
    }

    fun save(packageName: String, name: String) {
        activity.getSharedPreferences(
            "player_settings",
            Activity.MODE_PRIVATE
        )
            .edit()
            .putString("player_package", packageName)
            .putString("player_name", name)
            .apply()
    }

    /**
     * Convierte las URLs cortas de Segovia TV en URLs del Worker.
     */
    private fun normalizeUrl(raw: String): String {

        val value = raw.trim()

        if (value.isEmpty()) {
            return value
        }

        if (
            value.startsWith("http://") ||
            value.startsWith("https://")
        ) {
            return value
        }

        val clean = value.removePrefix("/")

        return "$WORKER/$clean"
    }

    /**
     * Intent explícito hacia el VideoPlayerActivity de VLC
     * integrado en este proyecto.
     *
     * No usamos ACTION_VIEW para resolver reproductores externos.
     * El ComponentName fuerza la Activity concreta.
     */
    private fun vlcPlayerIntent(
        url: String,
        title: String
    ): Intent {

        return Intent().apply {

            component = ComponentName(
                "org.videolan.vlc",
                "org.videolan.vlc.gui.video.VideoPlayerActivity"
            )

            setDataAndType(
                Uri.parse(url),
                "video/*"
            )

            putExtra(
                Intent.EXTRA_TITLE,
                title
            )

            putExtra(
                EXTRA_FROM_EXTERNAL,
                true
            )
        }
    }

    /**
     * Reproduce una película o un capítulo.
     */
    fun play(
        rawUrl: String,
        title: String,
        contentType: String = "movie",
        bannerUrl: String? = null,
        seriesTitle: String? = null,
        season: Int? = null,
        episode: Int? = null,
        nextUrl: String? = null,
        nextTitle: String? = null,
        nextSeason: Int? = null,
        nextEpisode: Int? = null
    ) {

        val finalUrl = normalizeUrl(rawUrl)

        try {

            if (finalUrl.isBlank()) {
                throw IllegalArgumentException(
                    "La URL de reproducción está vacía"
                )
            }

            val intent = vlcPlayerIntent(
                finalUrl,
                title
            )

            intent.putExtra(
                EXTRA_CONTENT_TYPE,
                contentType
            )

            if (!bannerUrl.isNullOrBlank()) {
                intent.putExtra(
                    EXTRA_BANNER_URL,
                    bannerUrl
                )
            }

            if (!seriesTitle.isNullOrBlank()) {
                intent.putExtra(
                    EXTRA_SERIES_TITLE,
                    seriesTitle
                )
            }

            if (season != null) {
                intent.putExtra(
                    EXTRA_SEASON,
                    season
                )
            }

            if (episode != null) {
                intent.putExtra(
                    EXTRA_EPISODE,
                    episode
                )
            }

            if (!nextUrl.isNullOrBlank()) {
                intent.putExtra(
                    EXTRA_NEXT_URL,
                    normalizeUrl(nextUrl)
                )
            }

            if (!nextTitle.isNullOrBlank()) {
                intent.putExtra(
                    EXTRA_NEXT_TITLE,
                    nextTitle
                )
            }

            if (nextSeason != null) {
                intent.putExtra(
                    EXTRA_NEXT_SEASON,
                    nextSeason
                )
            }

            if (nextEpisode != null) {
                intent.putExtra(
                    EXTRA_NEXT_EPISODE,
                    nextEpisode
                )
            }

            activity.startActivity(intent)

        } catch (e: Exception) {

            showRealError(
                "Error al abrir VLC integrado",
                e
            )
        }
    }

    /**
     * Mantiene soporte para listas de hasta 6 capítulos.
     *
     * La reproducción normal de Segovia TV actualmente utiliza play(),
     * pero dejamos esta función porque otros puntos del proyecto pueden
     * seguir llamándola.
     */
    fun playM3U(
        urls: List<String>,
        titles: List<String>,
        posters: List<String> = emptyList(),
        startIndex: Int = 0,
        seriesTitle: String? = null,
        season: Int? = null,
        episode: Int? = null
    ) {

        try {

            val count = minOf(
                6,
                urls.size,
                titles.size
            )

            if (count <= 0) {
                throw IllegalArgumentException(
                    "La lista de reproducción está vacía"
                )
            }

            val safeStartIndex = startIndex.coerceIn(
                0,
                count - 1
            )

            val intent = vlcPlayerIntent(
                normalizeUrl(urls[safeStartIndex]),
                titles[safeStartIndex]
            )

            intent.putExtra(
                EXTRA_CONTENT_TYPE,
                "series"
            )

            intent.putExtra(
                EXTRA_SEGOVIA_URLS,
                ArrayList(
                    urls
                        .take(count)
                        .map { normalizeUrl(it) }
                )
            )

            intent.putExtra(
                EXTRA_SEGOVIA_TITLES,
                ArrayList(
                    titles.take(count)
                )
            )

            if (posters.isNotEmpty()) {
                intent.putExtra(
                    EXTRA_SEGOVIA_POSTERS,
                    ArrayList(
                        posters.take(count)
                    )
                )
            }

            intent.putExtra(
                EXTRA_SEGOVIA_START_INDEX,
                safeStartIndex
            )

            if (!seriesTitle.isNullOrBlank()) {
                intent.putExtra(
                    EXTRA_SERIES_TITLE,
                    seriesTitle
                )
            }

            if (season != null) {
                intent.putExtra(
                    EXTRA_SEASON,
                    season
                )
            }

            if (episode != null) {
                intent.putExtra(
                    EXTRA_EPISODE,
                    episode
                )
            }

            activity.startActivity(intent)

        } catch (e: Exception) {

            showRealError(
                "Error al abrir VLC integrado",
                e
            )
        }
    }

    /**
     * Muestra el error REAL que Android/VLC está devolviendo.
     *
     * Esto reemplaza temporalmente el mensaje genérico:
     * "No se pudo abrir el reproductor VLC integrado".
     */
    private fun showRealError(
        prefix: String,
        e: Exception
    ) {

        val exceptionName =
            e.javaClass.simpleName

        val message =
            e.message ?: "sin mensaje"

        val fullMessage =
            "$prefix\n\n" +
            "Excepción: $exceptionName\n" +
            "Mensaje: $message"

        android.util.Log.e(
            "SegoviaVLC",
            fullMessage,
            e
        )

        Toast.makeText(
            activity,
            fullMessage,
            Toast.LENGTH_LONG
        ).show()
    }
}
