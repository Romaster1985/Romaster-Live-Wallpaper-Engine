package com.romaster.livewallengine.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoLayer(

    var fileName: String = "",

    var x: Float = 0f,

    var y: Float = 0f,

    var scale: Float = 100f,
    
    var fitMode: VideoFitMode =
        VideoFitMode.STRETCH,
    
    var aspectMode: VideoAspectMode =
        VideoAspectMode.ORIGINAL,

    var chromaEnabled: Boolean = false,

    var chromaColor: String = "#00FF00",

    var threshold: Float = 0.15f,
    
    var soundPath: String? = null,

    var soundDisplayName: String? = null,
    
    var soundDuration: Long = 0L,
    
    var soundVolume: Float = 1f,
    
    var soundEnabled: Boolean = true
)