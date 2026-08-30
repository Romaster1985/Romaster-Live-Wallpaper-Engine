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

package com.romaster.livewallengine.audio

import android.app.Activity
import android.content.Intent

object AudioPicker {

    const val REQUEST_BG_SOUND = 3001

    const val REQUEST_OVERLAY_SOUND = 3002

    fun open(

        activity: Activity,

        requestCode: Int

    ) {

        val intent = Intent(

            Intent.ACTION_OPEN_DOCUMENT

        )

        intent.addCategory(

            Intent.CATEGORY_OPENABLE

        )

        intent.type = "audio/*"

        activity.startActivityForResult(

            intent,

            requestCode

        )

    }

}