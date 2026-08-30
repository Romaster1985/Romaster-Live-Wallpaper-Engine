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

package com.romaster.livewallengine.wallpaper

import android.graphics.Canvas
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

import com.romaster.livewallengine.render.RenderThread
import com.romaster.livewallengine.render.WallpaperRenderer
import com.romaster.livewallengine.storage.StorageManager

class CanvasWallpaperService :
    WallpaperService() {

    override fun onCreateEngine():
        Engine {

        return CanvasEngine()
    }

    inner class CanvasEngine :
        Engine() {

        private val renderer =
            WallpaperRenderer()

        private var renderThread:
            RenderThread? = null

        override fun onSurfaceCreated(
            holder: SurfaceHolder
        ) {

            super.onSurfaceCreated(
                holder
            )

            renderThread =
                RenderThread {

                    drawFrame()
                }

            renderThread?.start()
        }

        private fun drawFrame() {

            val project =
                StorageManager.loadProject(
                    applicationContext
                ) ?: return

            val canvas: Canvas =
                surfaceHolder.lockCanvas()
                    ?: return

            try {

                canvas.drawColor(
                    android.graphics.Color.TRANSPARENT
                )

                renderer.draw(
                    applicationContext,
                    canvas,
                    project
                )

            } finally {

                surfaceHolder.unlockCanvasAndPost(
                    canvas
                )
            }
        }

        override fun onDestroy() {

            renderThread?.shutdown()

            renderThread = null

            super.onDestroy()
        }
    }
}