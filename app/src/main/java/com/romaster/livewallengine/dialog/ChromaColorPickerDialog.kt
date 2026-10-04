/*
 * -----------------------------------------------------------------------------
 * ColorPickerView (com.github.skydoves:colorpickerview)
 * Copyright 2017 skydoves (Jaewoong Eum)
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
 * -----------------------------------------------------------------------------
 *
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
 * Nota: Este archivo integra ColorPickerView licenciado bajo Apache 2.0.
 */

package com.romaster.livewallengine.dialog

import androidx.appcompat.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.romaster.livewallengine.LocaleHelper
import com.romaster.livewallengine.R
import com.romaster.livewallengine.storage.StorageManager
import com.romaster.livewallengine.video.VideoStorage
import com.romaster.livewallengine.ui.dialog.ColorPickerDialog
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class ChromaColorPickerDialog(
    private val context: Context
) {
    private val retriever = MediaMetadataRetriever()
    private var durationMs = 0L

    fun show(
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_chroma_picker, null)

        val colorPicker = view.findViewById<ColorPickerView>(R.id.colorPickerView)
        val slider = view.findViewById<Slider>(R.id.sliderFrame)
        val textTime = view.findViewById<TextView>(R.id.textFrameTime)
        val preview = view.findViewById<View>(R.id.viewSelectedColor)
        val textHex = view.findViewById<TextView>(R.id.textSelectedHex)
        val buttonHex = view.findViewById<MaterialButton>(R.id.buttonEditHex)
        val strCtx = LocaleHelper.wrap(context)
        buttonHex?.text = strCtx.getString(R.string.color_edit_hex)

        var selectedColor = initialColor
        preview.setBackgroundColor(initialColor)
        textHex.text = String.format("#%06X", 0xFFFFFF and initialColor)

        //--------------------------------------------------
        // Abrir video del Overlay
        //--------------------------------------------------
        val project = StorageManager.loadProject(context)
        val fileName = project?.overlayVideo ?: VideoStorage.OVERLAY_VIDEO
        val file = VideoStorage.getVideoFile(context, fileName)
        try {
            if (file.exists()) {
                retriever.setDataSource(file.absolutePath)
            } else {
                val afd = context.resources.openRawResourceFd(R.raw.test)
                retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            }
            durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            durationMs = 0L
        }

        // Slider en ms. NO usar stepSize si (valueTo-valueFrom) no es múltiplo
        // exacto del paso: Material Slider lanza IllegalStateException y crashea.
        // OPTION_CLOSEST ya entrega muchos más frames que los keyframes.
        val maxMs = durationMs.toFloat().coerceAtLeast(1f)
        slider.valueFrom = 0f
        slider.valueTo = maxMs
        slider.value = 0f
        // stepSize = 0 → continuo (válido). Si se quiere discreto, valueTo debe
        // quedar como N * stepSize con N entero.
        try {
            updateFrame(colorPicker, textTime, 0L)
        } catch (_: Exception) {
            textTime.text = formatTime(0L)
        }

        slider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            updateFrame(colorPicker, textTime, value.toLong())
        }

        colorPicker.setColorListener(
            ColorEnvelopeListener { envelope: ColorEnvelope, _ ->
                selectedColor = envelope.color
                preview.setBackgroundColor(selectedColor)
                textHex.text = "#${envelope.hexCode.removePrefix("#")}"
            }
        )

        buttonHex?.setOnClickListener {
            val current = textHex.text?.toString() ?: "#000000"
            ColorPickerDialog.showHexEditor(context, current) { hex ->
                try {
                    val c = Color.parseColor(hex)
                    selectedColor = c
                    preview.setBackgroundColor(c)
                    textHex.text = hex.uppercase()
                    // Reflejar en el picker si soporta color inicial
                    try {
                        colorPicker.setInitialColor(c)
                    } catch (_: Exception) {
                    }
                } catch (_: Exception) {
                }
            }
        }

        AlertDialog.Builder(context)
            .setTitle(strCtx.getString(R.string.chroma_pick_title))
            .setView(view)
            .setNegativeButton(strCtx.getString(R.string.cancel)) { _, _ ->
                retriever.release()
            }
            .setPositiveButton(strCtx.getString(R.string.ok)) { _, _ ->
                retriever.release()
                onColorSelected(selectedColor)
            }
            .setOnCancelListener {
                retriever.release()
            }
            .show()
    }

    /**
     * OPTION_CLOSEST: frame más cercano al tiempo pedido (no solo keyframe).
     * Da muchas más imágenes al mover el slider que OPTION_CLOSEST_SYNC.
     */
    private fun updateFrame(
        colorPicker: ColorPickerView,
        textTime: TextView,
        timeMs: Long
    ) {
        try {
            val bitmap = retriever.getFrameAtTime(
                timeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            )
            if (bitmap != null) {
                colorPicker.setPaletteDrawable(
                    BitmapDrawable(context.resources, bitmap)
                )
                textTime.text = formatTime(timeMs)
            } else {
                // Fallback a keyframe si el decoder no entrega frame exacto
                val fallback = retriever.getFrameAtTime(
                    timeMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                if (fallback != null) {
                    colorPicker.setPaletteDrawable(
                        BitmapDrawable(context.resources, fallback)
                    )
                }
                textTime.text = formatTime(timeMs)
            }
        } catch (_: Exception) {
            // Ignorar error de frame
        }
    }

    private fun formatTime(timeMs: Long): String {
        val minutes = timeMs / 60000
        val seconds = (timeMs % 60000) / 1000
        val millis = timeMs % 1000
        return String.format("%02d:%02d.%03d", minutes, seconds, millis)
    }
}
