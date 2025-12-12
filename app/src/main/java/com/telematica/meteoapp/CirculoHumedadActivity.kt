package com.telematica.meteoapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class CirculoHumedadActivity  @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var percentage: Int = 0  // 0 a 100

    private val backgroundPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 16f
        isAntiAlias = true
    }

    private val foregroundPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 16f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    fun setPercentage(p: Int) {
        percentage = p.coerceIn(0, 100)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = min(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        val newMeasureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(newMeasureSpec, newMeasureSpec)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthF = width.toFloat()
        val heightF = height.toFloat()

        val cx = widthF / 2f
        val cy = heightF / 2f

        val stroke = foregroundPaint.strokeWidth
        val radius = min(widthF, heightF) / 2f - stroke

        // Círculo de fondo
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        val sweepAngle = 360f * percentage / 100f

        // Rectángulo perfectamente centrado
        val rect = android.graphics.RectF(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius
        )

        canvas.drawArc(
            rect,
            -90f,
            sweepAngle,
            false,
            foregroundPaint
        )
    }

}
