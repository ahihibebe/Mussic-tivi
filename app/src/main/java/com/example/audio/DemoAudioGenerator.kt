package com.example.audio

import android.content.Context
import com.example.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object DemoAudioGenerator {

    suspend fun getOrCreateDemoTracks(context: Context): List<AudioTrack> = withContext(Dispatchers.IO) {
        val demoDir = File(context.filesDir, "demo_music").apply { mkdirs() }
        val tracks = mutableListOf<AudioTrack>()

        val demoSpecs = listOf(
            DemoSpec(
                id = "demo_1",
                filename = "aurora_borealis.wav",
                title = "Aurora Borealis (Hào Quang Bắc Cực)",
                artist = "TV Soundscape Lab",
                album = "High-Res Acoustic Sessions",
                format = "FLAC",
                bitrate = "24-bit 96kHz",
                sampleRate = "96.0 kHz",
                durationSeconds = 64,
                baseFreq = 220.0,
                scale = listOf(220.0, 277.18, 329.63, 440.0, 554.37, 659.25),
                style = 0
            ),
            DemoSpec(
                id = "demo_2",
                filename = "midnight_lofi_vibes.wav",
                title = "Midnight Chill Lo-Fi (Đêm Thành Phố)",
                artist = "Lofi Beats Vietnam",
                album = "TV Relaxing Nights Vol.1",
                format = "MP3",
                bitrate = "320 kbps",
                sampleRate = "44.1 kHz",
                durationSeconds = 72,
                baseFreq = 174.61, // F
                scale = listOf(174.61, 220.0, 261.63, 329.63, 392.0, 523.25),
                style = 1
            ),
            DemoSpec(
                id = "demo_3",
                filename = "acoustic_prelude.wav",
                title = "Acoustic Piano & Strings (Giai Điệu Bình Yên)",
                artist = "Acoustic Ensemble",
                album = "Audiophile Master Collection",
                format = "WAV",
                bitrate = "1411 kbps",
                sampleRate = "48.0 kHz",
                durationSeconds = 60,
                baseFreq = 261.63, // C
                scale = listOf(261.63, 293.66, 329.63, 392.0, 440.0, 523.25),
                style = 2
            ),
            DemoSpec(
                id = "demo_4",
                filename = "cyber_pulse_beat.wav",
                title = "Cyber Pulse Neon (Nhịp Điệu Điện Tử)",
                artist = "Synthwave Studio",
                album = "Future Horizon 2026",
                format = "AAC",
                bitrate = "256 kbps",
                sampleRate = "44.1 kHz",
                durationSeconds = 68,
                baseFreq = 130.81, // C3
                scale = listOf(130.81, 155.56, 174.61, 196.0, 233.08, 261.63),
                style = 3
            ),
            DemoSpec(
                id = "demo_5",
                filename = "ocean_ambient_breeze.wav",
                title = "Ocean Breeze & Rain (Âm Thanh Thiên Nhiên)",
                artist = "Nature Meditations",
                album = "Deep Sleep on TV",
                format = "OGG",
                bitrate = "192 kbps",
                sampleRate = "48.0 kHz",
                durationSeconds = 75,
                baseFreq = 146.83, // D
                scale = listOf(146.83, 164.81, 196.0, 220.0, 261.63, 293.66),
                style = 4
            )
        )

        for (spec in demoSpecs) {
            val file = File(demoDir, spec.filename)
            if (!file.exists() || file.length() < 1000) {
                generateMelodicWav(file, spec)
            }

            tracks.add(
                AudioTrack(
                    id = spec.id,
                    title = spec.title,
                    artist = spec.artist,
                    album = spec.album,
                    durationMs = spec.durationSeconds * 1000L,
                    filePath = file.absolutePath,
                    format = spec.format,
                    bitrate = spec.bitrate,
                    sampleRate = spec.sampleRate,
                    fileSizeBytes = file.length(),
                    parentDirectory = demoDir.absolutePath,
                    isFavorite = (spec.id == "demo_1" || spec.id == "demo_2"),
                    isDemo = true
                )
            )
        }

        tracks
    }

    private data class DemoSpec(
        val id: String,
        val filename: String,
        val title: String,
        val artist: String,
        val album: String,
        val format: String,
        val bitrate: String,
        val sampleRate: String,
        val durationSeconds: Int,
        val baseFreq: Double,
        val scale: List<Double>,
        val style: Int
    )

    private fun generateMelodicWav(outputFile: File, spec: DemoSpec) {
        val sampleRate = 22050 // Keep efficient and fast to generate
        val numChannels = 1
        val bitsPerSample = 16
        val totalSamples = sampleRate * spec.durationSeconds
        val dataSize = totalSamples * numChannels * (bitsPerSample / 8)

        FileOutputStream(outputFile).use { fos ->
            // Write standard 44-byte RIFF/WAVE header
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // Subchunk1Size for PCM
            header.putShort(1.toShort()) // PCM format = 1
            header.putShort(numChannels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * numChannels * (bitsPerSample / 8)) // byte rate
            header.putShort((numChannels * (bitsPerSample / 8)).toShort()) // block align
            header.putShort(bitsPerSample.toShort())
            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())

            // Generate music buffer in chunks of 4096 samples
            val chunkSize = 4096
            val chunkBuffer = ByteBuffer.allocate(chunkSize * 2).order(ByteOrder.LITTLE_ENDIAN)

            var sampleIndex = 0
            val beatIntervalSamples = (sampleRate * 0.4).toInt() // tempo

            while (sampleIndex < totalSamples) {
                chunkBuffer.clear()
                val remaining = totalSamples - sampleIndex
                val currentChunk = minOf(chunkSize, remaining)

                for (i in 0 until currentChunk) {
                    val currentSample = sampleIndex + i
                    val timeSec = currentSample.toDouble() / sampleRate
                    val beatIndex = (currentSample / beatIntervalSamples)

                    // Chord tone
                    val chordNote = spec.scale[beatIndex % spec.scale.size]
                    val subChordNote = spec.scale[(beatIndex + 2) % spec.scale.size]
                    val bassNote = spec.baseFreq / 2.0

                    val beatPhase = (currentSample % beatIntervalSamples).toDouble() / beatIntervalSamples
                    val envelope = (1.0 - beatPhase) // gentle decay per note

                    // Wave synthesis based on style
                    val sampleValue: Double = when (spec.style) {
                        0 -> { // Ambient synthwave
                            val pad = sin(2.0 * Math.PI * chordNote * timeSec) * 0.4 +
                                    sin(2.0 * Math.PI * subChordNote * 1.002 * timeSec) * 0.3 +
                                    sin(2.0 * Math.PI * bassNote * timeSec) * 0.3
                            pad * (0.6 + 0.4 * sin(2.0 * Math.PI * 0.25 * timeSec))
                        }
                        1 -> { // Lofi chill with beat pulse
                            val melody = sin(2.0 * Math.PI * chordNote * timeSec) * envelope * 0.4
                            val bass = sin(2.0 * Math.PI * bassNote * timeSec) * 0.4
                            val drumTick = if (beatPhase < 0.1) sin(2.0 * Math.PI * 80.0 * beatPhase * 10) * (1.0 - beatPhase * 10) * 0.3 else 0.0
                            melody + bass + drumTick
                        }
                        2 -> { // Acoustic piano
                            val piano = (sin(2.0 * Math.PI * chordNote * timeSec) +
                                    0.4 * sin(2.0 * Math.PI * chordNote * 2 * timeSec) +
                                    0.2 * sin(2.0 * Math.PI * chordNote * 3 * timeSec)) * envelope * 0.5
                            val lowBass = sin(2.0 * Math.PI * bassNote * timeSec) * 0.3
                            piano + lowBass
                        }
                        3 -> { // Cyber pulse
                            val square = if (sin(2.0 * Math.PI * chordNote * timeSec) > 0) 0.3 else -0.3
                            val subBass = sin(2.0 * Math.PI * (bassNote * 0.75) * timeSec) * 0.5
                            val hihat = if ((currentSample % (beatIntervalSamples / 2)) < 200) (Math.random() - 0.5) * 0.2 else 0.0
                            (square * envelope * 0.4) + subBass + hihat
                        }
                        else -> { // Ocean ambient
                            val ocean = (sin(2.0 * Math.PI * chordNote * timeSec) * 0.3 +
                                    sin(2.0 * Math.PI * (chordNote * 1.5) * timeSec) * 0.2) *
                                    (0.5 + 0.5 * sin(2.0 * Math.PI * 0.1 * timeSec))
                            ocean + (Math.random() - 0.5) * 0.05
                        }
                    }

                    // Soft clip and scale to 16-bit PCM (-32768 to 32767)
                    val clamped = sampleValue.coerceIn(-0.95, 0.95)
                    val pcm = (clamped * 32000.0).toInt().toShort()
                    chunkBuffer.putShort(pcm)
                }

                fos.write(chunkBuffer.array(), 0, currentChunk * 2)
                sampleIndex += currentChunk
            }
        }
    }
}
