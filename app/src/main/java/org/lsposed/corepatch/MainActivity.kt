package org.lsposed.corepatch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import org.lsposed.corepatch.App.Companion.mService
import org.lsposed.corepatch.App.Companion.reloadListener
import org.lsposed.corepatch.data.SwitchData
import org.lsposed.corepatch.ui.CustomSwitchLayout
import org.lsposed.corepatch.ui.UiPalette
import org.lsposed.corepatch.ui.dp

class MainActivity : Activity() {
    private lateinit var palette: UiPalette
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var hotReloadInFlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reloadListener = { runOnUiThread { renderSafely() } }
        renderSafely()
    }

    private fun renderSafely() {
        try {
            showContent()
        } catch (t: Throwable) {
            Log.e("CorePatch", "UI render failed", t)
            showFallbackError(t)
        }
    }

    private fun showContent() {
        palette = UiPalette.from(this)

        val service = mService
        val active = runCatching {
            service != null && "system" in service.scope
        }.getOrElse { throwable ->
            App.serviceError =
                "scope · ${throwable.javaClass.simpleName}: ${throwable.message ?: "no message"}"
            false
        }

        setContentView(buildScreen(active))
        configureSystemBars()
    }

    private fun showFallbackError(throwable: Throwable) {
        val dark = (
            resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            ) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(24.dp, 24.dp, 24.dp, 24.dp)
            setBackgroundColor(if (dark) Color.rgb(20, 18, 24) else Color.WHITE)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.ui_recovery_title)
            setTextColor(if (dark) Color.WHITE else Color.BLACK)
            setTextSize(22f)
            typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = getString(
                R.string.ui_recovery_summary,
                throwable.javaClass.simpleName,
                throwable.message ?: "no message",
            )
            setTextColor(if (dark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt())
            setTextSize(14f)
            setPadding(0, 12.dp, 0, 0)
        })
        setContentView(root)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { window.setDecorFitsSystemWindows(true) }
        }
    }

    private fun configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                window.setDecorFitsSystemWindows(false)
                val controller = window.insetsController
                if (controller != null) {
                    val lightFlags =
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    controller.setSystemBarsAppearance(
                        if (palette.isDark) 0 else lightFlags,
                        lightFlags,
                    )
                }
            }.onFailure {
                Log.w("CorePatch", "System bar controller unavailable", it)
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = palette.background
                window.navigationBarColor = palette.background

                var flags = 0
                if (!palette.isDark) {
                    flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                    }
                }
                window.decorView.systemUiVisibility = flags
            }
        }
    }

    private fun buildScreen(active: Boolean): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(palette.background)
        }

        val horizontalPadding = 20.dp
        val designTopPadding = 16.dp
        val designBottomPadding = 36.dp

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                horizontalPadding,
                designTopPadding,
                horizontalPadding,
                designBottomPadding,
            )
        }
        scroll.addView(content, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                val cutout = insets.getInsets(WindowInsets.Type.displayCutout())
                topInset = maxOf(systemBars.top, cutout.top)
                bottomInset = maxOf(systemBars.bottom, cutout.bottom)
            } else {
                @Suppress("DEPRECATION")
                topInset = maxOf(
                    insets.systemWindowInsetTop,
                    insets.displayCutout?.safeInsetTop ?: 0,
                )
                @Suppress("DEPRECATION")
                bottomInset = maxOf(
                    insets.systemWindowInsetBottom,
                    insets.displayCutout?.safeInsetBottom ?: 0,
                )
            }

            content.setPadding(
                horizontalPadding,
                topInset + designTopPadding,
                horizontalPadding,
                bottomInset + designBottomPadding,
            )
            insets
        }
        scroll.post { scroll.requestApplyInsets() }

        content.addView(buildTopBar(), linearParams(bottom = 20.dp))
        content.addView(buildStatusCard(active), linearParams(bottom = 14.dp))

        if (active) {
            content.addView(
                buildHotReloadDiagnosticCard(),
                linearParams(bottom = 24.dp),
            )
        }

        if (!active) {
            content.addView(buildUnavailableCard(), linearParams())
            return scroll
        }

        buildPreferenceGroups().forEachIndexed { index, group ->
            content.addView(
                buildSectionHeader(group.first),
                linearParams(
                    top = if (index == 0) 0 else 12.dp,
                    bottom = 9.dp,
                )
            )

            group.second.forEachIndexed { itemIndex, item ->
                content.addView(
                    buildSwitchRow(item),
                    linearParams(
                        bottom = if (itemIndex == group.second.lastIndex) 3.dp else 7.dp
                    )
                )
            }
        }

        return scroll
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        titles.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(palette.onSurface)
            setTextSize(28f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = -0.02f
        })

        titles.addView(TextView(this).apply {
            text = getString(
                R.string.app_subtitle,
                BuildConfig.VERSION_NAME,
                Build.VERSION.RELEASE,
            )
            setTextColor(palette.onSurfaceVariant)
            setTextSize(13f)
            setPadding(0, 3.dp, 0, 0)
        })

        bar.addView(titles, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        bar.addView(TextView(this).apply {
            text = "⋮"
            gravity = Gravity.CENTER
            setTextColor(palette.onSurface)
            setTextSize(28f)
            contentDescription = getString(R.string.more_options)
            background = palette.rippleBackground(
                palette.background,
                24.dp.toFloat(),
            )
            setOnClickListener { showOverflowMenu(this) }
        }, LinearLayout.LayoutParams(48.dp, 48.dp))

        return bar
    }

    private fun buildStatusCard(active: Boolean): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18.dp, 18.dp, 18.dp, 18.dp)
            background = palette.roundedBackground(
                if (active) palette.accentContainer else palette.errorContainer,
                28.dp.toFloat(),
            )
        }

        val icon = TextView(this).apply {
            text = if (active) "✓" else "!"
            gravity = Gravity.CENTER
            setTextSize(20f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                if (active) palette.onAccentContainer else palette.onErrorContainer
            )
            background = palette.roundedBackground(
                if (active) palette.accent else palette.error,
                22.dp.toFloat(),
            )
        }
        card.addView(icon, LinearLayout.LayoutParams(44.dp, 44.dp).apply {
            marginEnd = 14.dp
        })

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textColumn.addView(TextView(this).apply {
            text = getString(
                if (active) R.string.module_active else R.string.module_inactive
            )
            setTextColor(
                if (active) palette.onAccentContainer else palette.onErrorContainer
            )
            setTextSize(17f)
            typeface = Typeface.DEFAULT_BOLD
        })
        textColumn.addView(TextView(this).apply {
            text = getString(
                if (active) R.string.module_active_summary else R.string.module_inactive_summary
            )
            setTextColor(
                if (active) palette.onAccentContainer else palette.onErrorContainer
            )
            alpha = 0.82f
            setTextSize(13f)
            setPadding(0, 3.dp, 0, 0)
        })

        card.addView(textColumn, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        if (active) {
            card.addView(TextView(this).apply {
                text = getString(R.string.hot_reload_badge)
                gravity = Gravity.CENTER
                setTextColor(palette.onSurface)
                setTextSize(11f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(10.dp, 6.dp, 10.dp, 6.dp)
                background = palette.roundedBackground(
                    palette.surfaceContainerHigh,
                    18.dp.toFloat(),
                )
            })
        }

        return card
    }

    private fun buildHotReloadDiagnosticCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 16.dp, 18.dp, 16.dp)
            background = palette.roundedBackground(
                palette.surfaceContainer,
                24.dp.toFloat(),
            )
        }

        card.addView(TextView(this).apply {
            setText(R.string.hot_reload_diagnostics)
            setTextColor(palette.onSurface)
            setTextSize(16f)
            typeface = Typeface.DEFAULT_BOLD
        })

        val statusView = TextView(this).apply {
            text = getString(
                R.string.hot_reload_diagnostics_idle,
                App.serviceApiVersion?.toString() ?: "?",
            )
            setTextColor(palette.onSurfaceVariant)
            setTextSize(13f)
            setLineSpacing(0f, 1.08f)
            setPadding(0, 6.dp, 0, 12.dp)
        }
        card.addView(statusView)

        val button = TextView(this).apply {
            setText(R.string.hot_reload_test)
            gravity = Gravity.CENTER
            setTextColor(palette.onAccentContainer)
            setTextSize(14f)
            typeface = Typeface.DEFAULT_BOLD
            minHeight = 44.dp
            setPadding(14.dp, 10.dp, 14.dp, 10.dp)
            background = palette.rippleBackground(
                palette.accentContainer,
                18.dp.toFloat(),
            )
        }
        button.setOnClickListener {
            runHotReloadDiagnostic(statusView, button)
        }
        card.addView(button, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        return card
    }

    private fun runHotReloadDiagnostic(
        statusView: TextView,
        button: TextView,
    ) {
        val service = mService
        if (service == null) {
            safeHotReloadUi(statusView, button) {
                statusView.setText(R.string.xposed_service_unavailable)
            }
            return
        }

        if (hotReloadInFlight) {
            safeHotReloadUi(statusView, button) {
                statusView.setText(R.string.hot_reload_already_running)
                button.isEnabled = false
            }
            return
        }

        hotReloadInFlight = true
        button.isEnabled = false
        statusView.setText(R.string.hot_reload_checking)

        Thread {
            try {
                val targets = service.getRunningTargets()
                val systemTarget = targets.firstOrNull { target ->
                    target.processName == "system_server" ||
                        target.processName == "system"
                }

                if (systemTarget == null) {
                    val seen = targets.joinToString(", ") { it.processName }
                        .ifEmpty { "none" }
                    hotReloadInFlight = false
                    safeHotReloadUi(statusView, button) {
                        statusView.text = getString(
                            R.string.hot_reload_no_system_target,
                            seen,
                        )
                        button.isEnabled = true
                    }
                    return@Thread
                }

                val state = systemTarget.state.toString()
                val before = getString(
                    R.string.hot_reload_target_summary,
                    systemTarget.processName,
                    state,
                    systemTarget.loadedVersionCode.toString(),
                    BuildConfig.VERSION_CODE.toString(),
                )

                safeHotReloadUi(statusView, button) {
                    statusView.text = before
                }

                when (state) {
                    "UP_TO_DATE" -> {
                        hotReloadInFlight = false
                        safeHotReloadUi(statusView, button) {
                            statusView.text = getString(
                                R.string.hot_reload_up_to_date,
                                systemTarget.processName,
                                systemTarget.loadedVersionCode.toString(),
                                BuildConfig.VERSION_CODE.toString(),
                            )
                            button.isEnabled = true
                        }
                        return@Thread
                    }

                    "RELOADING" -> {
                        hotReloadInFlight = false
                        safeHotReloadUi(statusView, button) {
                            statusView.setText(R.string.hot_reload_already_running)
                            button.isEnabled = true
                        }
                        return@Thread
                    }

                    "FAILED" -> {
                        hotReloadInFlight = false
                        safeHotReloadUi(statusView, button) {
                            statusView.text = getString(
                                R.string.hot_reload_failed_state,
                                systemTarget.processName,
                                systemTarget.loadedVersionCode.toString(),
                                BuildConfig.VERSION_CODE.toString(),
                            )
                            button.isEnabled = true
                        }
                        return@Thread
                    }

                    "STALE" -> Unit

                    else -> {
                        hotReloadInFlight = false
                        safeHotReloadUi(statusView, button) {
                            statusView.text = getString(
                                R.string.hot_reload_unknown_state,
                                state,
                            )
                            button.isEnabled = true
                        }
                        return@Thread
                    }
                }

                service.hotReloadModule(systemTarget, null) { target, result ->
                    hotReloadInFlight = false
                    safeHotReloadUi(statusView, button) {
                        statusView.text = getString(
                            R.string.hot_reload_result,
                            target.processName,
                            target.state.toString(),
                            target.loadedVersionCode.toString(),
                            BuildConfig.VERSION_CODE.toString(),
                            result.status().toString(),
                            result.message() ?: "—",
                        )
                        button.isEnabled = true
                    }
                }
            } catch (t: Throwable) {
                hotReloadInFlight = false
                Log.e("CorePatch", "Hot reload diagnostic failed", t)
                safeHotReloadUi(statusView, button) {
                    statusView.text = getString(
                        R.string.hot_reload_error,
                        t.javaClass.simpleName,
                        t.message ?: "no message",
                    )
                    button.isEnabled = true
                }
            }
        }.start()
    }

    private fun safeHotReloadUi(
        statusView: TextView,
        button: TextView,
        action: () -> Unit,
    ) {
        mainHandler.post {
            if (isFinishing || isDestroyed) return@post
            if (!statusView.isAttachedToWindow || !button.isAttachedToWindow) return@post
            runCatching(action).onFailure {
                Log.e("CorePatch", "Hot reload UI callback failed", it)
            }
        }
    }

    private fun buildUnavailableCard(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 20.dp, 20.dp, 20.dp)
            background = palette.roundedBackground(
                palette.surfaceContainer,
                28.dp.toFloat(),
            )

            addView(TextView(this@MainActivity).apply {
                setText(R.string.xposed_service_unavailable)
                setTextColor(palette.onSurface)
                setTextSize(18f)
                typeface = Typeface.DEFAULT_BOLD
            })

            addView(TextView(this@MainActivity).apply {
                setText(R.string.xposed_service_unavailable_summary)
                setTextColor(palette.onSurfaceVariant)
                setTextSize(14f)
                setPadding(0, 8.dp, 0, 0)
            })

            App.serviceError?.let { error ->
                addView(TextView(this@MainActivity).apply {
                    text = getString(R.string.xposed_service_error_detail, error)
                    setTextColor(palette.error)
                    setTextSize(12f)
                    setPadding(0, 12.dp, 0, 0)
                })
            }
        }
    }

    private fun buildSectionHeader(title: String): View {
        return TextView(this).apply {
            text = title
            setTextColor(palette.accent)
            setTextSize(13f)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.04f
            setPadding(4.dp, 0, 4.dp, 0)
        }
    }

    private fun buildSwitchRow(data: SwitchData): View {
        return CustomSwitchLayout(this).apply {
            titleView.text = data.title
            subtitleView.text = data.description

            setOnCheckListener {}
            switchView.isChecked = Config.getConfig(data.key)
            setOnCheckListener { checked ->
                Config.setConfig(data.key, checked)
                if (checked && data.warning != null) {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle(data.title)
                        .setMessage(data.warning)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun buildPreferenceGroups(): List<Pair<String, List<SwitchData>>> {
        val installation = listOf(
            SwitchData(
                getString(R.string.bypass_downgrade),
                getString(R.string.bypass_downgrade_summary),
                Config.BYPASS_DOWNGRADE,
            ),
            SwitchData(
                getString(R.string.bypass_resource_arsc_restrictions),
                getString(R.string.bypass_resource_arsc_restrictions_summary),
                Config.BYPASS_RESOURCE_ARSC_RESTRICTIONS,
            ),
            SwitchData(
                getString(R.string.bypass_block),
                getString(R.string.bypass_block_summary),
                Config.BYPASS_BLOCK,
            ),
        )

        val signature = listOf(
            SwitchData(
                getString(R.string.bypass_verification),
                getString(R.string.bypass_verification_summary),
                Config.BYPASS_VERIFICATION,
            ),
            SwitchData(
                getString(R.string.bypass_digest),
                getString(R.string.bypass_digest_summary),
                Config.BYPASS_DIGEST,
            ),
            SwitchData(
                getString(R.string.bypass_exact_signature_match),
                getString(R.string.bypass_exact_signature_match_summary),
                Config.BYPASS_EXACT_SIGNATURE_MATCH,
            ),
            SwitchData(
                getString(R.string.use_previous_signatures),
                getString(R.string.use_previous_signatures_summary),
                Config.USE_PREVIOUS_SIGNATURES,
                if (isMiui()) {
                    getString(R.string.miui_usepresig_warn) +
                        "\n\n" +
                        getString(R.string.use_previous_signatures_warning)
                } else {
                    getString(R.string.use_previous_signatures_warning)
                },
            ),
            SwitchData(
                getString(R.string.bypass_shared_user),
                getString(R.string.bypass_shared_user_summary),
                Config.BYPASS_SHARED_USER,
            ),
        )

        val system = listOf(
            SwitchData(
                getString(R.string.allow_hidden_apis_for_system_apps),
                getString(R.string.allow_hidden_apis_for_system_apps_summary),
                Config.ALLOW_HIDDEN_APIS_FOR_SYSTEM_APPS,
            ),
            SwitchData(
                getString(R.string.disable_verification_agent),
                getString(R.string.disable_verification_agent_summary),
                Config.DISABLE_VERIFICATION_AGENT,
            ),
            SwitchData(
                getString(R.string.bypass_developer_verification),
                getString(R.string.bypass_developer_verification_summary),
                Config.BYPASS_DEVELOPER_VERIFICATION,
                getString(R.string.bypass_developer_verification_warning),
            ),
            SwitchData(
                getString(R.string.pms_debug_command),
                getString(R.string.pms_debug_command_summary),
                Config.PMS_DEBUG_COMMAND,
            ),
        )

        return listOf(
            getString(R.string.section_installation) to installation,
            getString(R.string.section_signature) to signature,
            getString(R.string.section_system) to system,
        )
    }

    private fun showOverflowMenu(anchor: View) {
        val component = ComponentName(this, "$packageName.LauncherAlias")
        val hidden = packageManager.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        PopupMenu(this, anchor).apply {
            menu.add(R.string.hide_launcher_icon).apply {
                isCheckable = true
                isChecked = hidden
            }
            setOnMenuItemClickListener {
                toggleLauncherIcon()
                true
            }
            show()
        }
    }

    private fun toggleLauncherIcon() {
        val component = ComponentName(this, "$packageName.LauncherAlias")
        val hidden = packageManager.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        packageManager.setComponentEnabledSetting(
            component,
            if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun linearParams(
        top: Int = 0,
        bottom: Int = 0,
    ) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        topMargin = top
        bottomMargin = bottom
    }

    @SuppressLint("PrivateApi")
    private fun isMiui(): Boolean = try {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getMethod("get", String::class.java)
        (get.invoke(null, "ro.miui.ui.version.code") as String).isNotEmpty()
    } catch (_: ReflectiveOperationException) {
        false
    }

    override fun onStart() {
        super.onStart()
        reloadListener = {
            if (!hotReloadInFlight) {
                runOnUiThread { renderSafely() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        reloadListener = {}
    }
}
