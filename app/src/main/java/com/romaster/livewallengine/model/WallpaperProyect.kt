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

    var previewLocked: Boolean = false,
    
    var overlayDurationMs: Long = 0L,
    
    var videoFadeDurationMs: Long = 2000L,

    var overlayFadeDurationMs: Long = 3000L,
    
    var clockFadeDurationMs: Long = 1000L
)