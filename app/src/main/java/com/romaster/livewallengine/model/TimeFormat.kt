package com.romaster.livewallengine.model

import kotlinx.serialization.Serializable

@Serializable
enum class TimeFormat {

    HH_MM,

    HH_MM_SS,

    HH_MM_AM_PM
}