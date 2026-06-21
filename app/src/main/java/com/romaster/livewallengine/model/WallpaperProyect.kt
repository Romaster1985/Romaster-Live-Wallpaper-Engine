package com.romaster.livewallengine.model

data class WallpaperProject(

    val version: Int = 1,

    val wallpaperVideo: String? = null,

    val layers: MutableList<VideoLayer> =
        mutableListOf(),

    val clock: ClockSettings =
        ClockSettings()
)