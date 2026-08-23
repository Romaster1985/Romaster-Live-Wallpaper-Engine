package com.romaster.livewallengine.model

import kotlinx.serialization.Serializable

@Serializable
data class WallpaperProject(

    var version: Int = 1,

    var wallpaperVideo: String? = null,

    var overlayVideo: String? = null,

    var layers: MutableList<VideoLayer> =
        mutableListOf(),

    var clock: ClockSettings =
        ClockSettings(),

    var overlay: OverlaySettings =
        OverlaySettings(),
    
    var overlayLoopEnabled: Boolean = false,

    var cueLockedMs: Int = 500,

    var cueUnlockedMs: Int = 1000,
    
    var cueLockedMode: CueMode = CueMode.LOOP,

    var cueUnlockedMode: CueMode = CueMode.LOOP,

    /** Ping-pong en bloqueo: 0 → cue → reversa → 0 → … */
    var cueLockedPingPong: Boolean = false,

    /** Ping-pong desbloqueado: cue → final → reversa → cue → … */
    var cueUnlockedPingPong: Boolean = false,

    /** Nombre de archivo del clip invertido (locked), dentro de pingpong/ */
    var cueLockedReverseFile: String? = null,

    /** Nombre de archivo del clip invertido (unlocked), dentro de pingpong/ */
    var cueUnlockedReverseFile: String? = null,

    var previewLocked: Boolean = false,
    
    var overlayDurationMs: Long = 0L,
    
    var videoFadeDurationMs: Long = 2000L,

    var overlayFadeDurationMs: Long = 3000L,
    
    var clockFadeDurationMs: Long = 1000L
)