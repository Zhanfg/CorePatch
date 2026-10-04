package org.lsposed.corepatch.hook

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import org.lsposed.corepatch.Config
import org.lsposed.corepatch.XposedHelper.findClassIfExists
import org.lsposed.corepatch.XposedHelper.hookBefore
import org.lsposed.corepatch.XposedHelper.log
import java.io.PrintWriter
import java.lang.reflect.Method

object PmsDebugCommandHook : BaseHook() {
    override val name = "PmsDebugCommandHook"

    private const val USER_SYSTEM = 0
    private val signingFlags = PackageManager.GET_SIGNING_CERTIFICATES.toLong()

    override fun hook() {
        val shellClass = findClassIfExists("com.android.server.pm.PackageManagerShellCommand") ?: return
        val onCommand = HookResolver.findMethod(shellClass, "onCommand") { method ->
            method.parameterTypes.contentEquals(arrayOf(String::class.java))
        } ?: return

        hookBefore(onCommand) { callback ->
            if (!Config.isPmsDebugCommandEnabled()) return@hookBefore
            if (callback.args.getOrNull(0) != "pms") return@hookBefore

            val shell = callback.thisObject ?: return@hookBefore
            val writer = callNoArg(shell, "getOutPrintWriter") as? PrintWriter ?: return@hookBefore

            runCatching {
                when ((callNoArg(shell, "getNextArg") as? String)?.lowercase()) {
                    "p", "package" -> {
                        val packageName = nextArg(shell, writer) ?: return@runCatching
                        dumpPackage(shell, packageName, writer)
                    }
                    "uid" -> {
                        val raw = nextArg(shell, writer) ?: return@runCatching
                        val uid = raw.toIntOrNull()
                        if (uid == null) writer.println("Invalid UID: " + raw)
                        else dumpUid(shell, uid, writer)
                    }
                    "su", "shareduser" -> {
                        val sharedUser = nextArg(shell, writer) ?: return@runCatching
                        dumpSharedUser(shell, sharedUser, writer)
                    }
                    "help", null -> printHelp(writer)
                    else -> printHelp(writer)
                }
            }.onFailure { throwable ->
                log("[" + name + "] command failed", throwable)
                writer.println(
                    "CorePatch PMS diagnostic failed: " + throwable.javaClass.simpleName +
                        ": " + (throwable.message ?: "no message")
                )
            }

            callback.returnAndSkip(0)
        }
    }

    private fun printHelp(writer: PrintWriter) {
        writer.println("CorePatch PMS diagnostics")
        writer.println("  pm pms package <package>")
        writer.println("  pm pms uid <uid>")
        writer.println("  pm pms shareduser <sharedUserId>")
    }

    private fun dumpPackage(shell: Any, packageName: String, writer: PrintWriter) {
        val pm = packageManagerInterface(shell) ?: error("PackageManager interface unavailable")
        val info = packageInfo(pm, packageName)
        if (info == null) {
            writer.println("Package not found: " + packageName)
            return
        }

        writer.println("package=" + packageName)
        writer.println("versionName=" + (info.versionName ?: "—"))
        writer.println("versionCode=" + info.longVersionCode)
        writer.println("uid=" + (info.applicationInfo?.uid ?: -1))
        writer.println("sharedUserId=" + (info.sharedUserId ?: "—"))
        writer.println("sourceDir=" + (info.applicationInfo?.sourceDir ?: "—"))
        writer.println("installer=" + (installerPackageName(pm, packageName) ?: "—"))

        val signingInfo = info.signingInfo
        if (signingInfo == null) {
            writer.println("signingInfo=unavailable")
            return
        }

        val scheme = signingScheme(signingInfo)
        writer.println(
            "signatureScheme=" + (scheme.first?.toString() ?: "unknown") +
                (scheme.second?.let { "." + it } ?: "")
        )
        writer.println("multipleSigners=" + signingInfo.hasMultipleSigners())

        val current = signingInfo.apkContentsSigners ?: emptyArray()
        writer.println("currentSigners=" + current.size)
        current.forEachIndexed { index, signature ->
            dumpSignature("signer[" + index + "]", signature, writer)
        }

        val history = signingInfo.signingCertificateHistory ?: emptyArray()
        writer.println("lineage=" + history.size)
        history.forEachIndexed { index, signature ->
            dumpSignature("lineage[" + index + "]", signature, writer)
        }
    }

    private fun dumpUid(shell: Any, uid: Int, writer: PrintWriter) {
        val pm = packageManagerInterface(shell) ?: error("PackageManager interface unavailable")
        val method = findMethod(pm.javaClass, "getPackagesForUid") { it.parameterCount == 1 }
            ?: error("getPackagesForUid unavailable")
        val packages = method.invoke(pm, uid) as? Array<*>
        writer.println("uid=" + uid)
        if (packages.isNullOrEmpty()) {
            writer.println("packages=none")
            return
        }
        packages.filterIsInstance<String>().forEach { writer.println("package=" + it) }
    }

