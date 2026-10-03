package org.lsposed.corepatch.hook

import android.os.Build
import org.lsposed.corepatch.Config
import org.lsposed.corepatch.XposedHelper
import org.lsposed.corepatch.XposedHelper.findClassIfExists
import org.lsposed.corepatch.XposedHelper.hookBefore
import org.lsposed.corepatch.XposedHelper.log

/**
 * Android 16.1 / 17 Developer Verification is separate from legacy Package Verification.
 * On supported platform builds PackageInstallerSession asks the configured verifier whether
 * the developer/package/signing identity is registered. Returning false from both gates keeps
 * the rest of PackageInstallerSession's normal verification/install pipeline intact.
 */
object DeveloperVerificationHook : BaseHook() {
    override val name = "DeveloperVerificationHook"

    override fun hook() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return

        val sessionClass = findClassIfExists(
            "com.android.server.pm.PackageInstallerSession"
        ) ?: run {
            log("[$name] PackageInstallerSession unavailable")
            return
        }

        var installed = 0
        listOf(
            "isVerificationServiceEnabled",
            "shouldUseVerificationService",
        ).forEach { methodName ->
            HookResolver.findMethods(sessionClass, methodName) { method ->
                method.parameterCount == 0 &&
                    method.returnType == Boolean::class.javaPrimitiveType
            }.forEach { method ->
                if (!XposedHelper.deoptimize(method)) {
                    log("[$name] failed to deoptimize $methodName")
                }
                hookBefore(method) { callback ->
                    if (Config.isBypassDeveloperVerificationEnabled()) {
                        callback.returnAndSkip(false)
                    }
                }
                installed++
            }
        }

        if (installed == 0) {
            log("[$name] no Developer Verification gates resolved")
        } else {
            log("[$name] installed $installed Developer Verification gate hooks")
        }
    }
}
