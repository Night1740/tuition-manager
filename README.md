# Tuition Manager

Local-first Android app for one small tuition or coaching class. The teacher’s phone is the system of record. Phase 1 covers setup, students, and batches. Attendance and fees are stored in the schema but have no screens yet.

## Setup

- JDK 21
- Android SDK with compile SDK 37.2 (minor API level 2), build-tools, and platform-tools
- Point the build at the SDK with `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew assembleRelease
```

`assembleRelease` runs R8 and shrinks resources. It is unsigned. A connected device or emulator can run the instrumented flow:

```bash
./gradlew connectedDebugAndroidTest
```

That command runs the first-launch flow. A second check, after `adb shell am force-stop com.tuitionmanager`, is:

```bash
adb shell am instrument -w \
  -e class com.tuitionmanager.RelaunchPersistenceTest \
  com.tuitionmanager.test/androidx.test.runner.AndroidJUnitRunner
```

Clear app data before a fresh first-launch run (`adb shell pm clear com.tuitionmanager`). Do not clear it between the flow and the relaunch check.

## Modules and layers

| Module | Role |
| --- | --- |
| `:app` | Single activity, Hilt application, Material 3 theme, debug StrictMode |
| `:core` | Ids, money, time, validation, Room, repositories |
| `:feature` | Navigation and screens. Calls domain validation and repositories. Does not import `com.tuitionmanager.core.data` |

Package and `applicationId`: `com.tuitionmanager`. minSdk 26, targetSdk 37.

Screens talk to repositories. Repositories talk to Room. A database failure becomes `DataResult.Failure` with an operation token. It is never dropped, and the log line does not include the exception message or any field value.

## Room is the source of truth

`tuition_manager.db` is schema version 1. There is no destructive migration. The device is offline by design: no Firebase, no live sync, no accounts, no staff logins, and no second device.

SQLCipher is not used yet. The database is private app storage. Encryption at rest can be added later without changing the product model.

Android Auto Backup is off (`android:allowBackup="false"`). Cloud-backup and device-transfer rules exclude the database, files, and preferences. Uninstall clears the class. An explicit encrypted backup can be added later; until then, copying student contacts or the payment ledger off the phone is not allowed.

## Conventions

- Ids created on the device are UUIDv7. Attendance and fee-obligation ids are UUIDv5 (RFC 9562). Their namespaces and name formats are frozen in `DeterministicIds` and must not change once rows exist.
- Money is `Paise` (`Long`). There is no floating-point currency.
- Instants are UTC epoch millis. A date the teacher picks (admission, archive, assignment start and end) is a `LocalDate` from an injectable clock in the device zone. Repositories do not derive that date from an `Instant`.
- `endedOn` on a batch assignment is exclusive.
- Student and batch lists are either active or archived, never both.
- Phone numbers are stored as 10 national digits. The shared rule accepts `+91`, a leading `0`, spaces, dashes, and parentheses, and it accepts landlines that fit that 10-digit form. Forms call that rule; they do not parse numbers themselves.
- Batch capacity is required, from 1 to 2000. Going over capacity is a typed repository result. The screen only asks the teacher to confirm.

Notes for the attendance and fee work that is still ahead are in `docs/decisions/later-attendance-and-fees.md`.
