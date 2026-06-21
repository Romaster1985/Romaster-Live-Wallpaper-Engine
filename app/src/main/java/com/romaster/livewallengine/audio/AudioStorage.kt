package com.romaster.livewallengine.audio

import android.content.Context
import android.net.Uri
import java.io.File
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever

object AudioStorage {

    const val BG_SOUND =
        "background_sound"

    const val OVERLAY_SOUND =
        "overlay_sound"

    private fun getAudioDirectory(
        context: Context
    ): File {

        val dir =
            File(
                context.filesDir,
                "audio"
            )

        if (!dir.exists()) {
            dir.mkdirs()
        }

        return dir
    }

    fun getAudioFile(

        context: Context,

        fileName: String

    ): File {

        return File(
            getAudioDirectory(context),
            fileName
        )

    }

    fun copyAudio(

        context: Context,

        sourceUri: Uri,

        fileName: String

    ): File {

        val destination =
            getAudioFile(
                context,
                fileName
            )

        context.contentResolver
            .openInputStream(sourceUri)
            ?.use { input ->

                destination
                    .outputStream()
                    .use { output ->

                        input.copyTo(output)

                    }

            }

        return destination

    }

    fun deleteAudio(

        context: Context,

        fileName: String

    ) {

        getAudioFile(
            context,
            fileName
        ).delete()

    }
    
    fun getDisplayName(

        context: Context,
    
        uri: Uri
    
    ): String {
    
        var result: String? = null
    
        context.contentResolver.query(
    
            uri,
    
            null,
    
            null,
    
            null,
    
            null
    
        )?.use { cursor ->
    
            val index =
    
                cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )
    
            if (
    
                index >= 0 &&
                cursor.moveToFirst()
    
            ) {
    
                result =
                    cursor.getString(index)
    
            }
    
        }
    
        return result
            ?: uri.lastPathSegment
            ?: "audio"
    
    }
    
    fun getDuration(

        context: Context,
    
        uri: Uri
    
    ): Long {
    
        val retriever =
            MediaMetadataRetriever()
    
        return try {
    
            retriever.setDataSource(
                context,
                uri
            )
    
            retriever.extractMetadata(
    
                MediaMetadataRetriever
                    .METADATA_KEY_DURATION
    
            )?.toLong()
    
                ?: 0L
    
        } finally {
    
            retriever.release()
    
        }
    
    }
    
    fun formatDuration(

        durationMs: Long
    
    ): String {
    
        val minutes =
            durationMs / 60000
    
        val seconds =
            (durationMs % 60000) / 1000
    
        val millis =
            durationMs % 1000
    
        return String.format(
    
            "%02d:%02d.%03d",
    
            minutes,
    
            seconds,
    
            millis
    
        )
    
    }
    
    fun clear(context: Context) {

        File(
            context.filesDir,
            "audio"
        ).deleteRecursively()
    
    }

}