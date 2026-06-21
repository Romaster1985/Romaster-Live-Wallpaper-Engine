package com.romaster.livewallengine.debug

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    
    private const val DEBUG_LOG = true

    private const val DIR_NAME =
        "Romaster_LiveWall_Engine"

    private const val FILE_NAME =
        "log.txt"

    private fun getLogFile(
        context: Context
    ): File {

        val dir = File(
            "/storage/emulated/0/Documents/$DIR_NAME"
        )

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return File(
            dir,
            FILE_NAME
        )
    }

    fun clear(
        context: Context
    ) {
        
        if (!DEBUG_LOG) return

        getLogFile(
            context
        ).writeText("")
    }

    fun log(
        context: Context,
        message: String
    ) {
        
        if (!DEBUG_LOG) return

        val time =
            SimpleDateFormat(
                "HH:mm:ss.SSS",
                Locale.getDefault()
            ).format(Date())

        val thread =
            Thread.currentThread().name

        getLogFile(context)
            .appendText(
                "[$time] [$thread] $message\n"
            )
    }

    fun logException(
        context: Context,
        title: String,
        e: Throwable
    ) {
        
        if (!DEBUG_LOG) return

        log(
            context,
            "ERROR: $title"
        )

        getLogFile(context)
            .appendText(
                e.stackTraceToString() +
                        "\n\n"
            )
    }

    fun writeDeviceInfo(
        context: Context
    ) {
        
        if (!DEBUG_LOG) return

        log(
            context,
            "========== DEVICE =========="
        )

        log(
            context,
            "Manufacturer: ${Build.MANUFACTURER}"
        )

        log(
            context,
            "Model: ${Build.MODEL}"
        )

        log(
            context,
            "Android: ${Build.VERSION.RELEASE}"
        )

        log(
            context,
            "SDK: ${Build.VERSION.SDK_INT}"
        )

        log(
            context,
            "============================"
        )
    }
}