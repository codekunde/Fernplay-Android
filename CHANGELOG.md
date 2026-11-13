# Changelog

## Unreleased

- Radio (first part): Home → Radio lists internet radio stations. Add them from an .m3u or .m3u8 file, the kind radio websites offer for download: with + in Radio, or with "Open with Fernplay" on the file in a file manager. Every http and https stream in the file becomes a station, named as the file names it (or after the file, or the server, when it gives no name); stations already in the list are skipped. Rename a station or delete it with Undo from its ⋮ menu.
- Radio (second part): tap a station to play it. Now Playing shows the song the station is playing, split into title and artist, with the station's name under it; LIVE takes the place of the time left, and there is no seek bar or Repeat. Previous and Next step through your stations. The notification, lock screen, Bluetooth and car displays show the song too, with the station as the album. A station added without a name takes the name it sends.
- When the connection drops, the display says RECONNECTING… and Fernplay connects again by itself, up to 3 times a minute; after that it stops and says UNREACHABLE, and Play tries again. A station is never skipped. A station playing over mobile data moves to Wi-Fi when the phone joins Wi-Fi again (a gap of a second or two), and it keeps playing with the screen off.
- Plain http stations work (many public stations offer only http). Fernplay now asks Android to keep Wi-Fi awake while a station plays.
- Stations live in their own list and never inside a playlist. The update keeps your playlists as they are.

## 0.3.0 (2026-11-08)

Android 8.0 or later. Installs over 0.2.0 and keeps your playlists, folders and equalizer.

- Remote for Fernplay on your PC: Home → Fernplay on your PC. On the PC, switch PHONE REMOTE on in Settings and choose PAIR A PHONE…; the phone finds the PC on the Wi-Fi (or takes its address) and asks once for the 6-digit code. Then the phone shows what the PC plays and controls it: play, pause, stop, previous, next, seek, volume, Shuffle, Repeat and Crossfade, and the PC's playlist (tap a row to play it). The phone's own music is not touched. It works on your home network only, encrypted, and only with the PC you paired; the connection ends when Fernplay goes to the background. Needs the Fernplay for Windows release that adds the phone remote.
- Fernplay now has network access, used only by the remote to talk to your PC.

## 0.2.0 (2026-11-08)

Android 8.0 or later. Installs over 0.1.0 and keeps your playlists and folders.

- Equalizer: the one from Fernplay for Windows. Ten bands from 60 Hz to 16 kHz, a preamp, and the same built-in presets (Flat, Rock, Pop, Classical, Dance, Bass boost, Treble boost, Vocal). Save the curve as your own preset and delete it again from the same menu. The green display shows the curve you hear. Fernplay does the filtering itself, so it sounds the same on every phone instead of depending on the phone maker's equalizer. Open it with EQ on Now Playing; a double-tap on a slider puts it back to 0 dB.
- Sleep timer: Now Playing ⋮ → Sleep timer. 15, 30, 45 minutes, 1 hour or the end of this song. While it runs you can add 15 minutes, switch to the end of the song, or turn it off, and the display shows a moon and the time left. At the end the music fades out over 30 seconds, then pauses; the volume is back to normal for next time. The end of the song also stops with Repeat track on.
- Now Playing's ⋮ is a menu: Gym view and Sleep timer.
- Fixed: on short screens the A–Z strip in All songs ran under the mini player and its last letters could not be reached. The letters now share the height there is, and on very short screens every other letter is shown; dragging still reaches every letter.

## 0.1.0 (2026-11-07)

Android 8.0 or later. The first release.

- Pick your music folders once, and leave out any subfolder; new songs appear by themselves.
- All songs with search, sort (title, artist, album, newest) and an A–Z strip; a song menu with Play next and Add to playlist; long-press to select several.
- Playlists on the home screen: play, shuffle, add songs, drag to reorder, remove with Undo, rename and delete. Songs that moved are found again.
- Now Playing with the green LCD-style display, a large gym view, and the queue (tap to play, remove with Undo).
- Repeat is one tap away on the player and in the notification (off, list, track); the lock screen, headset, Bluetooth and car buttons control playback.
- Picks up where you left off: queue, song, position and Repeat.
- Settings: music folders, rescan, and System, Dark or Light theme.
