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
import com.segovia.tv.ui.UiUtils

/**
 * Pantalla de una saga.
 * No modifica la grilla de Películas: solamente se muestra cuando el usuario
 * toca una película cuyo título termina con -saga.
 */
class SagaMoviesScreen(
    private val activity: AppCompatActivity,
    private val nav: NavigationUi,
    private val token: () -> String?,
    private val name: (String) -> String,
    private val player: ExternalPlayerLauncher
) {
    private val d get() = activity.resources.displayMetrics.density
    private fun dp(v: Int) = UiUtils.dp(v, d)

    fun show(sagaName: String, movies: List<PeliculaDrive>, selected: PeliculaDrive) {
        if (movies.isEmpty()) return

        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#080C14"))
            clipChildren = false
        }

        val hero = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.82f
        }
        root.addView(hero)

        root.addView(View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#F2080C14"),
                    Color.parseColor("#DD080C14"),
                    Color.parseColor("#88080C14"),
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
                    Color.parseColor("#88080C14"),
                    Color.parseColor("#FF080C14")
                )
            )
        })

        val bar = nav.topBar("Películas")
        root.addView(bar, FrameLayout.LayoutParams(-1, dp(76)))

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(50), dp(92), dp(35), dp(25))
        }

        val title = TextView(activity).apply {
            text = sagaName
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        }
        content.addView(title)

        val info = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(0, 0, 0, dp(16))
        }
        content.addView(info)

        val synopsis = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setLineSpacing(dp(3).toFloat(), 1.1f)
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(dp(760), dp(88)).apply {
                bottomMargin = dp(15)
            }
        }
        content.addView(synopsis)

        val posterScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
        }
        val posters = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            setPadding(dp(4), dp(8), dp(20), dp(12))
        }
        posterScroll.addView(posters)
        content.addView(
            posterScroll,
            LinearLayout.LayoutParams(-1, dp(300))
        )

        fun displayMovie(movie: PeliculaDrive) {
            ImageLoader.load(activity, movie.bannerUrl, hero, token())
            info.text = movieInfo(movie)
            synopsis.text = movie.sinopsis
            title.text = cleanSagaTitle(movie.titulo)
        }

        movies.forEach { movie ->
            val card = FrameLayout(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(175), dp(270)).apply {
                    rightMargin = dp(12)
                }
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                clipChildren = false
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(8).toFloat()
                }
                setOnFocusChangeListener { v, hasFocus ->
                    v.animate()
                        .scaleX(if (hasFocus) 1.08f else 1f)
                        .scaleY(if (hasFocus) 1.08f else 1f)
                        .translationZ(if (hasFocus) dp(5).toFloat() else 0f)
                        .setDuration(120)
                        .start()
                    if (hasFocus) displayMovie(movie)
                }
                setOnClickListener {
                    player.play(movie.streamUrl, name(cleanSagaTitle(movie.titulo)))
                }
            }

            val poster = ImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(175), dp(245))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = UiUtils.rounded(Color.parseColor("#151A24"), dp(8).toFloat())
            }
            ImageLoader.load(activity, movie.posterUrl, poster, token())
            card.addView(poster)

            val label = TextView(activity).apply {
                text = cleanSagaTitle(movie.titulo)
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                maxLines = 2
                layoutParams = FrameLayout.LayoutParams(-1, dp(42)).apply {
                    topMargin = dp(248)
                }
            }
            card.addView(label)
            posters.addView(card)
        }

        val scroll = ScrollView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                topMargin = dp(76)
            }
            isFillViewport = true
        }
        scroll.addView(content)
        root.addView(scroll)
        bar.bringToFront()
        activity.setContentView(root)

        val initial = movies.indexOfFirst { it === selected }.let { if (it >= 0) it else 0 }
        displayMovie(movies[initial])
        posters.getChildAt(initial)?.requestFocus()
    }

    private fun movieInfo(movie: PeliculaDrive): String {
        val clean = cleanSagaTitle(movie.titulo)
        val year = Regex("\\((\\d{4})\\)").find(movie.titulo)?.groupValues?.getOrNull(1)
        return listOfNotNull(
            year,
            if (clean.isNotBlank()) null else null
        ).joinToString("  •  ").ifBlank { sagaFallbackInfo(movie) }
    }

    private fun sagaFallbackInfo(movie: PeliculaDrive): String = "Película de la saga"

    private fun cleanSagaTitle(value: String): String {
        return value
            .replace(Regex("\\s*-\\s*saga\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "")
            .trim()
            .replace(Regex("\\s+\\d+\\s*$"), "")
            .trim()
            .ifBlank { value.trim() }
    }
}
