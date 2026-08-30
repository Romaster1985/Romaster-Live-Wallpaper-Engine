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

package com.romaster.livewallengine.video

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.romaster.livewallengine.debug.FileLogger
import com.romaster.livewallengine.storage.AppDirectories
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Genera un MP4 con los frames de [fromMs, toMs] en orden inverso.
 *
 * Salida siempre en videos/ con nombres fijos:
 * - overlay_video_reverse_locked.mp4
 * - overlay_video_reverse_unlocked.mp4
 */
object ReverseVideoProcessor {

    const val MAX_INTERVAL_MS = 8_000
    private const val TARGET_FPS = 20
    private const val MAX_WIDTH = 720
    private const val BIT_RATE = 2_500_000
    private const val I_FRAME_INTERVAL = 1
    private const val MIME = "video/avc"
    private const val TIMEOUT_US = 10_000L

    /**
     * @param locked true → overlay_video_reverse_locked.mp4
     */
    fun process(
        context: Context,
        sourceFile: File,
        fromMs: Int,
        toMs: Int,
        locked: Boolean,
        onProgress: (Float) -> Unit
    ): File {
        require(sourceFile.exists()) {
            "No existe el video fuente: ${sourceFile.absolutePath}"
        }

        val start = fromMs.coerceAtLeast(0)
        val end = toMs.coerceAtLeast(start + 100)
        val interval = end - start

        require(interval <= MAX_INTERVAL_MS) {
            "El intervalo de ping-pong no puede superar ${MAX_INTERVAL_MS / 1000} s"
        }

        val outputFileName = VideoStorage.reverseFileName(locked)
        val output = VideoStorage.getVideoFile(context, outputFileName)
        if (output.exists()) output.delete()

        val frameDir = File(
            AppDirectories.videos(context),
            "frames_tmp_${System.currentTimeMillis()}"
        )
        frameDir.mkdirs()

        try {
            val frames = extractFrames(
                context,
                sourceFile,
                start,
                end,
                frameDir,
                onProgress
            )

            if (frames.isEmpty()) {
                throw IllegalStateException("No se pudieron extraer frames del intervalo")
            }

            encodeReverse(frames, output, onProgress)

            FileLogger.log(
                context,
                "ReverseVideoProcessor OK -> ${output.absolutePath} (${frames.size} frames)"
            )

            return output
        } finally {
            frameDir.deleteRecursively()
        }
    }

    fun deleteIfExists(context: Context, locked: Boolean) {
        val file = VideoStorage.getReverseFile(context, locked)
        if (file.exists()) file.delete()
    }

    /** Compatibilidad: borra por nombre en videos/ o pingpong/ viejo. */
    fun deleteIfExists(context: Context, fileName: String?) {
        if (fileName.isNullOrBlank()) return
        val inVideos = VideoStorage.getVideoFile(context, fileName)
        if (inVideos.exists()) inVideos.delete()
        val inPing = File(context.filesDir, "pingpong/$fileName")
        if (inPing.exists()) inPing.delete()
    }

    fun clearAll(context: Context) {
        VideoStorage.clearReverseClips(context)
        FileLogger.log(context, "ReverseVideoProcessor.clearAll()")
    }

    private fun extractFrames(
        context: Context,
        sourceFile: File,
        fromMs: Int,
        toMs: Int,
        frameDir: File,
        onProgress: (Float) -> Unit
    ): List<File> {
        val retriever = MediaMetadataRetriever()
        val files = mutableListOf<File>()

        try {
            retriever.setDataSource(sourceFile.absolutePath)

            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val end = min(toMs.toLong(), durationMs)
            val start = min(fromMs.toLong(), end)
            val interval = max(1L, end - start)

            val frameCount = max(
                2,
                ((interval / 1000.0) * TARGET_FPS).roundToInt()
            )

            for (i in 0 until frameCount) {
                val t = start + (interval * i / (frameCount - 1).coerceAtLeast(1))
                val bmp = retriever.getFrameAtTime(
                    t * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                ) ?: continue

                val scaled = scaleBitmap(bmp)
                if (scaled !== bmp) bmp.recycle()

                val out = File(frameDir, "f_${i.toString().padStart(5, '0')}.jpg")
                out.outputStream().use { os ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 90, os)
                }
                scaled.recycle()
                files.add(out)

                onProgress((i + 1).toFloat() / frameCount * 0.6f)
            }
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        return files
    }

    private fun scaleBitmap(src: Bitmap): Bitmap {
        if (src.width <= MAX_WIDTH) return src
        val ratio = MAX_WIDTH.toFloat() / src.width
        val h = (src.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, MAX_WIDTH, h, true)
    }

    private fun encodeReverse(
        frames: List<File>,
        output: File,
        onProgress: (Float) -> Unit
    ) {
        if (frames.isEmpty()) return

        val first = android.graphics.BitmapFactory.decodeFile(frames[0].absolutePath)
            ?: throw IllegalStateException("No se pudo leer el primer frame")
        val width = first.width
        val height = first.height
        first.recycle()

        val format = MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, TARGET_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
        }

        val encoder = MediaCodec.createEncoderByType(MIME)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(
            output.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        var trackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val frameDurationUs = 1_000_000L / TARGET_FPS
        var inputIndex = 0
        val total = frames.size
        val ordered = frames.asReversed()
        var inputDone = false
        var outputDone = false
        var presentationTimeUs = 0L

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        if (inputIndex >= total) {
                            encoder.queueInputBuffer(
                                inIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val bmp = android.graphics.BitmapFactory.decodeFile(
                                ordered[inputIndex].absolutePath
                            )
                            val inputBuffer = encoder.getInputBuffer(inIndex)!!
                            inputBuffer.clear()
                            val yuv = bitmapToNv12(bmp, width, height)
                            bmp.recycle()
                            inputBuffer.put(yuv)
                            encoder.queueInputBuffer(
                                inIndex, 0, yuv.size, presentationTimeUs, 0
                            )
                            presentationTimeUs += frameDurationUs
                            inputIndex++
                            onProgress(0.6f + inputIndex.toFloat() / total * 0.4f)
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (muxerStarted) continue
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val encoded = encoder.getOutputBuffer(outIndex)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            try { encoder.stop(); encoder.release() } catch (_: Exception) {}
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {}
        }
    }

    private fun bitmapToNv12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uvIndex = width * height
        for (j in 0 until height) {
            for (i in 0 until width) {
                val c = argb[j * width + i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
