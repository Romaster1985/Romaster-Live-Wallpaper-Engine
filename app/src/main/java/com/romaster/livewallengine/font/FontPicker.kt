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