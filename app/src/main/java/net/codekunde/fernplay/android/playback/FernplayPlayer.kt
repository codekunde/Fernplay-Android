package net.codekunde.fernplay.android.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.exoplayer.ExoPlayer

/**
 * The player the media session, notification and app talk to.
 *
 * Today it passes everything to one ExoPlayer. It exists so crossfade (v2) can
 * run two ExoPlayers underneath without the session noticing. A test on
 * 2026-11-06 showed two players overlap only when one of them holds audio
 * focus; with crossfade this class will request focus itself and the players
 * will not.
 */
class FernplayPlayer(val engine: ExoPlayer) : ForwardingPlayer(engine)
