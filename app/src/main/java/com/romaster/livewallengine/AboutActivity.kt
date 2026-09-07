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

package com.romaster.livewallengine

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla "Acerca de" con video de fondo en bucle y enlaces de contacto / donación.
 *
 * Video: res/raw/about_bg.mp4 (si no existe, usa test.mp4).
 * Logos: res/drawable/about_ic_*.png (reemplazables por los oficiales).
 */
class AboutActivity : AppCompatActivity() {

    private var videoView: VideoView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        videoView = findViewById(R.id.videoAboutBackground)
        setupBackgroundVideo()

        findViewById<TextView>(R.id.textAboutVersion).text =
            "Versión ${appVersionName()}"

        findViewById<View>(R.id.buttonAboutClose).setOnClickListener { finish() }

        bindLink(R.id.buttonAboutInstagram, URL_INSTAGRAM)
        bindLink(R.id.buttonAboutThreads, URL_THREADS)
        bindLink(R.id.buttonAboutYoutube, URL_YOUTUBE)
        bindLink(R.id.buttonAboutGithub, URL_GITHUB)
        bindLink(R.id.buttonAboutReddit, URL_REDDIT)
        bindLink(R.id.buttonAboutPatreon, URL_PATREON)
        bindLink(R.id.buttonAboutGmail, URL_MAIL)
        bindLink(R.id.buttonAboutBmc, URL_BMC)

        findViewById<TextView>(R.id.textAboutBmcLabel).setOnClickListener {
            openUrl(URL_BMC)
        }
    }

    private fun setupBackgroundVideo() {
        val vv = videoView ?: return
        val resId = resources.getIdentifier("about_bg", "raw", packageName)
            .takeIf { it != 0 } ?: R.raw.test
        val uri = Uri.parse("android.resource://$packageName/$resId")
        vv.setVideoURI(uri)
        vv.setOnPreparedListener { mp: MediaPlayer ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            // Centrar / cubrir sin deformar demasiado
            try {
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            } catch (_: Exception) {
            }
            vv.start()
        }
        vv.setOnErrorListener { _, _, _ ->
            // Si falla el video, dejamos el fondo sólido del layout
            true
        }
    }

    private fun bindLink(buttonId: Int, url: String) {
        findViewById<ImageButton>(buttonId).setOnClickListener { openUrl(url) }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    private fun appVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    override fun onResume() {
        super.onResume()
        videoView?.start()
    }

    override fun onPause() {
        videoView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        videoView?.stopPlayback()
        videoView = null
        super.onDestroy()
    }

    companion object {
        const val URL_INSTAGRAM = "https://www.instagram.com/romaster_tuning"
        const val URL_THREADS = "https://www.threads.com/@romaster_tuning"
        const val URL_YOUTUBE = "https://youtube.com/@romaster85"
        const val URL_GITHUB = "https://github.com/Romaster1985"
        const val URL_REDDIT = "https://www.reddit.com/u/Local_Row_8542/s/fnOW0meSeQ"
        const val URL_PATREON = "https://www.patreon.com/romasterdroidtuning"
        const val URL_MAIL = "mailto:roman.ignacio.romero@gmail.com"
        const val URL_BMC = "https://www.buymeacoffee.com/Romaster"
    }
}
