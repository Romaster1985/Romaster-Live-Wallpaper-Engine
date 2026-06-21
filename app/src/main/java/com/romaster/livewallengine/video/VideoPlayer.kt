package com.romaster.livewallengine.video

import android.content.Context
import android.media.MediaPlayer
import android.view.Surface
import com.romaster.livewallengine.R

class VideoPlayer(

    private val context: Context
    
    ) {
    
    private var mediaPlayer: MediaPlayer? = null
    
    private var prepared = false
    
    fun initialize(
        surface: Surface
    ) {
    
        if (prepared) {
            return
        }
    
        mediaPlayer =
            MediaPlayer.create(
                context,
                R.raw.test
            )?.apply {
    
                setSurface(surface)
    
                isLooping = true
            }
    
        prepared = true
    }
    
    fun play() {
    
        mediaPlayer?.let {
    
            if (!it.isPlaying) {
    
                it.start()
            }
        }
    }
    
    fun pause() {
    
        mediaPlayer?.let {
    
            if (it.isPlaying) {
    
                it.pause()
            }
        }
    }
    
    // ============================================  
    // NUEVO: SEEK A UNA POSICIÓN ESPECÍFICA  
    // ============================================  
    fun seekTo(
        position: Int
    ) {
    
        mediaPlayer?.seekTo(position)
    }
    
    // ============================================  
    // NUEVO: OBTENER POSICIÓN ACTUAL  
    // ============================================  
    fun getCurrentPosition(): Int {
    
        return mediaPlayer?.currentPosition ?: 0
    }
    
    fun getDuration(): Int {
    
        return mediaPlayer?.duration ?: 0
    }
    
    fun getVideoWidth(): Int {
    
        return mediaPlayer?.videoWidth ?: 0
    }
    
    fun getVideoHeight(): Int {
    
        return mediaPlayer?.videoHeight ?: 0
    }
    
    fun isPlaying(): Boolean {
    
        return mediaPlayer?.isPlaying ?: false
    }
        
    fun release() {
    
        mediaPlayer?.release()
    
        mediaPlayer = null
    
        prepared = false
    }
}