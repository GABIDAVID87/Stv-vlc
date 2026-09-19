package com.segovia.tv.playback

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

class ExternalPlayerLauncher(private val activity: Activity) {

    companion object {
        private const val WORKER =
            "https://segovia-tv-proxy.guadianesgalaxi.workers.dev"

        const val REQUEST_RCTV_PROGRESS = 7401

        const val EXTRA_BANNER_URL = "segovia_banner_url"
        const val EXTRA_NEXT_URL = "segovia_next_episode_url"
        const val EXTRA_NEXT_TITLE = "segovia_next_episode_title"
        const val EXTRA_NEXT_SEASON = "segovia_next_episode_season"
        const val EXTRA_NEXT_EPISODE = "segovia_next_episode_number"

        const val EXTRA_CONTENT_TYPE = "content_type"
        const val EXTRA_FROM_EXTERNAL = "from_external"

        const val EXTRA_SERIES_TITLE = "series_title"
        const val EXTRA_SEASON = "season"
        const val EXTRA_EPISODE = "episode"

        const val EXTRA_SEGOVIA_URLS = "segovia_direct_urls"
        const val EXTRA_SEGOVIA_TITLES = "segovia_direct_titles"
        const val EXTRA_SEGOVIA_POSTERS = "segovia_direct_posters"
        const val EXTRA_SEGOVIA_START_INDEX = "segovia_direct_start_index"

        const val EXTRA_SEGOVIA_SERIES_TITLE = "segovia_series_title"
        const val EXTRA_SEGOVIA_SEASON = "segovia_season"
        const val EXTRA_SEGOVIA_EPISODE = "segovia_episode"

        const val EXTRA_SEGOVIA_PROGRESS_JSON = "segovia_progress_json"
    }

    private val prefs by lazy {
        activity.getSharedPreferences(
            "segoviatv_player",
            Activity.MODE_PRIVATE
        )
    }

    fun preferredPackage(): String? =
        prefs.getString("package", null)

    fun preferredName(): String? =
        prefs.getString("label", null)

    fun save(packageName: String, label: String) {
        prefs.edit()
            .putString("package", packageName)
            .putString("label", label)
            .apply()
    }

