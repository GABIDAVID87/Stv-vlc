package com.segovia.tv.sagas

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.model.PeliculaDrive
import com.segovia.tv.playback.ExternalPlayerLauncher
import com.segovia.tv.ui.ImageLoader
import com.segovia.tv.ui.NavigationUi
import com.segovia.tv.ui.RoundPosterImageView
import com.segovia.tv.ui.UiUtils

/**
 * Pantalla de una saga. La grilla principal no cambia: al tocar cualquiera
 * de las películas marcadas con -saga se abre esta pantalla con toda la saga.
 * No hay botón de reproducir: tocar el póster reproduce directamente.
 */
class SagaMoviesScreen(
    private val activity: AppCompatActivity,
    private val nav: NavigationUi,
    private val token: () -> String?,
    private val name: (String) -> String,
    private val player: ExternalPlayerLauncher,
    private val onPlay: (PeliculaDrive) -> Unit
) {
    private val d get() = activity.resources.displayMetrics.density
    private fun dp(v: Int) = UiUtils.dp(v, d)

    fun show(items: List<PeliculaDrive>) {
        if (items.isEmpty()) return

        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor(UiUtils.BG))
            clipChildren = false
        }

        val first = items.first()
        val hero = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, dp(430))
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        root.addView(hero)

        val dark = View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, dp(430))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.TRANSPARENT, Color.parseColor("#DD080C14"), Color.parseColor(UiUtils.BG))
            )
        }
        root.addView(dark)

        val bar = nav.topBar("SagaPeliculas")
        root.addView(bar, FrameLayout.LayoutParams(-1, dp(76)))

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(55), dp(95), dp(45), dp(35))
        }

        val sagaTitle = TextView(activity).apply {
            text = sagaName(first.titulo)
            textSize = 34f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }
        content.addView(sagaTitle)

        val info = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            setLineSpacing(dp(3).toFloat(), 1.05f)
            layoutParams = LinearLayout.LayoutParams(dp(850), dp(110))
        }
        content.addView(info)

        val postersScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, dp(270)).apply {
                topMargin = dp(12)
            }
        }

        val posters = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(12), dp(25), dp(12))
            clipChildren = false
        }
        postersScroll.addView(posters)
        content.addView(postersScroll)

        root.addView(
            ScrollView(activity).apply {
                isFillViewport = true
                clipChildren = false
                addView(content)
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            }
        )

        fun updateSelected(p: PeliculaDrive) {
            ImageLoader.load(activity, p.bannerUrl, hero, token())
            sagaTitle.text = cleanTitle(p.titulo)
            info.text = buildInfo(p)
        }

        val cards = mutableListOf<View>()
        items.forEachIndexed { index, movie ->
            val card = FrameLayout(activity).apply {
                id = View.generateViewId()
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(dp(165), dp(235)).apply {
                    rightMargin = dp(18)
                }
                setOnFocusChangeListener { v, focused ->
                    v.animate()
                        .scaleX(if (focused) 1.08f else 1f)
                        .scaleY(if (focused) 1.08f else 1f)
                        .translationZ(if (focused) dp(5).toFloat() else 0f)
                        .setDuration(140)
                        .start()
                    if (focused) {
                        updateSelected(movie)
                        (postersScroll as HorizontalScrollView).smoothScrollTo(
                            (v.left - dp(60)).coerceAtLeast(0), 0
                        )
                    }
                }
                setOnClickListener { onPlay(movie) }
            }

            val poster = RoundPosterImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(165), dp(220))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            ImageLoader.load(activity, movie.posterUrl, poster, token())
            card.addView(poster)

            val border = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(173), dp(228)).apply {
                    leftMargin = -dp(4)
                    topMargin = -dp(4)
                }
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(3), Color.parseColor("#FFD54F"))
                    cornerRadius = dp(10).toFloat()
                }
                visibility = View.GONE
            }
            card.addView(border)
            card.setOnFocusChangeListener { v, focused ->
                border.visibility = if (focused) View.VISIBLE else View.GONE
                v.animate()
                    .scaleX(if (focused) 1.08f else 1f)
                    .scaleY(if (focused) 1.08f else 1f)
                    .translationZ(if (focused) dp(5).toFloat() else 0f)
                    .setDuration(140)
                    .start()
                if (focused) updateSelected(movie)
            }
            posters.addView(card)
            cards.add(card)
        }

        cards.forEachIndexed { index, view ->
            view.nextFocusRightId = if (index + 1 < cards.size) cards[index + 1].id else view.id
            view.nextFocusLeftId = if (index > 0) cards[index - 1].id else view.id
        }

        activity.setContentView(root)
        bar.bringToFront()
        updateSelected(first)
        cards.firstOrNull()?.requestFocus()
    }

    private fun String.substringBeforeIgnoreCase(delimiter: String): String {
        val index = indexOf(delimiter, ignoreCase = true)
        return if (index >= 0) substring(0, index) else this
    }

    private fun sagaName(title: String): String {
        var value = title.substringBeforeIgnoreCase("-saga").trim()
        value = value.replace(Regex("\\(\\d{4}\\)"), " ")
        value = value.substringBefore(":").trim()
        value = value.replace(Regex("\\s+\\d+\\s*$"), "")
        return value.replace(Regex("\\s+"), " ").trim()
    }

    private fun cleanTitle(title: String): String =
        title.substringBeforeIgnoreCase("-saga").trim()

    private fun buildInfo(p: PeliculaDrive): String {
        val clean = cleanTitle(p.titulo)
        val year = Regex("\\((\\d{4})\\)").find(clean)?.groupValues?.getOrNull(1).orEmpty()
        return buildString {
            if (year.isNotBlank()) append(year).append("  •  ")
            append("Película")
            append("\n\n")
            append(p.sinopsis.ifBlank { "Sinopsis no disponible." })
        }
    }
}
