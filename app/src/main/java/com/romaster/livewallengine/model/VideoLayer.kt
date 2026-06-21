package com.romaster.livewallengine.model

data class VideoLayer(

    var fileName: String = "",

    var x: Float = 0f,

    var y: Float = 0f,

    var scale: Float = 1f,

    var chromaEnabled: Boolean = false,

    var chromaColor: String = "#00FF00",

    var threshold: Float = 0.15f
)