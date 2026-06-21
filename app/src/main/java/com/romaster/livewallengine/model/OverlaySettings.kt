package com.romaster.livewallengine.model

import kotlinx.serialization.Serializable

@Serializable
data class OverlaySettings(

    var enabled: Boolean = false,

    var videoPath: String? = null,
    
    var videoDurationMs: Long = 0L,

    var x: Float = 0f,

    var y: Float = 0f,

    var scale: Float = 1f,
    
    var aspectMode: OverlayAspectMode =
        OverlayAspectMode.ORIGINAL,
    
    var rotation: Float = 0f,

    var opacity: Float = 1f,

    var chromaEnabled: Boolean = false,

    var chromaColor: Int = 0xFF00FF00.toInt(),

    var threshold: Float = 50f,

    var softness: Float = 20f,
    
    var soundPath: String? = null,

    var soundDisplayName: String? = null,
    
    var soundDuration: Long = 0L,
    
    var soundVolume: Float = 1f,
    
    var soundEnabled: Boolean = true

)