    fun players() = activity.packageManager
        .queryIntentActivities(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("$WORKER/video?id=test"),
                    "video/*"
                )
            },
            PackageManager.MATCH_DEFAULT_ONLY
        )
        .filter {
            it.activityInfo.packageName != activity.packageName
        }
        .distinctBy {
            it.activityInfo.packageName
        }
        .sortedBy {
            it.loadLabel(activity.packageManager)
                .toString()
                .lowercase()
        }

    private fun normalizeUrl(raw: String): String {
        val url = raw.trim()

        if (url.isBlank()) return ""

        if (url.startsWith("/video?")) {
            return WORKER + url
        }

        if (url.startsWith("video?")) {
            return "$WORKER/$url"
        }

        if (url.startsWith("undefined/video?", true)) {
            return WORKER + "/" + url.substringAfter("undefined/")
        }

        if (url.startsWith("null/video?", true)) {
            return WORKER + "/" + url.substringAfter("null/")
        }

        if (
            !url.startsWith("http://") &&
            !url.startsWith("https://") &&
            !url.contains("/")
        ) {
            return "$WORKER/video?id=$url"
        }

        return url
    }

    /**
     * Abre el VideoPlayerActivity de VLC INTEGRADO
     * dentro del mismo APK de Segovia TV.
     *
     * IMPORTANTE:
     *
     * El paquete propietario del componente debe ser el
     * applicationId de la aplicación final.
     *
     * Como Segovia TV ejecuta esta Activity desde su propio APK,
     * usamos activity.packageName y mantenemos el nombre completo
     * de la Activity VLC.
     *
     * NO usamos "org.videolan.vlc" como paquete propietario,
     * porque ese paquete puede corresponder al VLC externo
     * instalado desde Play Store.
     */
    private fun vlcPlayerIntent(
        url: String,
        title: String
    ): Intent =
        Intent(Intent.ACTION_VIEW).apply {

            component = ComponentName(
                activity.packageName,
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

    // ============================================================
    // SERIE: ENVÍA HASTA 6 CAPÍTULOS AL VLC INTEGRADO
    // ============================================================
    fun playM3U(
        urls: List<String>,
        titles: List<String>,
        posters: List<String>,
        seriesTitle: String? = null,
        season: Int? = null,
        episode: Int? = null,
        bannerUrl: String? = null
    ) {

        val count = minOf(
            6,
            urls.size,
            titles.size
        )

        if (count <= 0) {
            Toast.makeText(
                activity,
                "No hay capítulos para reproducir",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val directUrls = ArrayList<String>()
        val directTitles = ArrayList<String>()
        val directPosters = ArrayList<String>()

        for (i in 0 until count) {

            val url = normalizeUrl(urls[i])

            if (url.isBlank()) {
                continue
            }

            directUrls.add(url)

            directTitles.add(
                titles[i].trim()
            )

            directPosters.add(
                if (i < posters.size) {
                    posters[i].trim()
                } else {
                    ""
                }
            )
        }

        if (directUrls.isEmpty()) {
            Toast.makeText(
                activity,
                "No hay URLs de capítulos válidas",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val startIndex = 0

        val firstUrl = directUrls[startIndex]
        val firstTitle = directTitles[startIndex]

        val intent = vlcPlayerIntent(
            firstUrl,
            firstTitle
        ).apply {

            putExtra(
                EXTRA_CONTENT_TYPE,
                "series"
            )

            putStringArrayListExtra(
                EXTRA_SEGOVIA_URLS,
                directUrls
            )

            putStringArrayListExtra(
                EXTRA_SEGOVIA_TITLES,
                directTitles
            )

            putStringArrayListExtra(
                EXTRA_SEGOVIA_POSTERS,
                directPosters
            )

            putExtra(
                EXTRA_SEGOVIA_START_INDEX,
                startIndex
            )

            if (!seriesTitle.isNullOrBlank()) {

                putExtra(
                    EXTRA_SERIES_TITLE,
                    seriesTitle
                )

                putExtra(
                    EXTRA_SEGOVIA_SERIES_TITLE,
                    seriesTitle
                )
            }

            if (season != null) {

                putExtra(
                    EXTRA_SEASON,
                    season.toString()
                )

                putExtra(
                    EXTRA_SEGOVIA_SEASON,
                    season
                )
            }

            if (episode != null) {

                putExtra(
                    EXTRA_EPISODE,
                    episode.toString()
                )

                putExtra(
                    EXTRA_SEGOVIA_EPISODE,
                    episode
                )
            }

            if (!bannerUrl.isNullOrBlank()) {

                putExtra(
                    EXTRA_BANNER_URL,
                    bannerUrl
                )
            }
        }

        try {

            activity.startActivityForResult(
                intent,
                REQUEST_RCTV_PROGRESS
            )

        } catch (e: Exception) {

            Toast.makeText(
                activity,
                "No se pudo abrir el reproductor VLC integrado",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ============================================================
    // PELÍCULA INDIVIDUAL
    // ============================================================
    fun play(
        url: String,
        title: String,
        bannerUrl: String? = null,
        nextUrl: String? = null,
        nextTitle: String? = null,
        nextSeason: Int? = null,
        nextEpisode: Int? = null
    ) {

        val finalUrl = normalizeUrl(url)

        if (finalUrl.isBlank()) {

            Toast.makeText(
                activity,
                "No hay URL de reproducción",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val intent = vlcPlayerIntent(
            finalUrl,
            title
        ).apply {

            putExtra(
                EXTRA_CONTENT_TYPE,
                "movie"
            )

            if (!bannerUrl.isNullOrBlank()) {

                putExtra(
                    EXTRA_BANNER_URL,
                    bannerUrl
                )
            }

            if (!nextUrl.isNullOrBlank()) {

                putExtra(
                    EXTRA_NEXT_URL,
                    nextUrl
                )
            }

            if (!nextTitle.isNullOrBlank()) {

                putExtra(
                    EXTRA_NEXT_TITLE,
                    nextTitle
                )
            }

            if (nextSeason != null) {

                putExtra(
                    EXTRA_NEXT_SEASON,
                    nextSeason
                )
            }

            if (nextEpisode != null) {

                putExtra(
                    EXTRA_NEXT_EPISODE,
                    nextEpisode
                )
            }
        }

        try {

            activity.startActivity(intent)

        } catch (e: Exception) {

            Toast.makeText(
                activity,
                "No se pudo abrir el reproductor VLC integrado",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
