# Tuition Manager

Local-first Android app for a small tuition or coaching class. Room on the phone is the only source of truth. There is no cloud sync in this version.

Phase 1B adds onboarding and the dashboard shell. Student and batch screens are still placeholders.

## Modules

- `:app` — application, single activity, Material 3 theme
- `:core` — ids, money, Room, repositories
- `:feature` — navigation, onboarding, dashboard

Package name: `com.tuitionmanager`.

## Build

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lint
./gradlew assembleRelease
```

The Android SDK is read from `local.properties` (`sdk.dir`) or `ANDROID_HOME`.

## Backup

`android:allowBackup` is false, and backup / device-transfer rules exclude the database and files. Student contacts and the payment ledger are not copied off the device by Android Auto Backup. An explicit encrypted backup can be added later.
