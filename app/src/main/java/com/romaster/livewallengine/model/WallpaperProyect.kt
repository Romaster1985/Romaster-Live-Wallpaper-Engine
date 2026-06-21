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
    
    var playback: PlaybackSettings =
        PlaybackSettings()
)