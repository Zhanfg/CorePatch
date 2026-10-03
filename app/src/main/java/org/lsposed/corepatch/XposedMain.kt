package org.lsposed.corepatch

import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.lsposed.corepatch.Config.printAllConfig
import org.lsposed.corepatch.hook.ApkSignatureVerifierHook
import org.lsposed.corepatch.hook.ApkSigningBlockUtilsHook
import org.lsposed.corepatch.hook.ApplicationInfoHook
import org.lsposed.corepatch.hook.AssetManagerHook
import org.lsposed.corepatch.hook.DeveloperVerificationHook
import org.lsposed.corepatch.hook.InstallPackageHelperHook
import org.lsposed.corepatch.hook.KeySetManagerServiceHook
import org.lsposed.corepatch.hook.MessageDigestHook
import org.lsposed.corepatch.hook.NtConfigListServiceImplHook
import org.lsposed.corepatch.hook.PackageManagerServiceHook
import org.lsposed.corepatch.hook.PackageManagerServiceUtilsHook
import org.lsposed.corepatch.hook.ReconcilePackageUtilsHook
import org.lsposed.corepatch.hook.ScanPackageUtilsHook
import org.lsposed.corepatch.hook.SharedUserSettingHook
import org.lsposed.corepatch.hook.SigningDetailsHook
import org.lsposed.corepatch.hook.StrictJarVerifierHook
import org.lsposed.corepatch.hook.VerificationParamsHook
import org.lsposed.corepatch.hook.VerifyingSessionHook

class XposedMain : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        XposedHelper.setXposedModule(this)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        XposedHelper.log("onSystemServerStarting: Current sdk version is ${Build.VERSION.SDK_INT}")

        XposedHelper.setHostClassLoader(param.classLoader)
        installSystemHooks()
    }

    override fun onHotReloading(
        param: XposedModuleInterface.HotReloadingParam
    ): Boolean {
        if (!XposedHelper.isHotReloadReady()) {
            XposedHelper.log("onHotReloading: current generation is not reload-ready")
            return false
        }

        // Only classloader-neutral state may cross module generations.
        param.setSavedInstanceState("CorePatch:${BuildConfig.VERSION_NAME}")
        XposedHelper.log("onHotReloading: allowing API 102 hot reload")
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        XposedHelper.setXposedModule(this)

        val oldHandles = param.oldHookHandles
        val classLoader = oldHandles.asSequence()
            .mapNotNull { runCatching { it.executable.declaringClass.classLoader }.getOrNull() }
            .firstOrNull()
            ?: Thread.currentThread().contextClassLoader
            ?: ClassLoader.getSystemClassLoader()

        XposedHelper.setHostClassLoader(classLoader)
        XposedHelper.log("onHotReloaded: ${oldHandles.size} old hooks")

        if (!XposedHelper.beginHotReload(oldHandles)) {
            XposedHelper.log("onHotReloaded: falling back to full unhook + rehook")
            super.onHotReloaded(param)
            installSystemHooks()
            return
        }

        try {
            installSystemHooks()
        } finally {
            XposedHelper.finishHotReload()
        }
    }

    private fun installSystemHooks() {
        runCatching { printAllConfig() }
            .onFailure { XposedHelper.log("failed to read config during hook install", it) }

        val hooks = listOf(
            ApkSignatureVerifierHook,
            ApkSigningBlockUtilsHook,
            ApplicationInfoHook,
            AssetManagerHook,
            DeveloperVerificationHook,
            InstallPackageHelperHook,
            KeySetManagerServiceHook,
            MessageDigestHook,
            NtConfigListServiceImplHook,
            PackageManagerServiceHook,
            PackageManagerServiceUtilsHook,
            ReconcilePackageUtilsHook,
            ScanPackageUtilsHook,
            SharedUserSettingHook,
            SigningDetailsHook,
            StrictJarVerifierHook,
            VerificationParamsHook,
            VerifyingSessionHook,
        )
        hooks.forEach { it.init() }
    }
}
