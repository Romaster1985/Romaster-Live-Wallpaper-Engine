package com.romaster.livewallengine.debug

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {

    private const val DEBUG_LOG = false

    private const val DIR_NAME =
        "Romaster_LiveWall_Engine"

    private var currentLogFile: File? = null

    private var currentContext: Context? = null

    private fun getLogDirectory(): File {

        val dir =
            File(
                "/storage/emulated/0/Documents/$DIR_NAME"
            )

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return dir
    }

    fun startNewSession(
        context: Context
    ) {

        if (!DEBUG_LOG)
            return

        currentContext =
            context.applicationContext

        val timestamp =
            SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss",
                Locale.getDefault()
            ).format(
                Date()
            )

        currentLogFile =
            File(
                getLogDirectory(),
                "log_$timestamp.txt"
            )

        currentLogFile!!
            .writeText("")
    }

    private fun getCurrentLogFile(): File {

        return currentLogFile
            ?: throw IllegalStateException(
                "FileLogger.startNewSession() no fue llamado"
            )
    }

    fun log(
        context: Context,
        message: String
    ) {

        if (!DEBUG_LOG)
            return

        val time =
            SimpleDateFormat(
                "HH:mm:ss.SSS",
                Locale.getDefault()
            ).format(
                Date()
            )

        val thread =
            Thread.currentThread().name

        getCurrentLogFile()
            .appendText(
                "[$time] [$thread] $message\n"
            )
    }

    fun logException(
        context: Context,
        title: String,
        e: Throwable
    ) {

        if (!DEBUG_LOG)
            return

        log(
            context,
            "ERROR: $title"
        )

        getCurrentLogFile()
            .appendText(
                e.stackTraceToString() +
                        "\n\n"
            )
    }

    fun writeDeviceInfo(
        context: Context
    ) {

        if (!DEBUG_LOG)
            return

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