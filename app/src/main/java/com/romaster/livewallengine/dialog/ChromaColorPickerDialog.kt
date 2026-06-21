package com.romaster.livewallengine.dialog

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView

import com.google.android.material.slider.Slider
import com.romaster.livewallengine.R
import com.romaster.livewallengine.storage.StorageManager
import com.romaster.livewallengine.video.VideoStorage
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
        val view =
            LayoutInflater.from(context)
                .inflate(
                    R.layout.dialog_chroma_picker,
                    null
                )
        val colorPicker =
            view.findViewById<ColorPickerView>(
                R.id.colorPickerView
            )
        val slider =
            view.findViewById<Slider>(
                R.id.sliderFrame
            )
        val textTime =
            view.findViewById<TextView>(
                R.id.textFrameTime
            )
        val preview =
            view.findViewById<View>(
                R.id.viewSelectedColor
            )
        val textHex =
            view.findViewById<TextView>(
                R.id.textSelectedHex
            )
        var selectedColor =
            initialColor
        preview.setBackgroundColor(
            initialColor
        )
        textHex.text =
            String.format(
                "#%06X",
                0xFFFFFF and initialColor
            )

        //--------------------------------------------------
        // Abrir video del Overlay
        //--------------------------------------------------
        val project =
            StorageManager.loadProject(
                context
            )
        val fileName =
            project?.overlayVideo
                ?: VideoStorage.OVERLAY_VIDEO
        val file =
            VideoStorage.getVideoFile(
                context,
                fileName
            )
        if (file.exists()) {
            retriever.setDataSource(
                file.absolutePath
            )
        } else {
            val afd =
                context.resources
                    .openRawResourceFd(
                        R.raw.test
                    )
            retriever.setDataSource(
                afd.fileDescriptor,
                afd.startOffset,
                afd.length
            )
            afd.close()
        }

        durationMs =
            retriever.extractMetadata(
                MediaMetadataRetriever
                    .METADATA_KEY_DURATION
            )?.toLongOrNull()
                ?: 0L

        slider.valueFrom = 0f
        slider.valueTo = durationMs.toFloat()
        slider.value = 0f

        //--------------------------------------------------
        // Primer frame
        //--------------------------------------------------
        updateFrame(
            colorPicker,
            textTime,
            0L
        )

        //--------------------------------------------------
        // Cambio de frame
        //--------------------------------------------------
        slider.addOnChangeListener {
                _,
                value,
                fromUser ->
            if (!fromUser)
                return@addOnChangeListener
            updateFrame(
                colorPicker,
                textTime,
                value.toLong()
            )
        }

        //--------------------------------------------------
        // Selección de color
        //--------------------------------------------------
        colorPicker.setColorListener(
            ColorEnvelopeListener {
                envelope: ColorEnvelope,
                _ ->
                selectedColor = envelope.color
                preview.setBackgroundColor(selectedColor)
                textHex.text = envelope.hexCode
            }
        )
        AlertDialog.Builder(context)
            .setTitle(
                "Seleccionar color Chroma"
            )
            .setView(view)
            .setNegativeButton(
                "Cancelar"
            ) { _, _ ->
                retriever.release()
            }
            .setPositiveButton(
                "Aceptar"
            ) { _, _ ->
                retriever.release()
                onColorSelected(
                    selectedColor
                )
            }
            .show()
    }
    
    // --------------------------------------------------
    // Actualiza el frame mostrado en el ColorPickerView
    // --------------------------------------------------
    private fun updateFrame(
        colorPicker: ColorPickerView,
        textTime: TextView,
        timeMs: Long
    ) {
        try {
            val bitmap = retriever.getFrameAtTime(
                timeMs * 1000L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )
            
            if (bitmap != null) {
                colorPicker.setPaletteDrawable(
                    BitmapDrawable(
                        context.resources,
                        bitmap
                    )
                )
                textTime.text = formatTime(timeMs)
            }
        } catch (_: Exception) {
            // Ignorar error
        }
    }
    
    // --------------------------------------------------
    // Formato mm:ss.mmm
    // --------------------------------------------------
    private fun formatTime(
        timeMs: Long
    ): String {
        val minutes = timeMs / 60000
        val seconds = (timeMs % 60000) / 1000
        val millis = timeMs % 1000
        return String.format(
                "%02d:%02d.%03d",
                minutes,
                seconds,
                millis
            )
    }
}