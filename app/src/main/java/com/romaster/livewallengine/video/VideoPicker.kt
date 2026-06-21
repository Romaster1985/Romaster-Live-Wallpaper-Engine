package com.romaster.livewallengine.video

import android.app.Activity
import android.content.Intent

object VideoPicker {

    const val REQUEST_CODE = 1001

    fun open(
        activity: Activity
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
            REQUEST_CODE
        )
    }
}