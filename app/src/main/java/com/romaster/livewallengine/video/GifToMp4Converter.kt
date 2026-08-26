package com.romaster.livewallengine.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.romaster.livewallengine.debug.FileLogger
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Convierte un GIF animado a MP4 (H.264) para el pipeline de MediaPlayer.
 * Salida con dimensiones pares (requisito del encoder).
 */
object GifToMp4Converter {

    private const val TARGET_FPS = 15
    private const val MAX_WIDTH = 720
    private const val BIT_RATE = 2_000_000
    private const val I_FRAME_INTERVAL = 1
    private const val MIME = "video/avc"
    private const val TIMEOUT_US = 10_000L
    private const val MAX_DURATION_MS = 30_000

    /**
     * @param transparencyFillColor color para rellenar la transparencia del GIF
     *        (MP4 no tiene alpha; sirve para chroma key después).
     * @param onProgress 0f..1f
     * @return archivo MP4 generado
     */
    fun convert(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        transparencyFillColor: Int = Color.BLACK,
        onProgress: (Float) -> Unit
    ): File {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val movie = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            Movie.decodeStream(input)
        } ?: throw IllegalArgumentException("No se pudo leer el GIF")

        if (movie.width() <= 0 || movie.height() <= 0) {
            throw IllegalArgumentException("GIF inválido (sin tamaño)")
        }

        val durationMs = movie.duration().takeIf { it > 0 } ?: 1000
        val clampedDuration = min(durationMs, MAX_DURATION_MS)
        val frameCount = max(
            2,
            ((clampedDuration / 1000.0) * TARGET_FPS).roundToInt()
        )

        val srcW = movie.width()
        val srcH = movie.height()
        val scale = if (srcW > MAX_WIDTH) MAX_WIDTH.toFloat() / srcW else 1f
        var outW = (srcW * scale).roundToInt().coerceAtLeast(2)
        var outH = (srcH * scale).roundToInt().coerceAtLeast(2)
        // Encoder H.264: dimensiones pares
        if (outW % 2 != 0) outW -= 1
        if (outH % 2 != 0) outH -= 1

        FileLogger.log(
            context,
            "GifToMp4: ${srcW}x${srcH} -> ${outW}x${outH}, " +
                "dur=${clampedDuration}ms, frames=$frameCount"
        )

        val frames = ArrayList<Bitmap>(frameCount)
        try {
            for (i in 0 until frameCount) {
                val t = ((clampedDuration.toLong() * i) / (frameCount - 1).coerceAtLeast(1))
                    .toInt()
                    .coerceIn(0, clampedDuration)
                movie.setTime(t)
                val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                // Fondo sólido: la transparencia del GIF queda de este color
                // (el MP4 no soporta alpha; así se puede usar chroma key después).
                canvas.drawColor(transparencyFillColor or 0xFF000000.toInt())
                canvas.save()
                canvas.scale(outW.toFloat() / srcW, outH.toFloat() / srcH)
                movie.draw(canvas, 0f, 0f)
                canvas.restore()
                frames.add(bmp)
                onProgress((i + 1).toFloat() / frameCount * 0.55f)
            }

            encodeFrames(frames, outputFile, outW, outH) { p ->
                onProgress(0.55f + p * 0.45f)
            }

            FileLogger.log(
                context,
                "GifToMp4 OK -> ${outputFile.absolutePath} (${outputFile.length()} bytes)"
            )
            return outputFile
        } finally {
            frames.forEach { it.recycle() }
        }
    }

    private fun encodeFrames(
        frames: List<Bitmap>,
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
                            val yuv = bitmapToNv12(frames[inputIndex], width, height)
                            val inputBuffer = encoder.getInputBuffer(inIndex)!!
                            inputBuffer.clear()
                            inputBuffer.put(yuv)
                            encoder.queueInputBuffer(
                                inIndex, 0, yuv.size, presentationTimeUs, 0
                            )
                            presentationTimeUs += frameDurationUs
                            inputIndex++
                            onProgress(inputIndex.toFloat() / total)
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
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
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
