/*
 * Copyright 2026 Román Ignacio Romero (Romaster)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Nota: Este proyecto incluye ColorPickerView (skydoves) licenciado bajo Apache 2.0.
 */

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
import com.romaster.livewallengine.model.CueMode

object DefaultProject {

    private val factoryProject = WallpaperProject(

        wallpaperVideo = null,

        overlayVideo = null,
        
        overlayLoopEnabled = false,
        
        cueLockedMs = 500,
        
        cueUnlockedMs = 1000,
        
        previewLocked = false,

        overlayDurationMs = 0L,
        
        videoFadeDurationMs = 2000L,
        
        overlayFadeDurationMs = 3000L,
        
        clockFadeDurationMs = 1000L,
        
        cueLockedMode = CueMode.LOOP,
        
        cueUnlockedMode = CueMode.LOOP,

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

            enabledOnLockScreen = true,
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

        )

    )

    fun create(): WallpaperProject {

        return factoryProject.copy(

            layers = factoryProject.layers
                .map { it.copy() }
                .toMutableList(),

            overlay = factoryProject.overlay.copy(),

            clock = factoryProject.clock.copy()

        )

    }

}