package com.segovia.tv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class SegoviaIconView(
    context: Context,
    private val iconType: String
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        isFocusable = false
    }

    fun setIconColor(color: Int) {
        paint.color = color
        fillPaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val s = minOf(w, h) * 0.68f

        paint.strokeWidth = s * 0.075f
        fillPaint.color = paint.color

        when (iconType) {
            "home" -> drawHome(canvas, cx, cy, s)
            "movies" -> drawClapper(canvas, cx, cy, s)
            "series" -> drawTv(canvas, cx, cy, s)
            "kids" -> drawKids(canvas, cx, cy, s)
            "settings" -> drawGear(canvas, cx, cy, s)
            "exit" -> drawExit(canvas, cx, cy, s)
        }
    }

    private fun drawHome(c: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Path()

        p.moveTo(cx - s * .43f, cy - s * .02f)
        p.lineTo(cx, cy - s * .40f)
        p.lineTo(cx + s * .43f, cy - s * .02f)

        p.moveTo(cx - s * .32f, cy - s * .08f)
        p.lineTo(cx - s * .32f, cy + s * .38f)
        p.lineTo(cx + s * .32f, cy + s * .38f)
        p.lineTo(cx + s * .32f, cy - s * .08f)

        p.moveTo(cx - s * .10f, cy + s * .38f)
        p.lineTo(cx - s * .10f, cy + s * .08f)
        p.lineTo(cx + s * .10f, cy + s * .08f)
        p.lineTo(cx + s * .10f, cy + s * .38f)

        c.drawPath(p, paint)
    }

    private fun drawClapper(c: Canvas, cx: Float, cy: Float, s: Float) {
        val body = RectF(
            cx - s * .40f,
            cy - s * .08f,
            cx + s * .40f,
            cy + s * .36f
        )

        c.drawRoundRect(
            body,
            s * .06f,
            s * .06f,
            paint
        )

        val p = Path()

        p.moveTo(cx - s * .40f, cy - s * .10f)
        p.lineTo(cx - s * .32f, cy - s * .34f)
        p.lineTo(cx + s * .40f, cy - s * .34f)
        p.lineTo(cx + s * .32f, cy - s * .10f)
        p.close()

        c.drawPath(p, paint)

        c.drawLine(
            cx - s * .18f,
            cy - s * .34f,
            cx - s * .12f,
            cy - s * .10f,
            paint
        )

        c.drawLine(
            cx + s * .05f,
            cy - s * .34f,
            cx + s * .11f,
            cy - s * .10f,
            paint
        )

        c.drawCircle(
            cx + s * .17f,
            cy + s * .13f,
            s * .035f,
            fillPaint
        )
    }

    private fun drawTv(c: Canvas, cx: Float, cy: Float, s: Float) {
        val r = RectF(
            cx - s * .40f,
            cy - s * .20f,
            cx + s * .40f,
            cy + s * .30f
        )

        c.drawRoundRect(
            r,
            s * .07f,
            s * .07f,
            paint
        )

        c.drawLine(
            cx - s * .16f,
            cy - s * .20f,
            cx - s * .30f,
            cy - s * .38f,
            paint
        )

        c.drawLine(
            cx + s * .16f,
            cy - s * .20f,
            cx + s * .30f,
            cy - s * .38f,
            paint
        )

        c.drawLine(
            cx - s * .13f,
            cy + s * .42f,
            cx + s * .13f,
            cy + s * .42f,
            paint
        )
    }

    private fun drawKids(c: Canvas, cx: Float, cy: Float, s: Float) {
        c.drawCircle(
            cx,
            cy,
            s * .32f,
            paint
        )

        c.drawCircle(
            cx - s * .13f,
            cy - s * .06f,
            s * .025f,
            fillPaint
        )

        c.drawCircle(
            cx + s * .13f,
            cy - s * .06f,
            s * .025f,
            fillPaint
        )

        val smile = RectF(
            cx - s * .16f,
            cy - s * .02f,
            cx + s * .16f,
            cy + s * .22f
        )

        c.drawArc(
            smile,
            15f,
            150f,
            false,
            paint
        )

        c.drawLine(
            cx - s * .34f,
            cy - s * .04f,
            cx - s * .45f,
            cy - s * .14f,
            paint
        )

        c.drawLine(
            cx + s * .34f,
            cy - s * .04f,
            cx + s * .45f,
            cy - s * .14f,
            paint
        )

        c.drawCircle(
            cx - s * .47f,
            cy - s * .15f,
            s * .04f,
            paint
        )

        c.drawCircle(
            cx + s * .47f,
            cy - s * .15f,
            s * .04f,
            paint
        )
    }

    private fun drawGear(c: Canvas, cx: Float, cy: Float, s: Float) {
        c.drawCircle(
            cx,
            cy,
            s * .24f,
            paint
        )

        c.drawCircle(
            cx,
            cy,
            s * .08f,
            paint
        )

        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45).toDouble())

            val x1 = cx + (cos(a) * s * .30f).toFloat()
            val y1 = cy + (sin(a) * s * .30f).toFloat()

            val x2 = cx + (cos(a) * s * .43f).toFloat()
            val y2 = cy + (sin(a) * s * .43f).toFloat()

            c.drawLine(
                x1,
                y1,
                x2,
                y2,
                paint
            )
        }
    }

    private fun drawExit(c: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Path()

        p.moveTo(
            cx + s * .08f,
            cy - s * .38f
        )

        p.lineTo(
            cx + s * .35f,
            cy - s * .38f
        )

        p.lineTo(
            cx + s * .35f,
            cy + s * .38f
        )

        p.lineTo(
            cx + s * .08f,
            cy + s * .38f
        )

        c.drawPath(p, paint)

        c.drawLine(
            cx - s * .38f,
            cy,
            cx + s * .16f,
            cy,
            paint
        )

        c.drawLine(
            cx - s * .38f,
            cy,
            cx - s * .22f,
            cy - s * .16f,
            paint
        )

        c.drawLine(
            cx - s * .38f,
            cy,
            cx - s * .22f,
            cy + s * .16f,
            paint
        )
    }
}