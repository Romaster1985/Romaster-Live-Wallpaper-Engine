package com.romaster.livewallengine.project

import com.romaster.livewallengine.model.ClockSettings
import com.romaster.livewallengine.model.DateFormat
import com.romaster.livewallengine.model.OverlaySettings
import com.romaster.livewallengine.model.TextAlignment
import com.romaster.livewallengine.model.TimeFormat
import com.romaster.livewallengine.model.VideoLayer
import com.romaster.livewallengine.model.WallpaperProject
import com.romaster.livewallengine.model.VideoFitMode
import com.romaster.livewallengine.model.VideoAspectMode
import com.romaster.livewallengine.model.PlaybackSettings

object DefaultProject {

    private val factoryProject = WallpaperProject(

        wallpaperVideo = null,

        overlayVideo = null,

        layers = mutableListOf(

            VideoLayer(
        
                x = 0f,
        
                y = 0f,
        
                scale = 100f,
        
                fitMode = VideoFitMode.STRETCH,
        
                aspectMode = VideoAspectMode.ORIGINAL,
        
                soundPath = null,
                soundDisplayName = null,
                soundDuration = 0L,
                soundVolume = 1f,
                soundEnabled = true
        
            )
        
        ),

        overlay = OverlaySettings(

            x = 0f,
            y = 0f,

            scale = 1f,
            
            videoDurationMs = 0L,

            rotation = 0f,

            opacity = 1f,

            chromaEnabled = false,
            chromaColor = 0xFF00FF00.toInt(),
            threshold = 50f,
            softness = 20f,

            soundPath = null,
            soundDisplayName = null,
            soundDuration = 0L,
            soundVolume = 1f,
            soundEnabled = true

        ),

        clock = ClockSettings(

            enabled = true,
            showDate = true,

            timeFormat = TimeFormat.HH_MM,
            dateFormat = DateFormat.DOW_DD_MON,

            clockSize = 64f,
            dateSize = 32f,

            x = 0.5f,
            y = 0.5f,

            alignment = TextAlignment.CENTER,

            clockColor = "#FFFFFF",
            dateColor = "#FFFFFF",

            clockColorPreset = "Blanco",
            dateColorPreset = "Blanco",

            fontFile = null,
            dateSpacing = 20f,
            clockFont = null,
            dateFont = null

        ),
        
        playback = PlaybackSettings(

            enabled = false,
        
            lockedCueMs = 0L,
        
            unlockedCueMs = 0L,
        
            previewLocked = false,
        
            cuesInitialized = false
        
        )

    )

    fun create(): WallpaperProject {

        return factoryProject.copy(

            layers = factoryProject.layers
                .map { it.copy() }
                .toMutableList(),

            overlay = factoryProject.overlay.copy(),

            clock = factoryProject.clock.copy(),
            
            playback = factoryProject.playback.copy()

        )

    }

}