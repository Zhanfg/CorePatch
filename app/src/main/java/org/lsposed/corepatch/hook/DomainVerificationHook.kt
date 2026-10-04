package org.lsposed.corepatch.hook

import org.lsposed.corepatch.Config
import org.lsposed.corepatch.XposedHelper.findClassIfExists
import org.lsposed.corepatch.XposedHelper.hookBefore
import org.lsposed.corepatch.XposedHelper.log

/**
 * Stops Android App Links domain-verification requests from being broadcast to the verifier.
 * Existing verification state and explicit user link choices are left untouched.
 */
object DomainVerificationHook : BaseHook() {
    override val name = "DomainVerificationHook"

    override fun hook() {
        val service = findClassIfExists(
            "com.android.server.pm.verify.domain.DomainVerificationService"
        ) ?: return

        val methods = HookResolver.findMethods(service, "sendBroadcast") { method ->
            method.returnType == Void.TYPE && method.parameterCount == 1
        }

        methods.forEach { method ->
            hookBefore(method) { callback ->
                if (Config.isDomainVerificationDisabled()) {
                    callback.returnAndSkip(null)
                }
            }
        }

        log("[" + name + "] installed " + methods.size + " domain broadcast gate hooks")
    }
}
