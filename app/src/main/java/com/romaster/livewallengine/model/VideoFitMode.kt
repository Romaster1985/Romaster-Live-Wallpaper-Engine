package com.romaster.livewallengine.model

import kotlinx.serialization.Serializable

@Serializable
enum class VideoFitMode {

    FIT,

    FILL,

    STRETCH,

    FREE

}