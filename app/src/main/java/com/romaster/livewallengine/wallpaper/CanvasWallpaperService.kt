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