    private fun dumpSharedUser(shell: Any, sharedUser: String, writer: PrintWriter) {
        val pm = packageManagerInterface(shell) ?: error("PackageManager interface unavailable")
        val method = findMethod(pm.javaClass, "getInstalledPackages") { it.parameterCount >= 2 }
            ?: error("getInstalledPackages unavailable")
        val args = Array<Any?>(method.parameterCount) { null }
        args[0] = signingFlags
        args[1] = USER_SYSTEM
        for (index in 2 until args.size) args[index] = defaultValue(method.parameterTypes[index])
        val slice = method.invoke(pm, *args) ?: error("installed package list unavailable")
        val getList = findMethod(slice.javaClass, "getList") { it.parameterCount == 0 }
            ?: error("ParceledListSlice.getList unavailable")
        val packages = (getList.invoke(slice) as? List<*>)
            ?.filterIsInstance<PackageInfo>()
            .orEmpty()
            .filter { it.sharedUserId == sharedUser }

        writer.println("sharedUserId=" + sharedUser)
        if (packages.isEmpty()) writer.println("packages=none")
        else packages.forEach {
            writer.println("package=" + it.packageName + " uid=" + (it.applicationInfo?.uid ?: -1))
        }
    }

    private fun packageInfo(pm: Any, packageName: String): PackageInfo? {
        val method = findMethod(pm.javaClass, "getPackageInfo") { candidate ->
            candidate.parameterCount >= 3 && candidate.parameterTypes[0] == String::class.java
        } ?: return null
        val args = Array<Any?>(method.parameterCount) { null }
        args[0] = packageName
        args[1] = signingFlags
        args[2] = USER_SYSTEM
        for (index in 3 until args.size) args[index] = defaultValue(method.parameterTypes[index])
        return method.invoke(pm, *args) as? PackageInfo
    }

    private fun installerPackageName(pm: Any, packageName: String): String? {
        val method = findMethod(pm.javaClass, "getInstallSourceInfo") { candidate ->
            candidate.parameterCount >= 2 && candidate.parameterTypes[0] == String::class.java
        } ?: return null
        val args = Array<Any?>(method.parameterCount) { null }
        args[0] = packageName
        args[1] = USER_SYSTEM
        for (index in 2 until args.size) args[index] = defaultValue(method.parameterTypes[index])
        val source = runCatching { method.invoke(pm, *args) }.getOrNull() ?: return null
        return runCatching {
            source.javaClass.getMethod("getInstallingPackageName").invoke(source) as? String
        }.getOrNull()
    }

    private fun signingScheme(signingInfo: Any): Pair<Int?, Int?> {
        val details = runCatching {
            signingInfo.javaClass.getDeclaredField("mSigningDetails").apply { isAccessible = true }
                .get(signingInfo)
        }.getOrNull() ?: return null to null
        return readInt(details, "getSignatureSchemeVersion", "signatureSchemeVersion") to
            readInt(details, "getSignatureSchemeMinorVersion", "signatureSchemeMinorVersion")
    }

    private fun readInt(instance: Any, vararg names: String): Int? {
        names.forEach { name ->
            findMethod(instance.javaClass, name) { it.parameterCount == 0 }?.let { method ->
                runCatching { return method.invoke(instance) as? Int }
            }
            runCatching {
                val field = instance.javaClass.getDeclaredField(name).apply { isAccessible = true }
                return field.getInt(instance)
            }
        }
        return null
    }

    private fun dumpSignature(prefix: String, signature: Signature, writer: PrintWriter) {
        writer.println(prefix + ".sha256=" + sha256(signature.toByteArray()))
        writer.println(prefix + ".chars=" + signature.toCharsString())
    }

    private fun sha256(bytes: ByteArray): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun packageManagerInterface(shell: Any): Any? = runCatching {
        shell.javaClass.getDeclaredField("mInterface").apply { isAccessible = true }.get(shell)
    }.getOrNull()

    private fun nextArg(shell: Any, writer: PrintWriter): String? {
        val value = callNoArg(shell, "getNextArg") as? String
        if (value == null) writer.println("Missing required argument")
        return value
    }

    private fun callNoArg(instance: Any, name: String): Any? =
        findMethod(instance.javaClass, name) { it.parameterCount == 0 }?.invoke(instance)

    private fun findMethod(
        start: Class<*>,
        name: String,
        predicate: (Method) -> Boolean,
    ): Method? {
        var clazz: Class<*>? = start
        while (clazz != null) {
            clazz.declaredMethods.firstOrNull { it.name == name && predicate(it) }?.let { method ->
                method.isAccessible = true
                return method
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Float::class.javaPrimitiveType -> 0f
        Double::class.javaPrimitiveType -> 0.0
        else -> null
    }
}