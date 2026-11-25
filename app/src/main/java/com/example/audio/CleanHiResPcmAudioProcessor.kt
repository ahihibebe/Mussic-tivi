package com.example.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Normalizes 24-bit, 32-bit, and float PCM audio (commonly decoded from 24-bit/48kHz+ FLAC/WAV)
 * into clean 16-bit PCM with rounding so Android TV AudioTrack/HDMI DACs that mis-handle
 * packed 24-bit or float PCM do not produce static hiss ("bị sôi").
 */
@OptIn(UnstableApi::class)
class CleanHiResPcmAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var antiHissEnabled: Boolean = true

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!antiHissEnabled) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT -> AudioProcessor.AudioFormat(
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount,
                C.ENCODING_PCM_16BIT
            )
            C.ENCODING_PCM_16BIT -> AudioProcessor.AudioFormat.NOT_SET
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining <= 0) return

        val encoding = inputAudioFormat.encoding
        val bytesPerInputSample = when (encoding) {
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT -> 4
            else -> return
        }

        val sampleCount = remaining / bytesPerInputSample
        if (sampleCount <= 0) return

        val outBuffer = replaceOutputBuffer(sampleCount * 2)
        when (encoding) {
            C.ENCODING_PCM_24BIT -> {
                for (i in 0 until sampleCount) {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt()
                    val sample24 = b0 or (b1 shl 8) or (b2 shl 16)
                    val rounded16 = ((sample24 + 128) shr 8).coerceIn(-32768, 32767)
                    outBuffer.putShort(rounded16.toShort())
                }
            }
            C.ENCODING_PCM_24BIT_BIG_ENDIAN -> {
                for (i in 0 until sampleCount) {
                    val b0 = inputBuffer.get().toInt()
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt() and 0xFF
                    val sample24 = (b0 shl 16) or (b1 shl 8) or b2
                    val rounded16 = ((sample24 + 128) shr 8).coerceIn(-32768, 32767)
                    outBuffer.putShort(rounded16.toShort())
                }
            }
            C.ENCODING_PCM_32BIT -> {
                for (i in 0 until sampleCount) {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt() and 0xFF
                    val b3 = inputBuffer.get().toInt()
                    val sample32 = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                    val sample16 = ((sample32 + 32768) shr 16).coerceIn(-32768, 32767)
                    outBuffer.putShort(sample16.toShort())
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                for (i in 0 until sampleCount) {
                    val f = inputBuffer.float.coerceIn(-1.0f, 1.0f)
                    val sample16 = (f * 32767f).toInt().coerceIn(-32768, 32767)
                    outBuffer.putShort(sample16.toShort())
                }
            }
        }
        outBuffer.flip()
    }
}
