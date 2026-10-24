# Releasing & updates

## Versions

Every push to `main` triggers `.github/workflows/build.yml`. It:

1. runs the core tests,
2. builds a release APK with `versionCode` = the workflow run number, so each build is newer than the last,
3. publishes the APK as GitHub Release `v0.1.<run number>`, with the commit message as release notes.

## Signing key (one-time, critical)

Android only installs an update over an existing app if both are signed with **the same key**.
Lose the key and the only way forward is to uninstall, which deletes the data on the phone (restore it from a backup file).

The key is generated once and stored as four repository secrets
(Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `MORPH_KEYSTORE_BASE64` | base64 of the `.jks` keystore file |
| `MORPH_KEYSTORE_PASSWORD` | keystore password |
| `MORPH_KEY_ALIAS` | `morph` |
| `MORPH_KEY_PASSWORD` | key password |

Keep an offline copy of the `.jks` file and the passwords in a password manager. The build fails loudly if the secrets are missing,
so an APK signed with the wrong key is never published.

## Data safety across updates

- `adb install -r` (what `scripts/install.sh` does) replaces the app and keeps its data.
- Before the first launch of each new version, Morph copies its database file to private storage (the last 5 copies are kept).
- Database schema changes go through `MorphDb.onUpgrade`, one step per version; user data is never dropped.
- **Me → Back up to a file** writes a full JSON backup you can store on Google Drive. **Restore** reads it back, also on a new phone.
- Android's own Google backup includes Morph's database (see `res/xml/data_extraction_rules.xml`).

## Sharing with friends

- Simplest: send them the `.apk` from a Release.
- If the repository stays private, only people with access can download Releases. To let friends update themselves
  (for example with [Obtainium](https://obtainium.imranr.dev/)), publish the APKs somewhere public. The code contains no personal data:
  test fixtures are anonymized and every install starts empty.
