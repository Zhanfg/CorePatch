package org.lsposed.corepatch

import org.lsposed.corepatch.App.Companion.rwPrefs
import org.lsposed.corepatch.XposedHelper.prefs

object Config {
    const val BYPASS_DOWNGRADE = "downgrade"
    const val BYPASS_VERIFICATION = "bypass_verification"
    const val DISABLE_JAR_VERIFIER = "disable_jar_verifier"
    const val DISABLE_MESSAGE_DIGEST = "disable_message_digest"
    const val BYPASS_MIN_SIGNATURE_VERSION = "bypass_min_signature_version"
    const val BYPASS_V1_SIGNATURE_ERRORS = "bypass_v1_signature_errors"
    const val BYPASS_RESOURCE_ARSC_RESTRICTIONS = "bypass_resource_arsc_restrictions"
    const val BYPASS_DIGEST = "bypass_digest"
    const val BYPASS_EXACT_SIGNATURE_MATCH = "bypass_exact_sig_match"
    const val USE_PREVIOUS_SIGNATURES = "use_previous_signatures"
    const val ALLOW_HIDDEN_APIS_FOR_SYSTEM_APPS = "allow_hidden_apis_for_system_apps"
    const val BYPASS_SHARED_USER = "bypass_shared_user"
    const val DISABLE_VERIFICATION_AGENT = "disable_verification_agent"
    const val BYPASS_DEVELOPER_VERIFICATION = "bypass_developer_verification"
    const val PMS_DEBUG_COMMAND = "pms_debug_command"
    const val DISABLE_OVERLAY_VALIDATION = "disable_overlay_validation"
    const val DISABLE_DOMAIN_VERIFICATION = "disable_domain_verification"
    const val BYPASS_BLOCK = "bypass_block"

    private val allConfig = arrayOf(
        BYPASS_DOWNGRADE,
        BYPASS_VERIFICATION,
        DISABLE_JAR_VERIFIER,
        DISABLE_MESSAGE_DIGEST,
        BYPASS_MIN_SIGNATURE_VERSION,
        BYPASS_V1_SIGNATURE_ERRORS,
        BYPASS_RESOURCE_ARSC_RESTRICTIONS,
        BYPASS_DIGEST,
        BYPASS_EXACT_SIGNATURE_MATCH,
        USE_PREVIOUS_SIGNATURES,
        ALLOW_HIDDEN_APIS_FOR_SYSTEM_APPS,
        BYPASS_SHARED_USER,
        DISABLE_VERIFICATION_AGENT,
        BYPASS_DEVELOPER_VERIFICATION,
        PMS_DEBUG_COMMAND,
        DISABLE_OVERLAY_VALIDATION,
        DISABLE_DOMAIN_VERIFICATION,
        BYPASS_BLOCK
    )

    fun printAllConfig() {
        allConfig.forEach {
            XposedHelper.log("${it}: ${prefs.getBoolean(it, false)}")
        }
    }

    fun isBypassDowngradeEnabled(): Boolean {
        return prefs.getBoolean(BYPASS_DOWNGRADE, false)
    }

    fun isBypassVerificationEnabled(): Boolean {
        return prefs.getBoolean(BYPASS_VERIFICATION, false)
    }

    fun isJarVerificationDisabled(): Boolean {
        return isBypassVerificationEnabled() || prefs.getBoolean(DISABLE_JAR_VERIFIER, false)
    }

    fun isMessageDigestDisabled(): Boolean {
        return isBypassVerificationEnabled() || prefs.getBoolean(DISABLE_MESSAGE_DIGEST, false)
    }

    fun isMinSignatureVersionBypassed(): Boolean {
        return isBypassVerificationEnabled() || prefs.getBoolean(BYPASS_MIN_SIGNATURE_VERSION, false)
    }

    fun isV1SignatureErrorBypassed(): Boolean {
        return isBypassVerificationEnabled() || prefs.getBoolean(BYPASS_V1_SIGNATURE_ERRORS, false)
    }

    fun isBypassResourceArscRestrictionsEnabled(): Boolean {
        return prefs.getBoolean(BYPASS_RESOURCE_ARSC_RESTRICTIONS, false)
    }

    fun isBypassDigestEnabled(): Boolean {
        return prefs.getBoolean(BYPASS_DIGEST, false)
    }

    fun isBypassExactSignatureMatch(): Boolean {
        return prefs.getBoolean(BYPASS_EXACT_SIGNATURE_MATCH, false)
    }

    fun isUsePreviousSignaturesEnabled(): Boolean {
        return prefs.getBoolean(USE_PREVIOUS_SIGNATURES, false)
    }

    fun isAllowHiddenApisForSystemAppsEnabled(): Boolean {
        return prefs.getBoolean(ALLOW_HIDDEN_APIS_FOR_SYSTEM_APPS, false)
    }

    fun isBypassSharedUserEnabled(): Boolean {
        return prefs.getBoolean(BYPASS_SHARED_USER, false)
    }

    fun isDisableVerificationAgentEnabled(): Boolean {
        return prefs.getBoolean(DISABLE_VERIFICATION_AGENT, false)
    }

    fun isBypassDeveloperVerificationEnabled(): Boolean {
        return prefs.getBoolean(BYPASS_DEVELOPER_VERIFICATION, false)
    }

    fun isPmsDebugCommandEnabled(): Boolean {
        return prefs.getBoolean(PMS_DEBUG_COMMAND, false)
    }

    fun isDisableOverlayValidationEnabled(): Boolean {
        return prefs.getBoolean(DISABLE_OVERLAY_VALIDATION, false)
    }

    fun isDomainVerificationDisabled(): Boolean {
        return prefs.getBoolean(DISABLE_DOMAIN_VERIFICATION, false)
    }

    fun isBypassBlockEnabled(): Boolean {
        return prefs.getBoolean(BYPASS_BLOCK, false)
    }

    fun getConfig(key: String): Boolean {
        return rwPrefs?.getBoolean(key, false) ?: false
    }

    fun setConfig(key: String, value: Boolean) {
        rwPrefs?.edit()?.putBoolean(key, value)?.apply()
    }
}
