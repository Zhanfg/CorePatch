# CorePatch Android 17 Functional Test Suite

This artifact contains isolated APK fixtures for testing Core Patch on Android 17.

## Before running
Enable these Core Patch switches:
- Bypass downgrade
- Bypass verification
- Bypass digest
- Bypass shared user verification
- Bypass Android developer verification

Disable competing PackageManager/signature hooks such as LuckyTool-CorePatch.

## Run
Keep all files in this folder structure and execute:

`CorePatch_FunctionalTest_v1.sh`

No arguments are required.

The script only installs the dedicated packages:
- dev.axymorrsen.corepatch.test
- dev.axymorrsen.corepatch.shared.a
- dev.axymorrsen.corepatch.shared.b

It removes them automatically at the end.
