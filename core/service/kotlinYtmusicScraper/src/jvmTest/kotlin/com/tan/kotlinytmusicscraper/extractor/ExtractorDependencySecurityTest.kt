package com.tan.kotlinytmusicscraper.extractor

import com.squareup.wire.ProtoAdapter
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExtractorDependencySecurityTest {
    @Test
    fun oversizedProtobufStringFailsAsMalformedInput() {
        // GHSA-9rm7-3qhh-h2mc: a positive Int.MAX_VALUE length must not
        // overflow the reader's cursor and escape as an unchecked crash.
        val bytes = byteArrayOf(0x0a, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x07)
        assertFailsWith<IOException> { ProtoAdapter.STRING_VALUE.decode(bytes) }
    }

    @Test
    fun validProtobufStringStillDecodes() {
        val bytes = byteArrayOf(0x0a, 0x04, 0x73, 0x61, 0x66, 0x65)
        assertEquals("safe", ProtoAdapter.STRING_VALUE.decode(bytes))
    }
}
