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
    var dateVerticalDeform: Float = 0f,

    /**
     * Si true, hora y fecha comparten la misma línea (baseline).
     * Con spacing 0 quedan superpuestos; la hora se dibuja detrás de la fecha.
     * El valor de dateSpacing desplaza en vertical la separación entre ambos.
     */
    var allowOverlap: Boolean = false,

    /** Grosor del borde de la hora (0 = sin borde). */
    var clockBorderWidth: Float = 0f,

    /** Grosor del borde de la fecha (0 = sin borde). */
    var dateBorderWidth: Float = 0f,

    var clockBorderColor: String = "#000000",

    var dateBorderColor: String = "#000000"
)
