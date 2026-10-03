package org.lsposed.corepatch.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView

class CustomSwitchLayout(context: Context) : LinearLayout(context) {
    private val palette = UiPalette.from(context)

    val titleView = TextView(context).apply {
        setTextColor(palette.onSurface)
        setTextSize(16f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val subtitleView = TextView(context).apply {
        setTextColor(palette.onSurfaceVariant)
        setTextSize(13f)
        setLineSpacing(0f, 1.08f)
        setPadding(0, 4.dp, 0, 0)
    }

    val switchView = ExpressiveSwitch(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = 82.dp
        setPadding(18.dp, 15.dp, 16.dp, 15.dp)
        background = palette.rippleBackground(
            palette.surfaceContainer,
            26.dp.toFloat(),
        )

        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            addView(titleView, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(subtitleView, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }

        addView(textColumn, LayoutParams(0, WRAP_CONTENT, 1f).apply {
            marginEnd = 16.dp
        })
        addView(switchView, LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        setOnClickListener { switchView.toggle() }
    }

    fun setOnCheckListener(listener: (Boolean) -> Unit) {
        switchView.setOnCheckedChangeListener(listener)
    }
}
