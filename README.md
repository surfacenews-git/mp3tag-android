# Mp3tag for Android

Audio metadata and batch tag editor for Android built with Kotlin, Jetpack Compose, and Material Design 3.

## Supported Audio Formats
- **MP3**: ID3v2.2, ID3v2.3 (UTF-16), ID3v2.4 (UTF-8), ID3v1.1, APIC cover art, USLT lyrics, custom `TXXX` frames
- **FLAC**: Vorbis Comments + `METADATA_BLOCK_PICTURE`
- **M4A / MP4 / AAC**: iTunes `ilst` atoms (`covr`, `trkn`, `disk`, custom `----` atoms) with automatic `stco`/`co64` offset updates
- **OGG Vorbis**: Vorbis Comment pages with OGG CRC32 recalculation
- **WAV**: `RIFF LIST/INFO` and embedded `id3 ` chunks

## Core Features
- Zero-permission folder & file access via Android Storage Access Framework (SAF) and Photo Picker
- Single-track & batch tag editing with `<keep>` preservation for mixed fields
- Format String Converters (`Tag -> Filename`, `Filename -> Tag`, `Tag -> Tag`, Auto-Numbering Wizard, M3U8/CSV export & text import)
- Quick Actions & saved Action Groups (Case Conversion, Plain & RegEx Replace, Format Value, Trim, Remove Fields)
- Online Tag Sources via iTunes Search and MusicBrainz + Cover Art Archive
- Built-in audio preview player

## GitHub Actions Release APK Build
The included workflow (`.github/workflows/android-release.yml`) runs unit tests and builds a signed **Release APK** (`app-release.apk`) uploaded as the `mp3tag-release-apk` workflow artifact.

### Optional Production Keystore Secrets
If you want the Release APK signed with your own upload keystore instead of the workflow's ephemeral CI keystore, configure these repository secrets in **Settings → Secrets and variables → Actions**:
- `RELEASE_KEYSTORE_BASE64`: Base64-encoded `.jks` keystore containing key alias `upload`
- `STORE_PASSWORD`: Keystore password
- `KEY_PASSWORD`: Key password
