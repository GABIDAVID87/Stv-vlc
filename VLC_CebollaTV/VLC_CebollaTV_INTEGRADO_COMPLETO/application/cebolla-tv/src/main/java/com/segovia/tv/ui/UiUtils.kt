package com.segovia.tv.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import android.content.Context

class RoundPosterImageView(context: Context) : AppCompatImageView(context) {
    init {
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 10f * resources.displayMetrics.density)
            }
        }
    }
}

object UiUtils {
    const val YELLOW = "#FFD700"
    const val GOLD = "#E8AA3B"
    const val BG = "#080C14"
    const val CARD = "#151B25"
    const val CARD_DARK = "#18181B"

    fun dp(v: Int, density: Float): Int = (v * density).toInt()
    fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply { shape=GradientDrawable.RECTANGLE;setColor(color);cornerRadius=radius }
    fun focusable(normal: Int, radius: Float, stroke:Int=3): StateListDrawable {
        val s=StateListDrawable()
        val f=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;setColor(normal);setStroke(stroke,Color.parseColor(YELLOW));cornerRadius=radius}
        val n=GradientDrawable().apply{shape=GradientDrawable.RECTANGLE;setColor(normal);cornerRadius=radius}
        s.addState(intArrayOf(android.R.attr.state_focused),f);s.addState(intArrayOf(),n);return s
    }
}
