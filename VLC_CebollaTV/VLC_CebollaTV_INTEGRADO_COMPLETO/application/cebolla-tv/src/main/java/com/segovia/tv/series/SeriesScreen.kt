package com.segovia.tv.series

import com.segovia.tv.grilla.GridScreen

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.model.Capitulo
import com.segovia.tv.model.SerieDrive
import com.segovia.tv.model.Temporada
import com.segovia.tv.model.SeguirViendoItem
import com.segovia.tv.playback.ExternalPlayerLauncher
import com.segovia.tv.ui.ImageLoader
import com.segovia.tv.ui.NavigationUi
import com.segovia.tv.ui.UiUtils

class SeriesScreen(
    private val activity: AppCompatActivity,
    private val nav: NavigationUi,
    private val token: () -> String?,
    private val name: (String) -> String,
    private val player: ExternalPlayerLauncher,
    private val getSeguirViendo: () -> List<SeguirViendoItem> = { emptyList() },
    private val onChapterPlayed: (SerieDrive, Temporada, Int, Capitulo) -> Unit = { _, _, _, _ -> }
) {
    private val d get() = activity.resources.displayMetrics.density
    private fun dp(v: Int) = UiUtils.dp(v, d)

    // ============================================================
    // BLOQUE — NORMALIZACIÓN DE URL PARA PROGRESO RCTV
    // ============================================================
    private fun normalizeProgressUrl(raw: String): String {
        val s = raw.trim()
        if (s.isBlank()) return ""
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        return "https://segovia-tv-proxy.guadianesgalaxi.workers.dev/video?id=${android.net.Uri.encode(s)}"
    }

    private fun durationToMs(value: String): Long {
        val s = value.trim().lowercase()
        if (s.isBlank()) return 0L

        try {
            val minMatch = Regex("""([0-9]+(?:[.,][0-9]+)?)\s*(min|mins|minuto|minutos)""").find(s)
            if (minMatch != null) {
                val minutes = minMatch.groupValues[1].replace(",", ".").toDouble()
                return (minutes * 60_000L).toLong()
            }

            val parts = s.split(":").map { it.trim().toLongOrNull() ?: 0L }
            return when (parts.size) {
                3 -> parts[0] * 3_600_000L + parts[1] * 60_000L + parts[2] * 1_000L
                2 -> parts[0] * 60_000L + parts[1] * 1_000L
                1 -> parts[0] * 1_000L
                else -> 0L
            }
        } catch (_: Exception) {
            return 0L
        }
    }

    fun showGrid(items: List<SerieDrive>, open: (SerieDrive) -> Unit) {
        GridScreen(activity, nav, token, name, {}, { s -> open(s) }).showSeries(items, "Series")
    }

    fun showDetail(serie: SerieDrive) {
        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor(UiUtils.BG))
            clipChildren = false
            clipToPadding = false
        }

        val banner = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = .94f
        }
        ImageLoader.load(activity, serie.bannerUrl, banner, token())
        root.addView(banner)

        root.addView(View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#F5080C14"),
                    Color.parseColor("#E8080C14"),
                    Color.parseColor("#85080C14"),
                    Color.TRANSPARENT
                )
            )
        })

        root.addView(View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.parseColor("#12080C14"),
                    Color.parseColor("#DD080C14"),
                    Color.parseColor("#FF080C14")
                )
            )
        })

        val bar = nav.homeBar()
        root.addView(bar, FrameLayout.LayoutParams(-1, dp(66)))

        val scroll = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                (activity.resources.displayMetrics.widthPixels * .58f).toInt(),
                -1
            ).apply {
                topMargin = dp(78)
                leftMargin = dp(48)
                bottomMargin = dp(20)
            }
            clipChildren = false
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), dp(30), dp(30))
            clipChildren = false
        }

        // Título de la serie
        content.addView(TextView(activity).apply {
            text = name(serie.titulo)
            textSize = 34f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        // Información de año, temporadas, género y estrellas
        content.addView(TextView(activity).apply {
            text = "${serie.anio}  •  ${serie.temporadas.size} Temporadas  •  ${serie.genero}  •  ★ ${serie.valoracion}"
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 0, 0, dp(12))
        })

        // Sinopsis
        val synopsis = TextView(activity).apply {
            text = serie.sinopsis
            textSize = 16f
            setTextColor(Color.WHITE)
            setLineSpacing(dp(2).toFloat(), 1.08f)
            maxLines = 5
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(synopsis)

        // Etiqueta TEMPORADAS
        content.addView(TextView(activity).apply {
            text = "TEMPORADAS"
            textSize = 15f
            setTextColor(Color.parseColor(UiUtils.YELLOW))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(8))
        })

        // Lista de botones de temporadas
        val seasons = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(10))
            clipChildren = false
        }
        content.addView(seasons)

        // Función para mostrar el desplegable de capítulos estilo modal
        fun showSeasonDialog(t: Temporada) {
            val overlay = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                setBackgroundColor(Color.parseColor("#E6000000"))
                isClickable = true
                isFocusable = true
                isFocusableInTouchMode = true
            }

            val displayMetrics = activity.resources.displayMetrics
            val cardW = (displayMetrics.widthPixels * 0.88f).toInt()
            val cardH = (displayMetrics.heightPixels * 0.82f).toInt()

            val card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = FrameLayout.LayoutParams(cardW, cardH).apply {
                    gravity = Gravity.CENTER
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.parseColor("#121622"))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
                setPadding(dp(16), dp(12), dp(16), dp(16))
                clipChildren = false
            }

            // Top Bar
            val topBar = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(-1, dp(40)).apply {
                    bottomMargin = dp(12)
                }
            }

            val closeBtn = TextView(activity).apply {
                text = " ✕ "
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = UiUtils.focusable(Color.parseColor("#27272A"), dp(6).toFloat())
                setOnClickListener {
                    root.removeView(overlay)
                }
            }

            val seasonTitle = TextView(activity).apply {
                text = if (t.titulo.isNotBlank()) t.titulo else "Temporada ${t.numero}"
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply {
                    leftMargin = dp(12)
                }
                gravity = Gravity.CENTER_VERTICAL
            }

            val appLogo = TextView(activity).apply {
                text = "SEGOVIA TV"
                textSize = 18f
                setTextColor(Color.parseColor("#E8AA3B"))
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER_VERTICAL
            }

            topBar.addView(closeBtn)
            topBar.addView(seasonTitle)
            topBar.addView(appLogo)
            card.addView(topBar)

            // Body con 3 columnas
            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            }

            // Columna 1: Lista de Capítulos
            val epScroll = ScrollView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, -1, 1.1f).apply {
                    rightMargin = dp(12)
                }
                isVerticalScrollBarEnabled = false
            }

            val epList = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            epScroll.addView(epList)

            // Columna 2: Póster Vertical de la Temporada
            val posterContainer = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(0, -1, 0.85f).apply {
                    rightMargin = dp(12)
                }
            }

            val epPoster = ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            ImageLoader.load(activity, t.posterUrl, epPoster, token())
            posterContainer.addView(epPoster)

            // Columna 3: Detalle del Capítulo
            val epDetails = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -1, 0.9f)
                setPadding(dp(4), 0, 0, 0)
            }

            val detailTitle = TextView(activity).apply {
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(4))
            }

            val detailDuration = TextView(activity).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#CBD5E1"))
                setPadding(0, 0, 0, dp(8))
            }

            val detailSynopsis = TextView(activity).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                setLineSpacing(dp(2).toFloat(), 1.05f)
                maxLines = 8
                ellipsize = TextUtils.TruncateAt.END
            }

            epDetails.addView(detailTitle)
            epDetails.addView(detailDuration)
            epDetails.addView(detailSynopsis)

            body.addView(epScroll)
            body.addView(posterContainer)
            body.addView(epDetails)
            card.addView(body)

            overlay.addView(card)

            // Cargar capítulos
            if (t.capitulos.isEmpty()) {
                epList.addView(TextView(activity).apply {
                    text = "Sin capítulos disponibles"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                })
            } else {
                val cleanSerieTitle = name(serie.titulo)
                val listaSeguir = getSeguirViendo()

                t.capitulos.forEachIndexed { idx, cap ->
                    val row = FrameLayout(activity).apply {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        isClickable = true
                        background = UiUtils.focusable(Color.parseColor("#1E293B"), dp(6).toFloat())
                        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply {
                            bottomMargin = dp(6)
                        }
                    }

                    // Margen interno optimizado para centrar letras
                    val innerLayout = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(-1, -1).apply {
                            setMargins(dp(14), dp(2), dp(10), dp(6))
                        }
                    }

                    val nameView = TextView(activity).apply {
                        text = "$cleanSerieTitle Capítulo ${idx + 1}"
                        textSize = 13.5f
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply {
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    }

                    // Botón de Play (triángulo grande, color gris fijo)
                    val playIconContainer = FrameLayout(activity).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(Color.parseColor("#334155"))
                            cornerRadius = dp(4).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(32), dp(24)).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            rightMargin = dp(4)
                        }
                    }

                    val playIcon = TextView(activity).apply {
                        text = "▶"
                        textSize = 13f
                        setTextColor(Color.parseColor("#94A3B8"))
                        gravity = Gravity.CENTER
                        layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                            gravity = Gravity.CENTER
                        }
                    }
                    playIconContainer.addView(playIcon)

                    innerLayout.addView(nameView)
                    innerLayout.addView(playIconContainer)
                    row.addView(innerLayout)

                    // 1. BARRA DE PROGRESO EN GRIS MÁS LARGA
                    val progressBarBg = View(activity).apply {
                        layoutParams = FrameLayout.LayoutParams(dp(210), dp(3)).apply {
                            gravity = Gravity.BOTTOM or Gravity.START
                            leftMargin = dp(14)
                            bottomMargin = dp(6)
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(Color.parseColor("#334155"))
                        }
                    }
                    row.addView(progressBarBg)

                    // 2. PROGRESO AMARILLO: usa la posición real guardada por RCTV
                    val capUrlNormalizada = normalizeProgressUrl(cap.streamUrl)

                    val itemGuardado = listaSeguir.firstOrNull {
                        it.tipo == "serie" &&
                        it.titulo.equals(serie.titulo, ignoreCase = true) &&
                        normalizeProgressUrl(it.streamUrl) == capUrlNormalizada
                    } ?: listaSeguir.firstOrNull {
                        it.tipo == "serie" &&
                        it.titulo.equals(serie.titulo, ignoreCase = true) &&
                        it.temporada == t.numero &&
                        it.capitulo == idx + 1
                    }

                    val progresoMs = itemGuardado?.progreso?.toLong()?.coerceAtLeast(0L) ?: 0L
                    val duracionMs = durationToMs(cap.duracion)

                    val progressPercent: Float =
                        if (progresoMs > 0L && duracionMs > 0L) {
                            (progresoMs.toFloat() / duracionMs.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                    if (progressPercent > 0f) {
                        val safePercent = progressPercent.coerceIn(0f, 1f)
                        val barWidth = (210f * safePercent).toInt()
                        val progressBarFill = View(activity).apply {
                            layoutParams = FrameLayout.LayoutParams(dp(barWidth), dp(3)).apply {
                                gravity = Gravity.BOTTOM or Gravity.START
                                leftMargin = dp(14)
                                bottomMargin = dp(6)
                            }
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                setColor(Color.parseColor("#E8AA3B"))
                            }
                        }
                        row.addView(progressBarFill)
                    }

                    // 3. PALOMITA VERDE (Solo si está visto casi completo >= 95%)
                    val isWatched = progressPercent >= 0.95f
                    if (isWatched) {
                        val checkView = TextView(activity).apply {
                            text = "✔"
                            textSize = 10f
                            setTextColor(Color.parseColor("#22C55E"))
                            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                                gravity = Gravity.BOTTOM or Gravity.START
                                leftMargin = dp(228)
                                bottomMargin = dp(3)
                            }
                        }
                        row.addView(checkView)
                    }

                    row.setOnFocusChangeListener { _, has ->
                        if (has) {
                            row.background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                setColor(Color.parseColor(UiUtils.YELLOW))
                                cornerRadius = dp(6).toFloat()
                            }
                            nameView.setTextColor(Color.BLACK)
                            playIconContainer.background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                setColor(Color.parseColor("#1E293B"))
                                cornerRadius = dp(4).toFloat()
                            }
                            playIcon.setTextColor(Color.BLACK)

                            // Limpiador inteligente para separar el título y limpiar el JSON/basura de sinopsis
                            var parsedTitle = name(cap.titulo)
                            var parsedSynopsis = cap.sinopsis

                            // Si la sinopsis viene con formato JSON desordenado, lo parseamos y extraemos limpio
                            if (parsedSynopsis.trim().startsWith("{") && parsedSynopsis.contains("sinopsis")) {
                                val tMatch = Regex("\"titulo\"\\s*:\\s*\"([^\"]+)\"").find(parsedSynopsis)
                                val sMatch = Regex("\"sinopsis\"\\s*:\\s*\"([^\"]+)\"").find(parsedSynopsis)
                                if (tMatch != null) parsedTitle = tMatch.groupValues[1]
                                if (sMatch != null) parsedSynopsis = sMatch.groupValues[1]
                            }

                            // Limpiar restos de nombres de archivo del título (extensiones, resoluciones, s01e02, etc.)
                            parsedTitle = parsedTitle
                                .replace(cleanSerieTitle, "", ignoreCase = true)
                                .replace(Regex("(?i)s\\d+e\\d+"), "")
                                .replace(Regex("(?i)\\b(1080p|720p|480p|WEB-DL|HDRip|HDTV|x264|x265|AAC|AC3|AMZN|GDRIVELatinoHD)\\b"), "")
                                .trim()

                            detailTitle.text = if (parsedTitle.isNotBlank()) "Capítulo ${idx + 1}: $parsedTitle" else "Capítulo ${idx + 1}"
                            detailDuration.text = if (cap.duracion.isNotBlank()) "⏱ ${cap.duracion}" else ""
                            detailSynopsis.text = if (parsedSynopsis.isNotBlank()) parsedSynopsis else "Sin sinopsis disponible."
                        } else {
                            row.background = UiUtils.focusable(Color.parseColor("#1E293B"), dp(6).toFloat())
                            nameView.setTextColor(Color.WHITE)
                            playIconContainer.background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                setColor(Color.parseColor("#334155"))
                                cornerRadius = dp(4).toFloat()
                            }
                            playIcon.setTextColor(Color.parseColor("#94A3B8"))
                        }
                    }

                    row.setOnClickListener {
                        onChapterPlayed(serie, t, idx + 1, cap)
                        val serieTitle = name(serie.titulo).trim()

                        fun cleanEpisodeTitle(chapter: Capitulo): String {
                            var title = name(chapter.titulo).trim()
                            val rawJson = chapter.sinopsis.trim()
                            if (rawJson.startsWith("{") && rawJson.contains("sinopsis")) {
                                val tMatch = Regex("\"titulo\"\\s*:\\s*\"([^\"]+)\"").find(rawJson)
                                if (tMatch != null) title = tMatch.groupValues[1].trim()
                            }
                            title = title
                                .replace(serieTitle, "", ignoreCase = true)
                                .replace(Regex("(?i)^cap[ií]tulo\\s*\\d+\\s*[:.\\-–—]?\\s*"), "")
                                .replace(Regex("(?i)s\\d{1,2}e\\d{1,3}"), "")
                                .replace(Regex("(?i)s\\d{1,2}"), "")
                                .replace(Regex("(?i)\\b(1080p|720p|480p|WEB-DL|WEBRip|HDRip|HDTV|x264|x265|AAC|AC3|AMZN|GDRIVELatinoHD)\\b"), "")
                                .replace(Regex("^[\\s._\\-–—:]+|[\\s._\\-–—:]+$"), "")
                                .replace(Regex("\\s{2,}"), " ")
                                .trim()
                            if (title.matches(Regex("^[\\s._\\-–—:]+$"))) title = ""
                            return title
                        }

                        fun playerTitle(seasonNumber: Int, episodeNumber: Int, episodeTitle: String): String {
                            return if (seasonNumber > 0) {
                                if (episodeTitle.isNotBlank()) "$serieTitle - T$seasonNumber - Capítulo $episodeNumber - $episodeTitle"
                                else "$serieTitle - T$seasonNumber - Capítulo $episodeNumber"
                            } else {
                                if (episodeTitle.isNotBlank()) "$serieTitle - Capítulo $episodeNumber - $episodeTitle"
                                else "$serieTitle - Capítulo $episodeNumber"
                            }
                        }

                        // Crear playlist de hasta 4 capítulos desde el seleccionado.
                        val playlistUrls = ArrayList<String>()
                        val playlistTitles = ArrayList<String>()
                        val playlistPosters = ArrayList<String>()
                        var added = 0

                        for (season in serie.temporadas.sortedBy { it.numero }) {
                            if (season.numero < t.numero) continue
                            val startIndex = if (season.numero == t.numero) idx else 0
                            for (j in startIndex until season.capitulos.size) {
                                if (added >= 4) break
                                val episode = season.capitulos[j]
                                val episodeTitle = cleanEpisodeTitle(episode)
                                playlistUrls.add(episode.streamUrl)
                                playlistTitles.add(playerTitle(season.numero, j + 1, episodeTitle))
                                playlistPosters.add(season.posterUrl)
                                added++
                            }
                            if (added >= 4) break
                        }

                        if (playlistUrls.isNotEmpty()) {
                            player.playM3U(playlistUrls, playlistTitles, playlistPosters)
                        }
                    }
                    epList.addView(row)
                }
            }

            // Tecla atrás
            overlay.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                    root.removeView(overlay)
                    true
                } else false
            }

            // Tocar fuera cierra
            overlay.setOnClickListener {
                root.removeView(overlay)
            }
            card.setOnClickListener { }

            root.addView(overlay)
            epList.getChildAt(0)?.requestFocus()
        }

        // Crear los botones de cada temporada
        serie.temporadas.forEach { t ->
            val b = TextView(activity).apply {
                text = "S%02d\n%d episodios".format(t.numero, t.capitulos.size)
                textSize = 12f
                gravity = Gravity.CENTER;
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                background = UiUtils.focusable(Color.parseColor(UiUtils.CARD), dp(8).toFloat(), 2)
                layoutParams = LinearLayout.LayoutParams(dp(112), dp(58)).apply {
                    rightMargin = dp(12)
                }
                setOnFocusChangeListener { v, has ->
                    if (has) {
                        setTextColor(Color.BLACK)
                        v.background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            setColor(Color.parseColor(UiUtils.YELLOW))
                            cornerRadius = dp(8).toFloat()
                        }
                    } else {
                        setTextColor(Color.WHITE)
                        v.background = UiUtils.focusable(Color.parseColor(UiUtils.CARD), dp(8).toFloat(), 2)
                    }
                }
                setOnClickListener {
                    showSeasonDialog(t)
                }
            }
            seasons.addView(b)
        }

        scroll.addView(content)
        root.addView(scroll)
        activity.setContentView(root)
        bar.bringToFront()

        seasons.getChildAt(0)?.requestFocus()
    }
}