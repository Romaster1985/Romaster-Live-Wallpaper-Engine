package com.romaster.livewallengine.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.romaster.livewallengine.project.ProjectManager
import java.io.ByteArrayOutputStream
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

            writeProjectJson(zip)
        
            writePreview(
                zip,
                previewBitmap
            )
        
            writeResources(
                context,
                zip
            )
        
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
    
        val stream =
            ByteArrayOutputStream()
    
        bitmap.compress(
            Bitmap.CompressFormat.PNG,
            100,
            stream
        )
    
        zip.putNextEntry(
            ZipEntry("preview.png")
        )
    
        zip.write(
            stream.toByteArray()
        )
    
        zip.closeEntry()
    
        bitmap.recycle()
    
    }
    
    private fun addFile(

        zip: ZipOutputStream,
    
        file: java.io.File,
    
        zipName: String
    
    ) {
    
        if (!file.exists())
            return
    
        zip.putNextEntry(
            ZipEntry(zipName)
        )
    
        file.inputStream().use {
    
            it.copyTo(zip)
    
        }
    
        zip.closeEntry()
    
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
                java.io.File(
                    context.filesDir,
                    "videos/$it"
                ),
                "videos/$it"
            )
    
        }
    
        // Video overlay
    
        project.overlayVideo?.let {
    
            addFile(
                zip,
                java.io.File(
                    context.filesDir,
                    "videos/$it"
                ),
                "videos/$it"
            )
    
        }
    
        // MP3 fondo
    
        project.layers.firstOrNull()
            ?.soundPath?.let {
    
            addFile(
                zip,
                java.io.File(
                    context.filesDir,
                    "audio/$it"
                ),
                "audio/$it"
            )
    
        }
    
        // MP3 overlay
    
        project.overlay.soundPath?.let {
    
            addFile(
                zip,
                java.io.File(
                    context.filesDir,
                    "audio/$it"
                ),
                "audio/$it"
            )
    
        }
    
        // Fuente reloj
    
        project.clock.clockFont?.let {
    
            addFile(
    
                zip,
    
                java.io.File(
                    context.filesDir,
                    "fonts/$it"
                ),
    
                "fonts/$it"
    
            )
    
        }
    
        // Fuente fecha
    
        project.clock.dateFont?.let {
    
            addFile(
    
                zip,
    
                java.io.File(
                    context.filesDir,
                    "fonts/$it"
                ),
    
                "fonts/$it"
    
            )
    
        }
    
    }

}