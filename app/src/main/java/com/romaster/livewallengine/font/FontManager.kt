package com.romaster.livewallengine.font

import android.content.Context
import android.graphics.Typeface

object FontManager {

    fun loadTypeface(
        context: Context,
        fileName: String?
    ): Typeface? {

        if (
            fileName.isNullOrBlank()
        ) {
            return null
        }

        return try {

            Typeface.createFromFile(
                FontStorage.getFontFile(
                    context,
                    fileName
                )
            )

        } catch (
            _: Exception
        ) {

            null
        }
    }
}