# Fernplay for Android

A simple music player for Android: open it, pick a track or a playlist, play.

Fernplay plays the music already on your phone. You pick your music folders once and new songs appear by themselves. Playlists live inside the app, all on the home screen, never as files in folders. Repeat is always one tap away, on the player and in the notification, and the lock screen, headset, Bluetooth and car buttons all control playback.

The EQ is the one from Fernplay for Windows: ten bands from 60 Hz to 16 kHz, a preamp, the same built-in presets and your own, and a curve that shows what you hear. It sounds the same on every phone because Fernplay does the filtering itself instead of relying on the phone maker's equalizer.

It can also be the remote for Fernplay on your PC: pair once with a 6-digit code, then play, pause, skip, seek, set the volume, Shuffle, Repeat and Crossfade, and pick a track from the PC's playlist. Home network only, encrypted, and only with the PC you paired.

It is the Android sibling of [Fernplay](https://github.com/codekunde/Fernplay) for Windows and shares its look: a dark frame, a green LCD-style display and a fern.

**Status:** 0.2.0 adds the equalizer and a sleep timer. Download `fernplay-0.2.0.apk` from the Releases page and open it on your phone (Android asks once to allow installs from that app); it installs over 0.1.0 and keeps your playlists.

## Requirements

Android 8.0 or later.

## Building

JDK 17 and the Android SDK (platform 35).

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/`.

`./gradlew testDebugUnitTest` runs on the JVM, no device needed: Room and ExoPlayer run under Robolectric.

### Release build

`./gradlew assembleRelease` signs the APK when these Gradle properties are set, for example in `~/.gradle/gradle.properties` (never in the repo):

```
fernplay.storeFile=/path/to/release.jks
fernplay.storePassword=...
fernplay.keyAlias=...
fernplay.keyPassword=...
```

Without them the APK is written unsigned to `app/build/outputs/apk/release/`.

## Licence

MIT, see [LICENSE](LICENSE). The LCD font, Share Tech Mono, is under the SIL Open Font License 1.1, see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
