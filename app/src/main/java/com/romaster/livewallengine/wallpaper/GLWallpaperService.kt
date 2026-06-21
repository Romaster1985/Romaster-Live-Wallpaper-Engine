package com.romaster.livewallengine.wallpaper

import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.render.GLRenderer
import com.romaster.livewallengine.video.VideoPlayer

class GLWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
    
        FileLogger.clear(this)
    
        FileLogger.writeDeviceInfo(this)
    
        FileLogger.log(
            this,  
            "GLWallpaperService.onCreateEngine()"
        )
    
        return GLEngine()
    }
    
    inner class GLEngine : Engine() {
    
        private var holder: SurfaceHolder? = null
    
        private var renderer: GLRenderer? = null
    
        private var videoPlayer: VideoPlayer? = null
    
        private var renderThread: Thread? = null
    
        @Volatile
        private var running = false
    
        @Volatile
        private var visible = false
    
        // ============================================  
        // POSICIÓN GUARDADA DEL VIDEO  
        // ============================================  
        private var savedPosition: Int = 0
    
        override fun onSurfaceCreated(
            holder: SurfaceHolder
        ) {
    
            super.onSurfaceCreated(holder)
    
            this.holder = holder
    
            FileLogger.log(
                this@GLWallpaperService,
                "onSurfaceCreated()"
            )
    
            if (visible) {
                startRendering()
            }
        }
    
        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            FileLogger.log(
                this@GLWallpaperService,
                "onSurfaceChanged: ${width}x${height}"
            )
        }
    
        override fun onVisibilityChanged(
            visible: Boolean
        ) {
    
            super.onVisibilityChanged(visible)
    
            this.visible = visible
    
            FileLogger.log(
                this@GLWallpaperService,
                "onVisibilityChanged($visible)"
            )
    
            if (visible) {
                startRendering()
            } else {
                stopRendering()
            }
        }
    
        override fun onSurfaceDestroyed(
            holder: SurfaceHolder
        ) {
    
            FileLogger.log(
                this@GLWallpaperService,
                "onSurfaceDestroyed()"
            )
            
            stopRendering()
            
            this.holder = null
            
            super.onSurfaceDestroyed(holder)
        }
        
        override fun onDestroy() {
            
            FileLogger.log(
                this@GLWallpaperService,
                "onDestroy()"
            )
            
            stopRendering()
            
            super.onDestroy()
        }
        
        private fun startRendering() {
    
            if (renderThread?.isAlive == true) {
                return
            }
    
            running = true
    
            renderThread = Thread {
    
                FileLogger.log(
                    this@GLWallpaperService,
                    "RenderThread iniciado"
                )
    
                try {
    
                    val surfaceHolder = holder
                        ?: return@Thread
    
                    // ============================================  
                    // CREAR RENDERER  
                    // ============================================  
                    renderer = GLRenderer(this@GLWallpaperService, surfaceHolder)
                    renderer!!.initialize()
    
                    videoPlayer = VideoPlayer(this@GLWallpaperService)
                    videoPlayer!!.initialize(renderer!!.getVideoSurface())
    
                    // Restaurar posición si hay una guardada  
                    if (savedPosition > 0) {
                        videoPlayer!!.seekTo(savedPosition)
                        FileLogger.log(
                            this@GLWallpaperService,
                            "Video restaurado en posición: ${savedPosition}ms"
                        )
                    }
    
                    videoPlayer!!.play()
    
                    var frameCount = 0
    
                    while (running) {
    
                        renderer!!.drawFrame()
    
                        frameCount++
                        if (frameCount % 30 == 0) {
                            // Log cada 30 frames (≈2 segundos) - solo para saber que está vivo  
                            FileLogger.log(
                                this@GLWallpaperService,
                                "Frames: $frameCount"
                            )
                        }
    
                        Thread.sleep(16)
                    }
    
                } catch (e: InterruptedException) {
    
                    FileLogger.log(
                        this@GLWallpaperService,
                        "RenderThread detenido"
                    )
    
                } catch (e: Exception) {
    
                    FileLogger.logException(
                        this@GLWallpaperService,
                        "RenderThread",
                        e
                    )
    
                } finally {
    
                    // ============================================  
                    // GUARDAR POSICIÓN ANTES DE DESTRUIR  
                    // ============================================  
                    videoPlayer?.let {
                        savedPosition = it.getCurrentPosition()
                        FileLogger.log(
                            this@GLWallpaperService,
                            "Video guardado en posición: ${savedPosition}ms"
                        )
                    }
    
                    videoPlayer?.release()
                    videoPlayer = null
    
                    renderer?.release()
                    renderer = null
    
                    FileLogger.log(
                        this@GLWallpaperService,
                        "RenderThread finalizado"
                    )
                }
            }
    
            renderThread!!.start()
        }
        
        private fun stopRendering() {
    
            running = false
    
            renderThread?.interrupt()
    
            try {
                renderThread?.join(1000)
            } catch (_: Exception) {
            }
    
            renderThread = null
    
            FileLogger.log(
                this@GLWallpaperService,
                "RenderThread detenido"
            )
        }
    }
}