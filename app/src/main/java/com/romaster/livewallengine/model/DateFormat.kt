package com.romaster.livewallengine.model

import kotlinx.serialization.Serializable

@Serializable
enum class DateFormat {

    DOW_DD_MON,

    DD_MM_YYYY,

    DOW_DD_MON_YYYY
}