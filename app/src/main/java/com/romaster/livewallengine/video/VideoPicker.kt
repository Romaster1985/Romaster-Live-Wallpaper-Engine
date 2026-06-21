package com.romaster.livewallengine.video

import android.app.Activity
import android.content.Intent

object VideoPicker {

    const val REQUEST_WALLPAPER = 1001

    const val REQUEST_OVERLAY = 1002

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

        intent.type = "video/*"

        activity.startActivityForResult(
            intent,
            requestCode
        )
    }
}