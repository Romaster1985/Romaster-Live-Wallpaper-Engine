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
 */

package com.romaster.livewallengine.media

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaMetadata
import java.io.FileOutputStream
import java.io.File
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference

/**
 * Info del reproductor activo vía [MediaSessionManager] (requiere acceso a notificaciones).
 * Compatible con fórmulas KLWP: mi(artist), mi(title), mi(state), …
 */
object MusicInfoProvider {

    data class Snapshot(
        val artist: String = "",
        val title: String = "",
        val album: String = "",
        val state: String = "NONE",
        val durationMs: Long = 0L,
        /** Posición muestreada (ms) en [positionSampleElapsed]. */
        val positionMs: Long = 0L,
        val positionSampleElapsed: Long = 0L,
        val playing: Boolean = false,
        val packageName: String = "",
        val coverUri: String = "",
        val track: Int = 0
    )

    private const val PREFS = "romaster_music_prefs"
    private const val KEY_PREFERRED = "preferred_package"

    private val cache = AtomicReference(Snapshot())

    @Volatile
    private var appContext: Context? = null

    private var activeController: MediaController? = null

    private val sessionCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            val ctx = appContext ?: return
            val c = activeController ?: return
            try {
                cache.set(snapshotFrom(c, ctx))
            } catch (_: Exception) {
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            val ctx = appContext ?: return
            val c = activeController ?: return
            try {
                cache.set(snapshotFrom(c, ctx))
            } catch (_: Exception) {
            }
        }
    }

    fun getPreferredPackage(context: Context): String {
        return prefs(context).getString(KEY_PREFERRED, "") ?: ""
    }

    fun setPreferredPackage(context: Context, packageName: String) {
        prefs(context).edit().putString(KEY_PREFERRED, packageName).apply()
        refresh(context)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(): Snapshot = cache.get()

    fun onListenerConnected(context: Context) {
        refresh(context)
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        appContext = app
        try {
            val msm = app.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
                ?: return
            val listener = ComponentName(app, MediaNotificationListener::class.java)
            val controllers = try {
                msm.getActiveSessions(listener)
            } catch (_: SecurityException) {
                emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val preferred = getPreferredPackage(app)
            val chosen = pickController(controllers, preferred)
            if (chosen == null) {
                unregisterActiveController()
                cache.set(Snapshot())
                return
            }
            // Registrar callback para detectar cambio de tema en vivo
            if (activeController !== chosen) {
                unregisterActiveController()
                try {
                    chosen.registerCallback(sessionCallback)
                    activeController = chosen
                } catch (_: Exception) {
                    activeController = null
                }
            }
            cache.set(snapshotFrom(chosen, app))
        } catch (_: Exception) {
            // mantener cache
        }
    }

    private fun unregisterActiveController() {
        try {
            activeController?.unregisterCallback(sessionCallback)
        } catch (_: Exception) {
        }
        activeController = null
    }

    private fun pickController(
        controllers: List<MediaController>,
        preferred: String
    ): MediaController? {
        if (controllers.isEmpty()) return null
        if (preferred.isNotBlank()) {
            controllers.firstOrNull { it.packageName == preferred }?.let { return it }
        }
        // Preferir el que esté reproduciendo
        controllers.firstOrNull {
            it.playbackState?.state == PlaybackState.STATE_PLAYING
        }?.let { return it }
        // Luego pausado con metadata
        controllers.firstOrNull {
            val st = it.playbackState?.state
            st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_STOPPED
        }?.let { return it }
        return controllers.firstOrNull()
    }

    private fun snapshotFrom(c: MediaController, context: Context): Snapshot {
        val meta = c.metadata
        val state = c.playbackState
        val st = mapState(state?.state ?: PlaybackState.STATE_NONE)
        val playing = state?.state == PlaybackState.STATE_PLAYING
        var pos = state?.position ?: 0L
        if (pos < 0) pos = 0L
        val dur = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val track = (meta?.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER) ?: 0L).toInt()
        val pkg = c.packageName ?: ""
        val cover = resolveCoverUri(meta, context, pkg)
        return Snapshot(
            artist = artist,
            title = title,
            album = album,
            state = st,
            durationMs = dur.coerceAtLeast(0L),
            positionMs = pos,
            positionSampleElapsed = SystemClock.elapsedRealtime(),
            playing = playing,
            packageName = pkg,
            coverUri = cover,
            track = track
        )
    }

    /**
     * Prioridad: HTTPS/HTTP (como KLWP/Spotify CDN) → bitmap embebido cacheado → content:// u otras URIs.
     */
    private fun resolveCoverUri(meta: MediaMetadata?, context: Context, pkg: String): String {
        if (meta == null) return ""
        val candidates = listOfNotNull(
            meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            meta.getString(MediaMetadata.METADATA_KEY_ART_URI)
        ).map { it.trim() }.filter { it.isNotEmpty() }

        candidates.firstOrNull {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }?.let { return it }

        // Identidad del tema para que cada canción tenga archivo distinto (el cache de fórmulas detecta el cambio)
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val album = meta.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val artHint = candidates.firstOrNull() ?: ""
        val trackKey = "${pkg}|${title}|${artist}|${album}|${artHint}".hashCode().toString()

        // Bitmap embebido → archivo local único por tema
        val bmp: Bitmap? = try {
            meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        } catch (_: Exception) {
            null
        }
        if (bmp != null && !bmp.isRecycled) {
            try {
                val dir = File(context.cacheDir, "mi_covers")
                if (!dir.exists()) dir.mkdirs()
                val f = File(dir, "cover_$trackKey.jpg")
                if (!f.exists() || f.length() < 32L) {
                    FileOutputStream(f).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                }
                return f.absolutePath
            } catch (_: Exception) {
            }
        }

        // content:// u otras: añadir fragmento con trackKey para invalidar cache de fórmulas
        val base = candidates.firstOrNull() ?: return ""
        return if (base.contains("#mi=")) base else "$base#mi=$trackKey"
    }

    private fun mapState(state: Int): String = when (state) {
        PlaybackState.STATE_STOPPED -> "STOPPED"
        PlaybackState.STATE_PAUSED -> "PAUSED"
        PlaybackState.STATE_PLAYING -> "PLAYING"
        PlaybackState.STATE_FAST_FORWARDING -> "FORWARDING"
        PlaybackState.STATE_REWINDING -> "REWINDING"
        PlaybackState.STATE_SKIPPING_TO_NEXT -> "SKIPPING_FORWARDS"
        PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> "SKIPPING_BACKWARDS"
        PlaybackState.STATE_BUFFERING -> "BUFFERING"
        PlaybackState.STATE_ERROR -> "ERROR"
        PlaybackState.STATE_CONNECTING -> "BUFFERING"
        else -> "NONE"
    }

    /** Posición actual estimada (ms), avanza si está en PLAYING. */
    fun currentPositionMs(): Long {
        val s = cache.get()
        if (!s.playing) return s.positionMs.coerceAtLeast(0L)
        val elapsed = SystemClock.elapsedRealtime() - s.positionSampleElapsed
        val pos = s.positionMs + elapsed
        return if (s.durationMs > 0) pos.coerceIn(0L, s.durationMs) else pos.coerceAtLeast(0L)
    }

    fun field(name: String, context: Context?): String {
        if (context != null) {
            // Refresco ligero en cada evaluación (MediaSession es barato si hay listener)
            refresh(context)
        }
        val s = cache.get()
        return when (name.lowercase()) {
            "artist" -> s.artist
            "title" -> s.title
            "album" -> s.album
            "state" -> s.state
            "len", "duration" -> (s.durationMs / 1000L).toString()
            "pos", "position" -> (currentPositionMs() / 1000L).toString()
            "percent", "pct" -> {
                if (s.durationMs <= 0L) "0"
                else {
                    val p = (currentPositionMs() * 100.0) / s.durationMs
                    String.format(java.util.Locale.US, "%.2f", p.coerceIn(0.0, 100.0))
                }
            }
            "vol", "volume" -> {
                if (context == null) return "0"
                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    ((cur * 100) / max).toString()
                } catch (_: Exception) {
                    "0"
                }
            }
            "package", "pkg" -> s.packageName
            "cover", "art" -> s.coverUri
            "track" -> s.track.toString()
            "playing" -> if (s.playing) "1" else "0"
            else -> ""
        }
    }

    fun isNotificationAccessEnabled(context: Context): Boolean {
        return try {
            val cn = ComponentName(context, MediaNotificationListener::class.java)
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            flat.split(':').any {
                ComponentName.unflattenFromString(it)?.packageName == context.packageName ||
                    it.contains(context.packageName)
            } || flat.contains(cn.flattenToString()) ||
                flat.contains(cn.flattenToShortString())
        } catch (_: Exception) {
            false
        }
    }
}
