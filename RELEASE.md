# TowhidLudo Release Guide

## Version

- Application ID: `com.towhid.ludo`
- Release version: `1.0.0` (`versionCode 1`)
- Minimum Android: API 26
- Target / compile SDK: API 36
- Java / Kotlin JVM target: 17

For every Play Store update, increase `versionCode`. Change `versionName` when the public version changes.

## Create the release key once

Keep this key and its passwords safe. Losing it can prevent future updates to the same published app.

```bash
keytool -genkeypair -v \
  -keystore towhidludo-release.jks \
  -alias towhidludo \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Then create the local signing config:

```bash
cp keystore.properties.example keystore.properties
```

Edit `keystore.properties` with the real passwords. The signing properties and keystore files are intentionally ignored by Git.

## Build

Debug APK:

```bash
./gradlew clean assembleDebug
```

Signed release APK (when `keystore.properties` is configured):

```bash
./gradlew clean assembleRelease
```

Release App Bundle for Google Play:

```bash
./gradlew clean bundleRelease
```

Expected outputs:

- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab`

If release signing is not configured, Gradle can still configure the project, but the release artifact will not be signed for distribution.

## Final device checklist

- Start both `1 vs 1` and `2 vs 2` matches.
- Verify all four powers: Double, Choose Roll, Protect, and +1 extra roll.
- Verify Human and Computer can collect and intelligently use powers.
- Verify capture, safe cells, exact home finish, natural-six extra turn, and +1 extra turn.
- Rotate the device before a roll, after a roll, after using Double, and during a Computer turn; the match must restore correctly.
- Verify Back and Restart confirmation dialogs.
- Finish a complete 1v1 match and a complete 2v2 match and verify the winner dialog.
- Leave the app in background and return to it; confirm the current screen remains usable.
- Test light mode and dark mode.
- Confirm the TowhidLudo launcher icon, label, and version are correct.
- Install the release APK on a real device before distributing it.
