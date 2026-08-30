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

package com.romaster.livewallengine.font

import android.app.Activity
import android.content.Intent

object FontPicker {

    const val REQUEST_FONT = 2003

    fun open(
        activity: Activity
    ) {

        val intent =
            Intent(
                Intent.ACTION_OPEN_DOCUMENT
            )

        intent.type = "*/*"

        intent.addCategory(
            Intent.CATEGORY_OPENABLE
        )

        intent.putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf(
                "font/*",
                "application/x-font-ttf",
                "application/x-font-otf"
            )
        )

        activity.startActivityForResult(
            intent,
            REQUEST_FONT
        )
    }
}