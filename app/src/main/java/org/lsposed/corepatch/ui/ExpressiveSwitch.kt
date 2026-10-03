package org.lsposed.corepatch.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.animation.DecelerateInterpolator

class ExpressiveSwitch(context: Context) : View(context) {
    private val palette = UiPalette.from(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()

    private var listener: ((Boolean) -> Unit)? = null
    private var progress = 0f
    private var animator: ValueAnimator? = null

    var isChecked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            animateTo(if (value) 1f else 0f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                stateDescription = if (value) "On" else "Off"
            }
            listener?.invoke(value)
        }

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            stateDescription = "Off"
        }
    }

    fun toggle() {
        isChecked = !isChecked
    }

    fun setOnCheckedChangeListener(listener: ((Boolean) -> Unit)?) {
        this.listener = listener
    }

    override fun performClick(): Boolean {
        super.performClick()
        toggle()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(52.dp, 32.dp)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthF = width.toFloat()
        val heightF = height.toFloat()
        track.set(0f, 0f, widthF, heightF)

        paint.style = Paint.Style.FILL
        paint.color = UiPalette.blend(
            palette.surfaceContainerHigh,
            palette.accent,
            progress,
        )
        canvas.drawRoundRect(track, heightF / 2f, heightF / 2f, paint)

        if (progress < 0.5f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.dp.toFloat()
            paint.color = palette.outline
            canvas.drawRoundRect(track, heightF / 2f, heightF / 2f, paint)
        }

        val thumbRadius = 12.dp.toFloat()
        val startX = 16.dp.toFloat()
        val endX = widthF - 16.dp
        val centerX = startX + (endX - startX) * progress
        val centerY = heightF / 2f

        paint.style = Paint.Style.FILL
        paint.color = UiPalette.blend(
            palette.onSurfaceVariant,
            if (palette.isDark) 0xFF1D1B20.toInt() else 0xFFFFFFFF.toInt(),
            progress,
        )
        canvas.drawCircle(centerX, centerY, thumbRadius, paint)

        if (progress > 0.65f) {
            val alpha = ((progress - 0.65f) / 0.35f).coerceIn(0f, 1f)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.dp.toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = UiPalette.withAlpha(palette.accent, alpha)
            canvas.drawLine(
                centerX - 5.dp,
                centerY,
                centerX - 1.dp,
                centerY + 4.dp,
                paint,
            )
            canvas.drawLine(
                centerX - 1.dp,
                centerY + 4.dp,
                centerX + 6.dp,
                centerY - 4.dp,
                paint,
            )
        }
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        if (!isAttachedToWindow) {
            progress = target
            invalidate()
            return
        }

        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
