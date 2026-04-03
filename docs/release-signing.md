# Release Signing

`NandaSNES` now supports Android release signing without storing secrets in Git.

## Option 1: local `keystore.properties`

Create `android/keystore.properties` from the example file:

```powershell
Copy-Item android\keystore.properties.example android\keystore.properties
```

Fill it with your local values:

```properties
storeFile=C:/secure/path/nandanes-release.jks
storePassword=your-store-password
keyAlias=nandanes
keyPassword=your-key-password
```

## Option 2: environment variables

You can also configure signing through environment variables:

- `NANDANES_STORE_FILE`
- `NANDANES_STORE_PASSWORD`
- `NANDANES_KEY_ALIAS`
- `NANDANES_KEY_PASSWORD`

## Verify signing configuration

```powershell
cd android
.\gradlew.bat printReleaseSigningStatus
```

## Build a signed release APK

```powershell
cd android
.\gradlew.bat :app:assembleRelease
```

If signing is configured, Gradle will produce a signed release APK in:

`android/app/build/outputs/apk/release/`

If signing is not configured, Gradle will still build an unsigned release APK.

## Generate a keystore

Example using `keytool`:

```powershell
keytool -genkeypair -v `
  -keystore C:\secure\path\nandanes-release.jks `
  -alias nandanes `
  -keyalg RSA `
  -keysize 4096 `
  -validity 3650
```

Keep the keystore and passwords backed up securely. Losing them means you cannot ship updates for the same Android application ID.
