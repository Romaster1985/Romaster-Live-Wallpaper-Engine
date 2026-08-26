package com.romaster.livewallengine.model

import kotlinx.serialization.Serializable

@Serializable
data class ClockSettings(

    var enabledOnLockScreen: Boolean = true,
    
    var enabled: Boolean = true,

    var showDate: Boolean = true,

    var timeFormat: TimeFormat =
        TimeFormat.HH_MM,

    var dateFormat: DateFormat =
        DateFormat.DOW_DD_MON,

    var fontFile: String? = null,

    var clockSize: Float = 64f,

    var dateSize: Float = 32f,

    var clockColor: String = "#FFFFFF",

    var dateColor: String = "#FFFFFF",
    
    var clockColorPreset: String = "Blanco",

    var dateColorPreset: String = "Blanco",

    var alignment: TextAlignment = TextAlignment.CENTER,

    var x: Float = 0.5f,

    var y: Float = 0.5f,

    var dateSpacing: Float = 20f,
    
    var clockFont: String? = null,

    var dateFont: String? = null,

    /**
     * Si true, el reloj se dibuja entre el video de fondo y el Video-OL
     * (queda "detrás" del overlay de video).
     */
    var behindVideoOverlay: Boolean = false,

    /**
     * Si true, la fecha va arriba y la hora debajo.
     */
    var swapTimeAndDate: Boolean = false,

    /**
     * Deformación vertical de la hora en px (-500…+500). 0 = normal.
     */
    var clockVerticalDeform: Float = 0f,

    /**
     * Deformación vertical de la fecha en px (-500…+500). 0 = normal.
     */
    var dateVerticalDeform: Float = 0f
)
