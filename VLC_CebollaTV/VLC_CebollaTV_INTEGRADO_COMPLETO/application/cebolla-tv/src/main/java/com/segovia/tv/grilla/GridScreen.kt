package com.segovia.tv.grilla

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.segovia.tv.model.*
import com.segovia.tv.ui.*

class GridScreen(
    private val activity: AppCompatActivity,
    private val nav: NavigationUi,
    private val token: () -> String?,
    private val name: (String) -> String,
    private val openMovie: (PeliculaDrive) -> Unit,
    private val openSeries: (SerieDrive) -> Unit
) {

    private val d get() = activity.resources.displayMetrics.density
    private fun dp(v: Int) = UiUtils.dp(v, d)

    fun showMovies(items: List<PeliculaDrive>, title: String) =
        show(items.map { Card(it, null) }, title) {
            openMovie(it.movie!!)
        }

    fun showSeries(items: List<SerieDrive>, title: String) =
        show(items.map { Card(null, it) }, title) {
            openSeries(it.series!!)
        }

    fun showKids(items: List<KidsItem>, title: String) =
        show(items.map {
            when (it) {
                is KidsItem.Movie -> Card(it.data, null, it)
                is KidsItem.Series -> Card(null, it.data, it)
            }
        }, title) { card ->
            when (val k = card.kids) {
                is KidsItem.Movie -> openMovie(k.data)
                is KidsItem.Series -> openSeries(k.data)
                else -> {}
            }
        }

    private data class Card(
        val movie: PeliculaDrive?,
        val series: SerieDrive?,
        val kids: KidsItem? = null
    )

    private fun show(
        items: List<Card>,
        title: String,
        onOpen: (Card) -> Unit
    ) {
        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            clipChildren = false
        }

        val header = TextView(activity).apply {
            text = "SEGOVIA TV"
            textSize = 26f
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E8AA3B"))
            setPadding(dp(30), 0, 0, 0)
            setBackgroundColor(Color.BLACK)
        }

        root.addView(
            header,
            FrameLayout.LayoutParams(-1, dp(42))
        )

        val side = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#050505"))
            setPadding(dp(5), dp(15), dp(5), dp(10))
        }

        fun menu(
            txt: String,
            icon: String,
            active: Boolean,
            action: () -> Unit
        ): View {
            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
            }

            val img = SegoviaIconView(activity, icon).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dp(25),
                    dp(26)
                )
                setIconColor(
                    if (active) Color.parseColor(UiUtils.GOLD)
                    else Color.WHITE
                )
            }

            val label = TextView(activity).apply {
                text = txt
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            }

            box.addView(img)
            box.addView(label)

            box.setOnFocusChangeListener { _, focus ->
                val c = if (focus || active) {
                    Color.parseColor(UiUtils.GOLD)
                } else {
                    Color.WHITE
                }
                img.setIconColor(c)
                label.setTextColor(c)
            }

            box.setOnClickListener {
                action()
            }

            side.addView(
                box,
                LinearLayout.LayoutParams(
                    dp(66),
                    dp(64)
                )
            )

            return box
        }

        val home = menu("INICIO", "home", false) {
            activity.onBackPressedDispatcher.onBackPressed()
        }

        val movies = menu("PELÍCULAS", "movies", title == "Películas") {
            navRoute("Peliculas")
        }

        val series = menu("SERIES", "series", title == "Series") {
            navRoute("Series")
        }

        val kids = menu("KIDS", "kids", title == "Kids") {
            navRoute("Kids")
        }

        val exit = menu("SALIR", "exit", false) {
            activity.finishAffinity()
        }

        root.addView(
            side,
            FrameLayout.LayoutParams(
                dp(66),
                -1
            ).apply {
                topMargin = dp(42)
            }
        )

        val scroll = ScrollView(activity).apply {
            clipChildren = false
            layoutParams = FrameLayout.LayoutParams(-1, -1).apply {
                leftMargin = dp(66)
                topMargin = dp(42)
            }
        }

        val grid = GridLayout(activity).apply {
            columnCount = 8
            setPadding(
                dp(8),
                dp(5),
                dp(10),
                dp(20)
            )
            clipChildren = false
        }

        val screen = activity.resources.displayMetrics.widthPixels
        val available = (screen - dp(100)).coerceAtLeast(dp(700))
        val gap = dp(6)

        // NO SE MODIFICA EL TAMAÑO DE LOS PÓSTERS
        val posterW = ((available - gap * 7) / 8).coerceAtLeast(dp(100))
        val posterH = (posterW * 1.38f).toInt()

        val cardH = posterH + dp(34)

        val cards = mutableListOf<View>()

        items.forEach { data ->
            lateinit var border: View

            val card = FrameLayout(activity).apply {
                id = View.generateViewId()

                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true

                clipChildren = false

                layoutParams = GridLayout.LayoutParams().apply {
                    width = posterW
                    height = cardH
                    setMargins(
                        gap / 2,
                        dp(4),
                        gap / 2,
                        dp(5)
                    )
                }

                setOnFocusChangeListener { v, focus ->
                    if (focus) {
                        border.visibility = View.VISIBLE

                        v.animate()
                            .scaleX(1.05f)
                            .scaleY(1.05f)
                            .translationZ(dp(4).toFloat())
                            .setDuration(150)
                            .start()
                    } else {
                        border.visibility = View.GONE

                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationZ(0f)
                            .setDuration(150)
                            .start()
                    }
                }

                setOnClickListener {
                    onOpen(data)
                }
            }

            val poster = RoundPosterImageView(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    posterW,
                    posterH
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val url = when {
                data.movie != null -> data.movie.posterUrl
                data.series != null -> data.series.posterUrl
                else -> ""
            }

            ImageLoader.load(
                activity,
                url,
                poster,
                token()
            )

            card.addView(poster)

            val titleView = TextView(activity).apply {
                text = when {
                    data.movie != null -> name(data.movie.titulo)
                    data.series != null -> name(data.series.titulo)
                    else -> ""
                }

                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)

                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false

                layoutParams = FrameLayout.LayoutParams(
                    posterW,
                    dp(32)
                ).apply {
                    topMargin = posterH + dp(2)
                }
            }

            card.addView(titleView)

            border = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    posterW + dp(8),
                    cardH + dp(8)
                ).apply {
                    leftMargin = -dp(4)
                    topMargin = -dp(4)
                }

                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        dp(3),
                        Color.parseColor("#FFD54F")
                    )
                    cornerRadius = dp(10).toFloat()
                }

                visibility = View.GONE
            }

            card.addView(border)
            border.bringToFront()

            grid.addView(card)
            cards.add(card)
        }

        grid.post {
            cards.forEachIndexed { index, v ->
                val col = index % 8

                v.nextFocusRightId =
                    if (col < 7 && index + 1 < cards.size) {
                        cards[index + 1].id
                    } else {
                        v.id
                    }

                v.nextFocusLeftId =
                    if (col > 0) {
                        cards[index - 1].id
                    } else {
                        movies.id
                    }

                v.nextFocusDownId =
                    if (index + 8 < cards.size) {
                        cards[index + 8].id
                    } else {
                        v.id
                    }

                v.nextFocusUpId =
                    if (index - 8 >= 0) {
                        cards[index - 8].id
                    } else {
                        movies.id
                    }
            }

            cards.firstOrNull()?.requestFocus()
        }

        scroll.addView(grid)
        root.addView(scroll)

        movies.nextFocusUpId = home.id
        movies.nextFocusDownId = series.id

        series.nextFocusUpId = movies.id
        series.nextFocusDownId = kids.id

        kids.nextFocusUpId = series.id
        kids.nextFocusDownId = exit.id

        exit.nextFocusUpId = kids.id

        cards.firstOrNull()?.let {
            movies.nextFocusRightId = it.id
            series.nextFocusRightId = it.id
            kids.nextFocusRightId = it.id
        }

        activity.setContentView(root)
        header.bringToFront()
    }

    private fun navRoute(route: String) {
        (activity as? com.segovia.tv.MainActivity)?.navigate(route)
    }
}