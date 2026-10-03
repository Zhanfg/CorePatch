package org.lsposed.corepatch.hook

import android.annotation.SuppressLint
import android.os.Build
import org.lsposed.corepatch.Config
import org.lsposed.corepatch.XposedHelper.getOriginInvoker
import org.lsposed.corepatch.XposedHelper.hookAfter
import org.lsposed.corepatch.XposedHelper.hostClassLoader
import org.lsposed.corepatch.XposedHelper.log

object InstallPackageHelperHook : BaseHook() {
    override val name = "InstallPackageHelperHook"

    @SuppressLint("PrivateApi")
    override fun hook() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val installPackageHelperClazz =
            hostClassLoader.loadClass("com.android.server.pm.InstallPackageHelper")
        val doesSignatureMatchForPermissionsMethod = HookResolver.findMethod(
            installPackageHelperClazz,
            "doesSignatureMatchForPermissions"
        ) { method ->
            method.returnType == Boolean::class.javaPrimitiveType &&
                method.parameterCount >= 3
        } ?: return

        val doesSignatureMatchForPermissionsInvoker =
            getOriginInvoker(doesSignatureMatchForPermissionsMethod)
        val parsedPackageClass = doesSignatureMatchForPermissionsMethod.parameterTypes[1]
        val getPackageNameMethod = parsedPackageClass.methods.firstOrNull { method ->
            method.name == "getPackageName" && method.parameterCount == 0
        }?.apply { isAccessible = true } ?: run {
            log("[$name] getPackageName is unavailable")
            return
        }
        val installedPackages = createInstalledPackageLookup(installPackageHelperClazz)

        hookAfter(doesSignatureMatchForPermissionsMethod) hook@{ callback ->
            if (!Config.isBypassDigestEnabled() || !Config.isUsePreviousSignaturesEnabled()) {
                return@hook
            }
            if (callback.result != false) return@hook

            val parsedPackage = callback.args.getOrNull(1) ?: return@hook
            val packageName = getPackageNameMethod.invoke(parsedPackage) as? String ?: return@hook
            val sourcePackageName = callback.args.getOrNull(0) as? String ?: return@hook

            if (packageName == sourcePackageName) {
                callback.result = true
                return@hook
            }

            val helper = callback.thisObject ?: return@hook
            val oldPackage = installedPackages?.invoke(helper, packageName) ?: return@hook
            if (!parsedPackageClass.isInstance(oldPackage)) return@hook

            val matches = runCatching {
                val args = callback.args
                if (args.size < 3) return@runCatching false
                doesSignatureMatchForPermissionsInvoker?.invoke(
                    helper,
                    sourcePackageName,
                    oldPackage,
                    args[2],
                ) as? Boolean == true
            }.getOrElse { throwable ->
                log("[$name] failed to check the installed package signature", throwable)
                false
            }

            if (matches) callback.result = true
        }
    }

    private fun createInstalledPackageLookup(
        installPackageHelperClazz: Class<*>
    ): ((Any, String) -> Any?)? =
        runCatching {
            val packageManagerField = installPackageHelperClazz.getDeclaredField("mPm").apply {
                isAccessible = true
            }
            val packageManagerClazz = packageManagerField.type
            val snapshotComputerMethod = HookResolver.findMethod(
                packageManagerClazz,
                "snapshotComputer"
            ) { it.parameterCount == 0 } ?: error("snapshotComputer unavailable")

            val getPackageStateMethod = HookResolver.findMethod(
                snapshotComputerMethod.returnType,
                "getPackageStateInternal"
            ) { method ->
                method.parameterTypes.contentEquals(arrayOf(String::class.java))
            } ?: error("getPackageStateInternal unavailable")

            val getAndroidPackageMethod = getPackageStateMethod.returnType.methods.firstOrNull { method ->
                method.name == "getAndroidPackage" && method.parameterCount == 0
            }?.apply { isAccessible = true } ?: error("getAndroidPackage unavailable")

            val lookup: (Any, String) -> Any? = { helper, packageName ->
                runCatching {
                    val packageManager = packageManagerField.get(helper)
                    val computer = snapshotComputerMethod.invoke(packageManager)
                    val packageState = getPackageStateMethod.invoke(computer, packageName)
                    packageState?.let { getAndroidPackageMethod.invoke(it) }
                }.getOrElse { throwable ->
                    log("[$name] failed to find the installed package", throwable)
                    null
                }
            }
            lookup
        }.getOrElse { throwable ->
            log("[$name] installed package lookup is unavailable", throwable)
            null
        }
}
