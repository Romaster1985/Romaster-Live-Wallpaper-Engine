package com.romaster.livewallengine.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.project.ProjectManager
import com.romaster.livewallengine.video.VideoStorage
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ProjectExporter {

    fun export(

        context: Context,
    
        destination: Uri,
    
        previewBitmap: Bitmap?
    
    ) {

        val output =
            context.contentResolver
                .openOutputStream(destination)
                ?: return

        ZipOutputStream(output).use { zip ->

            // 1) Config y preview primero (nunca deben faltar)
            try {
                writeProjectJson(zip)
            } catch (e: Exception) {
                FileLogger.log(context, "ProjectExporter project.json ERROR: ${e.message}")
            }

            try {
                writePreview(zip, previewBitmap)
            } catch (e: Exception) {
                FileLogger.log(context, "ProjectExporter preview.png ERROR: ${e.message}")
            }

            // 2) Recursos
            try {
                writeResources(context, zip)
            } catch (e: Exception) {
                FileLogger.log(context, "ProjectExporter resources ERROR: ${e.message}")
            }
        
        }
    }

    private fun writeProjectJson(

        zip: ZipOutputStream

    ) {

        val json =
            ProjectSerializer.encode(
                ProjectManager.getProject()
            )

        zip.putNextEntry(
            ZipEntry("project.json")
        )

        zip.write(
            json.toByteArray()
        )

        zip.closeEntry()
    }

    private fun writePreview(
        zip: ZipOutputStream,
        bitmap: Bitmap?
    ) {
    
        if (bitmap == null)
            return
    
        zip.putNextEntry(
            ZipEntry("preview.png")
        )
    
        bitmap.compress(
            Bitmap.CompressFormat.PNG,
            100,
            zip
        )
    
        zip.closeEntry()
    }
    
    private fun addFile(

        zip: ZipOutputStream,
    
        file: File,
    
        zipName: String,
    
        context: Context
    
    ): Boolean {
    
        if (!file.exists() || !file.isFile || file.length() <= 0L) {
            return false
        }

        return try {
            zip.putNextEntry(ZipEntry(zipName))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            FileLogger.log(
                context,
                "ProjectExporter OK: $zipName (${file.length()} bytes)"
            )
            true
        } catch (e: Exception) {
            FileLogger.log(
                context,
                "ProjectExporter FAIL: $zipName ${e.message}"
            )
            false
        }
    }
    
    private fun writeResources(

        context: Context,
    
        zip: ZipOutputStream
    
    ) {
    
        val project =
            ProjectManager.getProject()
    
        // Video principal
        project.wallpaperVideo?.let {
            addFile(
                zip,
                File(context.filesDir, "videos/$it"),
                "videos/$it",
                context
            )
        }
    
        // Video overlay
        project.overlayVideo?.let {
            addFile(
                zip,
                File(context.filesDir, "videos/$it"),
                "videos/$it",
                context
            )
        }

        // Clips de reversa (nombres fijos en videos/)
        addFile(
            zip,
            VideoStorage.getReverseLockedFile(context),
            "videos/${VideoStorage.OVERLAY_REVERSE_LOCKED}",
            context
        )
        addFile(
            zip,
            VideoStorage.getReverseUnlockedFile(context),
            "videos/${VideoStorage.OVERLAY_REVERSE_UNLOCKED}",
            context
        )
    
        // MP3 fondo
        project.layers.firstOrNull()?.soundPath?.let {
            addFile(
                zip,
                File(context.filesDir, "audio/$it"),
                "audio/$it",
                context
            )
        }
    
        // MP3 overlay
        project.overlay.soundPath?.let {
            addFile(
                zip,
                File(context.filesDir, "audio/$it"),
                "audio/$it",
                context
            )
        }
    
        // Fuente reloj
        project.clock.clockFont?.let {
            addFile(
                zip,
                File(context.filesDir, "fonts/$it"),
                "fonts/$it",
                context
            )
        }
    
        // Fuente fecha
        project.clock.dateFont?.let {
            addFile(
                zip,
                File(context.filesDir, "fonts/$it"),
                "fonts/$it",
                context
            )
        }
    }

}
