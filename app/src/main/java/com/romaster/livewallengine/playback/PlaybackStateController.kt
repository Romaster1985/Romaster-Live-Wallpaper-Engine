package com.romaster.livewallengine.playback

object PlaybackStateController {

    @Volatile
    private var state =
        PlaybackState.UNLOCKED

    fun getState(): PlaybackState {

        return state

    }

    fun isLocked(): Boolean {

        return state == PlaybackState.LOCKED

    }

    fun isUnlocked(): Boolean {

        return state == PlaybackState.UNLOCKED

    }

    fun setLocked() {

        state = PlaybackState.LOCKED

    }

    fun setUnlocked() {

        state = PlaybackState.UNLOCKED

    }

}