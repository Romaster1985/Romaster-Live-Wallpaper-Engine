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

package com.romaster.livewallengine.debug

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {

    // Activado temporalmente para diagnosticar CrystalBlur / desenfoque
    private const val DEBUG_LOG = true

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