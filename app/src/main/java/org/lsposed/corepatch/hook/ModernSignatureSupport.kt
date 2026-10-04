package org.lsposed.corepatch.hook

import android.os.Build
import org.lsposed.corepatch.BuildConfig
import org.lsposed.corepatch.Config
import org.lsposed.corepatch.XposedHelper.findClassIfExists
import org.lsposed.corepatch.XposedHelper.hookAfter
import org.lsposed.corepatch.XposedHelper.hookBefore
import org.lsposed.corepatch.XposedHelper.log
import java.lang.reflect.Array as ReflectArray

/**
 * Android 17's platform verifier already knows how to preserve modern signing semantics when
 * certificate-only verification is requested:
 *
 * - v3.2 keeps the v3 scheme version and sets minorVersion=2 (hybrid).
 * - v4/v4.1 still resolves the backing v3 block and the matching V4 SigningInfoBlock.
 *
 * CorePatch therefore does not duplicate AOSP's parser. We only force integrity verification off
 * at the supported verifier boundaries and keep scheme/lineage parsing in the platform.
 */
object ModernSignatureSupport {
    private const val APK_SIGNATURE_SCHEME_V3_BLOCK_ID = -262969152 // 0xf05368c0
    private const val APK_SIGNATURE_SCHEME_V31_BLOCK_ID = 0x1b93ad61
    private const val APK_SIGNATURE_SCHEME_V32_BLOCK_ID = 0x70e1c89f

    private const val SIGNING_BLOCK_V3 = 3
    private const val SIGNING_BLOCK_V4 = 4
    private const val MINOR_VERSION_32_HYBRID = 2

    fun install(apkSignatureVerifierClazz: Class<*>) {
        installLowLevelIntegrityBypass(
            "android.util.apk.ApkSignatureSchemeV2Verifier"
        )
        installLowLevelIntegrityBypass(
            "android.util.apk.ApkSignatureSchemeV3Verifier"
        )

        if (Build.VERSION.SDK_INT >= 37) {
            logAndroid17Surface()
            installModernResultDiagnostics(apkSignatureVerifierClazz)
        }
    }

    /**
     * Reinforce the upper ApkSignatureVerifier verifyFull=false hook at the actual v2/v3
     * integrity-verifier boundary. This is intentionally limited to the boolean integrity
     * parameter; certificate parsing, proof-of-rotation, v3.2 hybrid signer validation and
     * content-digest extraction remain handled by AOSP.
     */
    private fun installLowLevelIntegrityBypass(className: String) {
        val verifier = findClassIfExists(className) ?: return

        HookResolver.findMethods(verifier, "verify") { method ->
            method.parameterCount == 2 &&
                method.parameterTypes[1] == Boolean::class.javaPrimitiveType &&
                (
                    method.parameterTypes[0] == String::class.java ||
                        method.parameterTypes[0].name == "java.io.RandomAccessFile"
                )
        }.forEach { method ->
            hookBefore(method) { callback ->
                if (!Config.isBypassVerificationEnabled()) return@hookBefore
                callback.args[1] = false
            }
        }
    }

    private fun installModernResultDiagnostics(apkSignatureVerifierClazz: Class<*>) {
        listOf("verifyV3Signature", "verifyV4Signature").forEach { methodName ->
            HookResolver.findMethods(apkSignatureVerifierClazz, methodName).forEach { method ->
                hookAfter(method) { callback ->
                    if (!BuildConfig.DEBUG || !Config.isBypassVerificationEnabled()) {
                        return@hookAfter
                    }
                    if (callback.throwable != null) return@hookAfter

                    val signing = readSigningState(callback.result) ?: return@hookAfter
                    val apkPath = callback.args
                        .firstOrNull { it is String && it.endsWith(".apk", ignoreCase = true) }
                        as? String

                    when {
                        signing.scheme == SIGNING_BLOCK_V3 &&
                            signing.minor == MINOR_VERSION_32_HYBRID -> {
                            val blockId = apkPath?.let(::readV3BlockId)
                            log(
                                "[ModernSignature] v3.2 hybrid accepted" +
                                    blockId?.let { " block=${formatBlockId(it)}" }.orEmpty() +
                                    " path=${apkPath ?: "<unknown>"}"
                            )
                        }

                        signing.scheme == SIGNING_BLOCK_V3 -> {
                            val blockId = apkPath?.let(::readV3BlockId)
                            log(
                                "[ModernSignature] ${labelV3Block(blockId)} accepted" +
                                    blockId?.let { " block=${formatBlockId(it)}" }.orEmpty() +
                                    " path=${apkPath ?: "<unknown>"}"
                            )
                        }

                        signing.scheme == SIGNING_BLOCK_V4 -> {
                            val blocks = apkPath?.let(::readV4SigningInfoBlocks).orEmpty()
                            val label = if (blocks.isNotEmpty()) "v4.1-layout" else "v4"
                            log(
                                "[ModernSignature] $label accepted" +
                                    if (blocks.isNotEmpty()) {
                                        " signingInfoBlocks=${blocks.joinToString(",") { formatBlockId(it) }}"
                                    } else {
                                        ""
                                    } +
                                    " path=${apkPath ?: "<unknown>"}"
                            )
                        }
                    }
                }
            }
        }
    }

    private data class SigningState(
        val scheme: Int,
        val minor: Int,
    )

