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