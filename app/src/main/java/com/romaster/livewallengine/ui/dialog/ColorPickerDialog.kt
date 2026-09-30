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

package com.romaster.livewallengine.ui.dialog

import android.content.Context
import android.app.AlertDialog
import android.view.LayoutInflater
import android.graphics.Color
import android.widget.TextView
import android.view.View
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton

import com.romaster.livewallengine.R
import com.romaster.livewallengine.LocaleHelper

import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.sliders.AlphaSlideBar
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar

object ColorPickerDialog {

    fun show(

        context: Context,
    
        initialColor: String,
    
        onColorSelected: (String) -> Unit
    
    ) {
        // Context con locale de la app (título / Editar HEX / botones)
        val ctx = LocaleHelper.wrap(context)
        
        val view =
            LayoutInflater
                .from(ctx)
                .inflate(
                    R.layout.dialog_color_picker,
                    null
                )
        
        view.findViewById<TextView>(R.id.textColorPickerTitle)?.text =
            ctx.getString(R.string.color_pick_title)

        val buttonHex =
            view.findViewById<MaterialButton>(
                R.id.buttonEditHex
            )
        buttonHex.text = ctx.getString(R.string.color_edit_hex)
        
        val preview =
            view.findViewById<View>(
                R.id.colorPreview
            )
        
        val hexText =
            view.findViewById<TextView>(
                R.id.textHex
            )
        
        val picker =
            view.findViewById<ColorPickerView>(
                R.id.colorPicker
            )
        
        val alpha =
            view.findViewById<AlphaSlideBar>(
                R.id.alphaSlide
            )
        
        val brightness =
            view.findViewById<BrightnessSlideBar>(
                R.id.brightnessSlide
            )
    
        picker.attachAlphaSlider(
            alpha
        )
    
        picker.attachBrightnessSlider(
            brightness
        )
        
        val initialHex =
            normalizeHex(initialColor)
        
        preview.setBackgroundColor(
            Color.parseColor(initialHex)
        )
        
        hexText.text =
            initialHex
        
        var selectedHex =
            initialHex
        
        picker.setInitialColor(
            Color.parseColor(initialHex)
        )
    
        picker.setColorListener(

            ColorEnvelopeListener {
        
                    envelope,
        
                    _ ->
        
                selectedHex =
                    "#${envelope.hexCode}"
        
                preview.setBackgroundColor(
                    Color.parseColor(
                        selectedHex
                    )
                )
        
                hexText.text =
                    selectedHex
            }
        
        )
        
        buttonHex.setOnClickListener {

            showHexEditor(
        
                context,
        
                selectedHex
        
            ) { hex ->
        
                selectedHex = hex
        
                preview.setBackgroundColor(
                    Color.parseColor(hex)
                )
        
                hexText.text = hex
        
                picker.setInitialColor(
                    Color.parseColor(hex)
                )
        
            }
        
        }
    
        AlertDialog.Builder(ctx)
    
            .setView(view)
    
            .setPositiveButton(
                ctx.getString(R.string.ok)
            ) { _, _ ->
    
                onColorSelected(
                    selectedHex
                )
            }
    
            .setNegativeButton(
                ctx.getString(R.string.cancel),
                null
            )
    
            .show()
    }
    
    private fun showHexEditor(

        context: Context,
    
        initialColor: String,
    
        onHexSelected: (String) -> Unit
    
    ) {
        val ctx = LocaleHelper.wrap(context)
        
        val initialHex =
            normalizeHex(initialColor)
    
        val view = LayoutInflater
            .from(ctx)
            .inflate(
                R.layout.dialog_hex_input,
                null
            )
    
        val preview =
            view.findViewById<View>(
                R.id.previewHexColor
            )
    
        val layout =
            view.findViewById<TextInputLayout>(
                R.id.layoutHex
            )
    
        val edit =
            view.findViewById<EditText>(
                R.id.editHex
            )
    
        edit.setText(
            initialHex
        )
    
        preview.setBackgroundColor(
            Color.parseColor(
                initialHex
            )
        )
    
        lateinit var dialog: AlertDialog
    
        dialog =
            AlertDialog.Builder(ctx)
    
                .setTitle(
                    ctx.getString(R.string.color_custom_title)
                )
    
                .setView(view)
    
                .setPositiveButton(
                    ctx.getString(R.string.ok),
                    null
                )
    
                .setNegativeButton(
                    ctx.getString(R.string.cancel),
                    null
                )
    
                .create()
    
        dialog.setOnShowListener {
    
            val positive =
                dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
                )
    
            positive.setOnClickListener {
    
                val hex =
                    edit.text
                        .toString()
                        .uppercase()
    
                if (
                    isValidHex(hex)
                ) {
    
                    onHexSelected(hex)
    
                    dialog.dismiss()
                }
            }
    
            positive.isEnabled =
                isValidHex(
                    initialHex
                )
        }
    
        edit.addTextChangedListener(
    
            object : TextWatcher {
    
                override fun beforeTextChanged(
    
                    s: CharSequence?,
    
                    start: Int,
    
                    count: Int,
    
                    after: Int
    
                ) {
                }
    
                override fun onTextChanged(
    
                    s: CharSequence?,
    
                    start: Int,
    
                    before: Int,
    
                    count: Int
    
                ) {
                }
    
                override fun afterTextChanged(
    
                    editable: Editable?
    
                ) {
    
                    val hex =
                        editable
                            .toString()
                            .uppercase()
    
                    val valid =
                        isValidHex(hex)
    
                    layout.error =
                        if (valid)
                            null
                        else
                            ctx.getString(R.string.color_hex_invalid)
    
                    dialog
                        .getButton(
                            AlertDialog.BUTTON_POSITIVE
                        )
                        ?.isEnabled =
                        valid
    
                    if (valid) {
    
                        preview.setBackgroundColor(
    
                            Color.parseColor(hex)
    
                        )
                    }
                }
            }
        )
        dialog.show()
    }
    
    private fun isValidHex(
        value: String
    ): Boolean {
    
        return Regex(
            "^#[0-9A-Fa-f]{8}$"
        ).matches(value)
    }
    
    private fun normalizeHex(
        value: String
    ): String {
    
        val hex = value.uppercase()
    
        return when (hex.length) {
    
            7 -> "#FF${hex.substring(1)}"
    
            9 -> hex
    
            else -> "#FFFFFFFF"
        }
    }
}