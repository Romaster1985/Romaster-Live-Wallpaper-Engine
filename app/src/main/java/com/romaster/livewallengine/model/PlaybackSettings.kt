package com.romaster.livewallengine.model

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackSettings(

    var enabled: Boolean = false,

    var overlayDurationMs: Long = 0L,

    var lockedCueMs: Long = 0L,

    var unlockedCueMs: Long = 0L,

    var previewLocked: Boolean = false,

    var cuesInitialized: Boolean = false

)