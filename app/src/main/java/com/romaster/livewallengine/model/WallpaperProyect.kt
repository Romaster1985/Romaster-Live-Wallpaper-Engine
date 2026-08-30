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