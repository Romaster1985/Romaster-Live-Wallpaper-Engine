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
