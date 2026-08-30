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

package com.romaster.livewallengine.video

import android.app.Activity
import android.content.Intent

object VideoPicker {

    const val REQUEST_WALLPAPER = 1001

    const val REQUEST_OVERLAY = 1002

    const val REQUEST_WALLPAPER_GIF = 1003

    const val REQUEST_OVERLAY_GIF = 1004

    fun open(
        activity: Activity,
        requestCode: Int
    ) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        activity.startActivityForResult(intent, requestCode)
    }

    fun openGif(
        activity: Activity,
        requestCode: Int
    ) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/gif"
            // Algunos pickers no filtran bien solo con type
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/gif", "image/*"))
        }
        activity.startActivityForResult(intent, requestCode)
    }
}
