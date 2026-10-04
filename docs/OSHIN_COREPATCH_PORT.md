# OShin CorePatch capability audit / Android 17 port

Source baseline:
- OShin public PackageManagerServices implementation (pre-private mirror)
- OShin Xposed Module Repo release notes through 2026
- Android 17 / API 37 AOSP service layout

## Capability matrix

| OShin capability | CorePatch N status | Notes |
| --- | --- | --- |
| Allow downgrade | Existing | PackageManagerServiceUtils.checkDowngrade |
| Allow different-signature update | Existing | SigningDetails + KeySetManagerService + Android 17 InstallPackageHelper path |
| Disable JAR verifier | Ported granular policy | Main bypass still implies it for backward compatibility |
| Disable MessageDigest verification | Ported granular policy | Main bypass still implies it |
| Bypass resources.arsc restriction | Existing | AssetManager.containsAllocatedTable |
| Bypass minimum signature scheme | Ported granular policy | ApkSignatureVerifier + ScanPackageUtils |
| Recover v1 -103 signature error | Ported granular policy | Keeps modern signer recovery before synthetic fallback |
| Allow mismatched split signatures | Existing | SigningDetails.signaturesMatchExactly |
| Disable install verification agent | Existing | VerificationParams / VerifyingSession |
| Allow system app hidden APIs | Existing | ApplicationInfo |
| Allow non-system shared UID | Existing + Android 17 strengthened | ReconcilePackageUtils + canJoinSharedUserId |
| PMS debug command | Ported | Hot-Reload-safe pm pms implementation |
| Disable Overlay Validation | Ported | InstallPackageHelper.assertOverlayIsValid; does not bypass OMS/idmap policy |
| Disable App Domain Verification | Ported | Suppresses DomainVerificationService verification broadcasts; preserves state/user choices |
| Installer/uninstaller takeover | Deferred | Public source only contains an unfinished redirect stub; later implementation is private |

## CorePatch N capabilities beyond the public OShin implementation

- libxposed API 102 Hot Reload lifecycle
- Android 17 Developer Verification gate support
- Android 17 duplicate-permission / previous-signature compatibility path
- Android 17 sharedUser admission validation with functional UID test
- scheme-aware v3.2 / v4.1 diagnostics and fixture matrix
- v4 sidecar fallback boundary separated from IncFS kernel enforcement
- Android 17 / ColorOS 17 functional test suite

## Design rule

OShin-style granular switches do not break the old CorePatch master switch. The effective policy is:

    master verification bypass OR granular switch

This preserves upgrades from existing configurations while allowing narrower behavior for new installs.
