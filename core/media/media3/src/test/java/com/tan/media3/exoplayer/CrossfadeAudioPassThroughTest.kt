package com.tan.media3.exoplayer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.tan.media3.audio.CrossfadeFilterAudioProcessor
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

@UnstableApi
class CrossfadeAudioPassThroughTest {
    @Test fun disabledFilterKeepsEveryPcmByteAndConsumesOnlyTheSelectedRange() {
        val processor = CrossfadeFilterAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val bytes = ByteArray(19_200) { (it % 251).toByte() }
        val input = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip(); position(8); limit(bytes.size - 8) }
        processor.queueInput(input)
        assertEquals(input.limit(), input.position())
        val actual = ByteArray(bytes.size - 16)
        processor.output.get(actual)
        assertArrayEquals(bytes.copyOfRange(8, bytes.size - 8), actual)
    }

    @Test fun reuseOfOutputStorageAcrossAudioChunksDoesNotDropOrMixSamples() {
        val processor = CrossfadeFilterAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        repeat(100) { chunk ->
            val bytes = ByteArray(4096) { (chunk + it).toByte() }
            processor.queueInput(ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() })
            val actual = ByteArray(bytes.size)
            processor.output.get(actual)
            assertArrayEquals(bytes, actual)
        }
    }
}