    private fun readSigningState(raw: Any?): SigningState? {
        var value = raw ?: return null

        // ParseResult<T>
        val isErrorMethod = value.javaClass.methods.firstOrNull {
            it.name == "isError" && it.parameterCount == 0
        }
        if (isErrorMethod != null) {
            val isError = runCatching { isErrorMethod.invoke(value) as Boolean }.getOrDefault(true)
            if (isError) return null
            val getResult = value.javaClass.methods.firstOrNull {
                it.name == "getResult" && it.parameterCount == 0
            } ?: return null
            value = runCatching { getResult.invoke(value) }.getOrNull() ?: return null
        }

        // ApkSignatureVerifier.SigningDetailsWithDigests
        val signingDetailsField = runCatching {
            value.javaClass.getDeclaredField("signingDetails").apply { isAccessible = true }
        }.getOrNull()
        if (signingDetailsField != null) {
            value = runCatching { signingDetailsField.get(value) }.getOrNull() ?: return null
        }

        val schemeMethod = value.javaClass.methods.firstOrNull {
            it.name == "getSignatureSchemeVersion" && it.parameterCount == 0
        } ?: return null
        val minorMethod = value.javaClass.methods.firstOrNull {
            it.name == "getSignatureSchemeMinorVersion" && it.parameterCount == 0
        }

        val scheme = runCatching { schemeMethod.invoke(value) as Int }.getOrNull() ?: return null
        val minor = runCatching { minorMethod?.invoke(value) as? Int }.getOrNull() ?: 0
        return SigningState(scheme, minor)
    }

    private fun readV3BlockId(apkPath: String): Int? {
        val verifier = findClassIfExists("android.util.apk.ApkSignatureSchemeV3Verifier")
            ?: return null
        val method = verifier.declaredMethods.firstOrNull {
            it.name == "unsafeGetCertsWithoutVerification" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java
        }?.apply { isAccessible = true } ?: return null

        val signer = runCatching { method.invoke(null, apkPath) }.getOrNull() ?: return null
        val field = runCatching {
            signer.javaClass.getDeclaredField("blockId").apply { isAccessible = true }
        }.getOrNull() ?: return null
        return runCatching { field.getInt(signer) }.getOrNull()
    }

    private fun readV4SigningInfoBlocks(apkPath: String): List<Int> {
        val verifier = findClassIfExists("android.util.apk.ApkSignatureSchemeV4Verifier")
            ?: return emptyList()
        val extract = verifier.declaredMethods.firstOrNull {
            it.name == "extractSignature" &&
                it.parameterCount == 1 &&
                it.parameterTypes[0] == String::class.java
        }?.apply { isAccessible = true } ?: return emptyList()

        val pair = runCatching { extract.invoke(null, apkPath) }.getOrNull()
            ?: return emptyList()
        val signingInfos = runCatching {
            pair.javaClass.getField("second").get(pair)
        }.getOrNull() ?: return emptyList()
        val blocks = runCatching {
            signingInfos.javaClass.getField("signingInfoBlocks").get(signingInfos)
        }.getOrNull() ?: return emptyList()

        val count = runCatching { ReflectArray.getLength(blocks) }.getOrDefault(0)
        return buildList {
            for (index in 0 until count) {
                val block = ReflectArray.get(blocks, index) ?: continue
                val blockId = runCatching {
                    block.javaClass.getField("blockId").getInt(block)
                }.getOrNull() ?: continue
                add(blockId)
            }
        }
    }

    private fun labelV3Block(blockId: Int?): String = when (blockId) {
        APK_SIGNATURE_SCHEME_V3_BLOCK_ID -> "v3.0"
        APK_SIGNATURE_SCHEME_V31_BLOCK_ID -> "v3.1"
        APK_SIGNATURE_SCHEME_V32_BLOCK_ID -> "v3.2-hybrid"
        null -> "v3"
        else -> "v3-extended"
    }

    private fun formatBlockId(value: Int): String =
        "0x" + value.toUInt().toString(16).padStart(8, '0')

    private fun logAndroid17Surface() {
        val v3 = findClassIfExists("android.util.apk.ApkSignatureSchemeV3Verifier")
        val v4 = findClassIfExists("android.util.apk.ApkSignatureSchemeV4Verifier")
        val signingDetails = findClassIfExists("android.content.pm.SigningDetails")
        val v4SigningInfos = findClassIfExists("android.os.incremental.V4Signature\$SigningInfos")

        val v32Block = runCatching {
            v3?.getDeclaredField("APK_SIGNATURE_SCHEME_V32_BLOCK_ID")
                ?.apply { isAccessible = true }
                ?.getInt(null)
        }.getOrNull()

        val hasMinorVersion = signingDetails?.methods?.any {
            it.name == "getSignatureSchemeMinorVersion" && it.parameterCount == 0
        } == true
        val hasV41Blocks = runCatching {
            v4SigningInfos?.getField("signingInfoBlocks")
        }.getOrNull() != null
        val hasV41Selector = v4?.declaredMethods?.any {
            it.name == "findSigningInfoForBlockId" && it.parameterCount == 2
        } == true

        log(
            "[ModernSignature] A17 surface: " +
                "v3.2=${v32Block == APK_SIGNATURE_SCHEME_V32_BLOCK_ID && hasMinorVersion}, " +
                "v4.1=${hasV41Blocks && hasV41Selector}, " +
                "v32Block=${v32Block?.let(::formatBlockId) ?: "missing"}"
        )
    }
}
