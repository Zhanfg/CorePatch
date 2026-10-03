package org.lsposed.corepatch.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build

data class UiPalette(
    val isDark: Boolean,
    val background: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val accent: Int,
    val accentContainer: Int,
    val onAccentContainer: Int,
    val outline: Int,
    val error: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val ripple: Int,
) {
    fun roundedBackground(color: Int, radius: Float): Drawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }

    fun rippleBackground(color: Int, radius: Float): Drawable {
        val content = roundedBackground(color, radius)
        val mask = roundedBackground(Color.WHITE, radius)
        return RippleDrawable(ColorStateList.valueOf(ripple), content, mask)
    }

    companion object {
        fun from(context: Context): UiPalette {
            val dark = (
                context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                ) == Configuration.UI_MODE_NIGHT_YES

            val background = if (dark) {
                Color.rgb(20, 18, 24)
            } else {
                Color.rgb(255, 247, 255)
            }
            val onSurface = if (dark) {
                Color.rgb(230, 224, 233)
            } else {
                Color.rgb(29, 27, 32)
            }
            val onSurfaceVariant = if (dark) {
                Color.rgb(202, 196, 208)
            } else {
                Color.rgb(73, 69, 79)
            }

            val accentFallback = if (dark) {
                Color.rgb(208, 188, 255)
            } else {
                Color.rgb(103, 80, 164)
            }
            val accent = resolveSystemAccent(context, dark, accentFallback)

            val surfaceContainer =
                blend(background, onSurface, if (dark) 0.08f else 0.055f)
            val surfaceContainerHigh =
                blend(background, onSurface, if (dark) 0.13f else 0.085f)
            val accentContainer =
                blend(background, accent, if (dark) 0.30f else 0.18f)
            val onAccentContainer =
                blend(onSurface, accent, if (dark) 0.12f else 0.08f)
            val outline = if (dark) {
                Color.rgb(147, 143, 153)
            } else {
                Color.rgb(121, 116, 126)
            }
            val error = if (dark) {
                Color.rgb(255, 180, 171)
            } else {
                Color.rgb(186, 26, 26)
            }
            val errorContainer = if (dark) {
                Color.rgb(65, 0, 2)
            } else {
                Color.rgb(255, 218, 214)
            }
            val onErrorContainer = if (dark) {
                Color.rgb(255, 218, 214)
            } else {
                Color.rgb(65, 0, 2)
            }
            val ripple = withAlpha(accent, if (dark) 0.24f else 0.16f)

            return UiPalette(
                isDark = dark,
                background = background,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                onSurface = onSurface,
                onSurfaceVariant = onSurfaceVariant,
                accent = accent,
                accentContainer = accentContainer,
                onAccentContainer = onAccentContainer,
                outline = outline,
                error = error,
                errorContainer = errorContainer,
                onErrorContainer = onErrorContainer,
                ripple = ripple,
            )
        }

        private fun resolveSystemAccent(
            context: Context,
            dark: Boolean,
            fallback: Int,
        ): Int {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val name = if (dark) "system_accent1_200" else "system_accent1_600"
                val id = context.resources.getIdentifier(name, "color", "android")
                if (id != 0) {
                    return runCatching { context.getColor(id) }
                        .getOrDefault(fallback)
                }
            }
            return fallback
        }

        fun blend(base: Int, overlay: Int, amount: Float): Int {
            val t = amount.coerceIn(0f, 1f)
            val r = (Color.red(base) * (1f - t) + Color.red(overlay) * t).toInt()
            val g = (Color.green(base) * (1f - t) + Color.green(overlay) * t).toInt()
            val b = (Color.blue(base) * (1f - t) + Color.blue(overlay) * t).toInt()
            return Color.rgb(r, g, b)
        }

        fun withAlpha(color: Int, amount: Float): Int =
            Color.argb(
                (255 * amount.coerceIn(0f, 1f)).toInt(),
                Color.red(color),
                Color.green(color),
                Color.blue(color),
            )
    }
}
