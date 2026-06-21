package com.romaster.livewallengine.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

class VideoWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return VideoEngine()
    }

    inner class VideoEngine : Engine() {

        override fun onCreate(
            surfaceHolder: SurfaceHolder
        ) {
            super.onCreate(surfaceHolder)
        }

        override fun onVisibilityChanged(
            visible: Boolean
        ) {
            super.onVisibilityChanged(visible)
        }

        override fun onDestroy() {
            super.onDestroy()
        }
    }
}