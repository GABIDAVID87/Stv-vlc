package com.segovia.tv.inicio

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.model.PeliculaDrive
import com.segovia.tv.model.SeguirViendoItem
import com.segovia.tv.ui.ImageLoader
import com.segovia.tv.ui.NavigationUi
import com.segovia.tv.ui.UiUtils
import kotlin.math.roundToInt

class HomeScreen(
    private val activity: AppCompatActivity,
    private val nav: NavigationUi,
    private val token: () -> String?,
    private val name: (String) -> String,
    private val heroItems: () -> List<PeliculaDrive>,
    private val continueItems: () -> List<SeguirViendoItem>,
    private val openContinue: (SeguirViendoItem) -> Unit
) {

    private val d get() = activity.resources.displayMetrics.density

    private fun dp(v: Int) = UiUtils.dp(v, d)

    fun show() {

        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#070A0F"))
            clipChildren = false
            clipToPadding = false
        }

        val hero = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.96f
        }

        root.addView(hero)

        root.addView(View(activity).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)

            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.argb(250, 5, 8, 13),
                    Color.argb(225, 5, 8, 13),
                    Color.argb(95, 5, 8, 13),
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
                    Color.argb(25, 5, 8, 13),
                    Color.argb(170, 5, 8, 13),
                    Color.argb(255, 5, 8, 13)
                )
            )
        })

        val bar = nav.homeBar()

        root.addView(
            bar,
            FrameLayout.LayoutParams(-1, dp(66)).apply {
                gravity = Gravity.TOP
            }
        )

        val content = FrameLayout(activity).apply {
            clipChildren = false
            clipToPadding = false
        }

        root.addView(content)

        val logo = TextView(activity).apply {
            text = "Segovia TV"
            textSize = 29f
            setTextColor(Color.parseColor("#E8AA3B"))
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
        }

        content.addView(logo)

        val tc = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            clipChildren = false
            clipToPadding = false
        }

        val title = TextView(activity).apply {
            text = "Cargando..."
            textSize = 36f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        val synopsis = TextView(activity).apply {
            text = "Cargando sinopsis..."
            textSize = 16f
            setTextColor(Color.WHITE)
            includeFontPadding = false
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(2f, 1.08f)
            visibility = View.VISIBLE
        }

        tc.addView(
            title,
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            }
        )

        tc.addView(synopsis)

        content.addView(tc)

        val history = continueItems()
        val movies = heroItems()

        val section = TextView(activity).apply {
            text = if (history.isNotEmpty()) {
                "Seguir viendo"
            } else {
                "Películas"
            }

            textSize = 18f
            setTextColor(Color.parseColor("#E8AA3B"))
            setTypeface(null, Typeface.BOLD)
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
        }

        content.addView(section)

        val posterRow = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, 0)
        }

        val inner = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, 0, 0, 0)
        }

        posterRow.addView(inner)
        content.addView(posterRow)

        val screenW = activity.resources.displayMetrics.widthPixels

        val posterW = (screenW * 0.086f)
            .roundToInt()
            .coerceAtLeast(dp(104))

        val posterH = (posterW * 1.47f).roundToInt()

        val side = (screenW * 0.0045f)
            .roundToInt()
            .coerceAtLeast(dp(4))

        fun updateMovie(movie: PeliculaDrive) {

            title.text = name(movie.titulo)

            synopsis.text = movie.sinopsis
            synopsis.visibility = View.VISIBLE

            if (movie.bannerUrl.isNotBlank()) {
                ImageLoader.load(
                    activity,
                    movie.bannerUrl,
                    hero,
                    token()
                )
            }
        }

        fun updateContinue(item: SeguirViendoItem) {

            val movie = movies.firstOrNull {
                it.titulo.equals(
                    item.titulo,
                    ignoreCase = true
                ) ||
                (
                    it.posterUrl.isNotBlank() &&
                    it.posterUrl == item.posterUrl
                )
            }

            val titulo = movie?.titulo
                ?.takeIf { it.isNotBlank() }
                ?: item.titulo

            val sinopsis = movie?.sinopsis
                ?.takeIf { it.isNotBlank() }
                ?: item.sinopsis

            val banner = movie?.bannerUrl
                ?.takeIf { it.isNotBlank() }
                ?: item.bannerUrl

            title.text = name(titulo)

            synopsis.text = sinopsis
            synopsis.visibility = View.VISIBLE

            if (banner.isNotBlank()) {
                ImageLoader.load(
                    activity,
                    banner,
                    hero,
                    token()
                )
            }
        }

        fun makeFocusBackground():
                android.graphics.drawable.StateListDrawable {

            val states =
                android.graphics.drawable.StateListDrawable()

            val focused = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(
                    dp(6),
                    Color.parseColor("#FFD700")
                )
                cornerRadius = dp(8).toFloat()
            }

            val normal = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(8).toFloat()
            }

            states.addState(
                intArrayOf(android.R.attr.state_focused),
                focused
            )

            states.addState(
                intArrayOf(),
                normal
            )

            return states
        }

        fun makeCard(
            posterUrl: String,
            onFocus: () -> Unit,
            onClick: () -> Unit,
            index: Int
        ): FrameLayout {

            val card = FrameLayout(activity).apply {

                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true

                clipChildren = false
                clipToPadding = false

                background = makeFocusBackground()

                setPadding(
                    dp(3),
                    dp(3),
                    dp(3),
                    dp(3)
                )

                layoutParams = LinearLayout.LayoutParams(
                    posterW,
                    posterH
                ).apply {

                    leftMargin =
                        if (index == 0) {
                            (screenW * 0.044f).roundToInt()
                        } else {
                            side
                        }

                    rightMargin = side
                    topMargin = dp(3)
                    bottomMargin = dp(3)
                }

                setOnFocusChangeListener { v, hasFocus ->

                    if (hasFocus) {

                        v.animate()
                            .scaleX(1.055f)
                            .scaleY(1.055f)
                            .setDuration(120)
                            .start()

                        v.elevation = dp(12).toFloat()

                        onFocus()

                    } else {

                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(120)
                            .start()

                        v.elevation = 0f
                    }
                }

                setOnClickListener {
                    onClick()
                }
            }

            val img = ImageView(activity).apply {

                layoutParams = FrameLayout.LayoutParams(
                    -1,
                    -1
                )

                scaleType = ImageView.ScaleType.CENTER_CROP

                clipToOutline = true

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(7).toFloat()
                }
            }

            ImageLoader.load(
                activity,
                posterUrl,
                img,
                token()
            )

            card.addView(img)

            return card
        }

        if (history.isNotEmpty()) {

            history.forEachIndexed { index, item ->

                val card = makeCard(
                    posterUrl = item.posterUrl,

                    onFocus = {
                        updateContinue(item)
                    },

                    onClick = {
                        openContinue(item)
                    },

                    index = index
                )

                inner.addView(card)
            }

        } else {

            movies.forEachIndexed { index, movie ->

                val card = makeCard(
                    posterUrl = movie.posterUrl,

                    onFocus = {
                        updateMovie(movie)
                    },

                    onClick = {
                        updateMovie(movie)
                    },

                    index = index
                )

                inner.addView(card)
            }
        }

        activity.setContentView(root)

        bar.bringToFront()
        bar.isClickable = false

        root.post {

            val w = root.width
            val h = root.height

            if (w <= 0 || h <= 0) {
                return@post
            }

            logo.layoutParams =
                FrameLayout.LayoutParams(
                    (w * 0.19f).toInt(),
                    (h * 0.075f).toInt()
                ).apply {
                    leftMargin = (w * 0.043f).toInt()
                    topMargin = (h * 0.045f).toInt()
                }

            tc.layoutParams =
                FrameLayout.LayoutParams(
                    (w * 0.49f).toInt(),
                    (h * 0.22f).toInt()
                ).apply {
                    leftMargin = (w * 0.044f).toInt()
                    topMargin = (h * 0.425f).toInt()
                }

            section.layoutParams =
                FrameLayout.LayoutParams(
                    (w * 0.28f).toInt(),
                    (h * 0.055f).toInt()
                ).apply {
                    leftMargin = (w * 0.044f).toInt()
                    topMargin = (h * 0.640f).toInt()
                }

            posterRow.layoutParams =
                FrameLayout.LayoutParams(
                    -1,
                    (h * 0.245f).toInt()
                ).apply {
                    topMargin = (h * 0.715f).toInt()
                }

            posterRow.post {
                inner.getChildAt(0)?.requestFocus()
            }
        }

        if (history.isNotEmpty()) {

            history.firstOrNull()?.let {
                updateContinue(it)
            }

        } else {

            movies.firstOrNull()?.let {
                updateMovie(it)
            }
        }
    }
}