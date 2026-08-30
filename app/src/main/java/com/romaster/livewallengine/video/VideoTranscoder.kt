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
import android.net.Uri
import com.romaster.livewallengine.debug.FileLogger
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Re-encodea un video a MP4 H.264 “ligero” (mismo estilo que GifToMp4Converter)
 * para bucles más suaves con MediaPlayer (seek al inicio menos brusco).
 */
object VideoTranscoder {

    private const val TARGET_FPS = 15
    private const val MAX_WIDTH = 720
    private const val BIT_RATE = 2_500_000
    private const val I_FRAME_INTERVAL = 1
    private const val MIME = "video/avc"
    private const val TIMEOUT_US = 10_000L
    /** Tope de seguridad para no saturar memoria/tiempo en el editor. */
    private const val MAX_DURATION_MS = 90_000

    /**
     * @param onProgress 0f..1f
     * @return [outputFile] generado
     */
    fun transcode(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        onProgress: (Float) -> Unit
    ): File {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val workDir = File(
            outputFile.parentFile,
            "transcode_tmp_${System.currentTimeMillis()}"
        )
        workDir.mkdirs()

        try {
            val frames = extractFrames(context, sourceUri, workDir, onProgress)
            if (frames.isEmpty()) {
                throw IllegalStateException("No se pudieron extraer frames del video")
            }

            val first = android.graphics.BitmapFactory.decodeFile(frames[0].absolutePath)
                ?: throw IllegalStateException("Frame inválido")
            val width = first.width
            val height = first.height
            first.recycle()

            encodeFrames(frames, outputFile, width, height, onProgress)

            FileLogger.log(
                context,
                "VideoTranscoder OK -> ${outputFile.absolutePath} " +
                    "(${frames.size} frames, ${width}x${height})"
            )
            return outputFile
        } finally {
            try {
                workDir.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }

    private fun extractFrames(
        context: Context,
        sourceUri: Uri,
        frameDir: File,
        onProgress: (Float) -> Unit
    ): List<File> {
        val retriever = MediaMetadataRetriever()
        val files = mutableListOf<File>()

        try {
            context.contentResolver.openFileDescriptor(sourceUri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            } ?: run {
                // Fallback: copiar a temp y abrir por path
                val tmp = File(frameDir, "source_copy.bin")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalArgumentException("No se pudo abrir el video")
                retriever.setDataSource(tmp.absolutePath)
            }

            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            if (durationMs <= 0L) {
                throw IllegalArgumentException("Duración de video inválida")
            }

            val clamped = min(durationMs, MAX_DURATION_MS.toLong())
            val frameCount = max(
                2,
                ((clamped / 1000.0) * TARGET_FPS).roundToInt()
            )

            for (i in 0 until frameCount) {
                val tMs = if (frameCount == 1) 0L
                else (clamped * i / (frameCount - 1))

                val bmp = retriever.getFrameAtTime(
                    tMs * 1000L,
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

                onProgress(0.55f * (i + 1).toFloat() / frameCount)
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        return files
    }

    private fun scaleBitmap(src: Bitmap): Bitmap {
        var w = src.width
        var h = src.height
        if (w <= 0 || h <= 0) return src

        if (w > MAX_WIDTH) {
            val scale = MAX_WIDTH.toFloat() / w
            w = MAX_WIDTH
            h = max(1, (h * scale).roundToInt())
        }
        // Encoder H.264 exige dimensiones pares
        if (w % 2 != 0) w -= 1
        if (h % 2 != 0) h -= 1
        w = max(2, w)
        h = max(2, h)

        return if (w == src.width && h == src.height) src
        else Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun encodeFrames(
        frames: List<File>,
        output: File,
        width: Int,
        height: Int,
        onProgress: (Float) -> Unit
    ) {
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
                                frames[inputIndex].absolutePath
                            ) ?: throw IllegalStateException("No se pudo leer frame $inputIndex")
                            val yuv = bitmapToNv12(bmp, width, height)
                            bmp.recycle()
                            val inputBuffer = encoder.getInputBuffer(inIndex)!!
                            inputBuffer.clear()
                            inputBuffer.put(yuv)
                            encoder.queueInputBuffer(
                                inIndex, 0, yuv.size, presentationTimeUs, 0
                            )
                            presentationTimeUs += frameDurationUs
                            inputIndex++
                            onProgress(0.55f + 0.45f * inputIndex.toFloat() / total)
                        }
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
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
                        val eos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (eos) outputDone = true
                    }
                }
            }
        } finally {
            try {
                encoder.stop()
                encoder.release()
            } catch (_: Exception) {
            }
            try {
                if (muxerStarted) muxer.stop()
                muxer.release()
            } catch (_: Exception) {
            }
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
