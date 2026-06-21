package com.romaster.livewallengine.model

data class ClockSettings(

    var enabled: Boolean = true,

    var fontFile: String? = null,

    var size: Float = 64f,

    var color: String = "#FFFFFF",

    var x: Float = 100f,

    var y: Float = 100f
)