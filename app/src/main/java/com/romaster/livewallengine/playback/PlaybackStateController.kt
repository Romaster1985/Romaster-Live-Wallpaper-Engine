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