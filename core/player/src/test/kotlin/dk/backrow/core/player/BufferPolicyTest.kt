// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BufferPolicyTest {
    private val oneGb = 1_030_384L * 1024 // MemTotal of the 1 GB API 25 emulator
    private val twoGb = 2_020_264L * 1024 // MemTotal of the 2 GB API 36 emulator

    @Test
    fun `1 GB devices get the low tier, capped at 1080p`() {
        val c = BufferPolicy.forDevice(memoryClassMb = 128, isLowRamDevice = false, totalRamBytes = oneGb)

        assertEquals(DeviceTier.LOW, c.tier)
        assertEquals(1080, c.maxVideoHeight)
        assertTrue(c.targetBufferBytes <= 16 * 1024 * 1024)
    }

    @Test
    fun `isLowRamDevice forces the low tier whatever the memory class`() {
        assertEquals(DeviceTier.LOW, BufferPolicy.forDevice(memoryClassMb = 512, isLowRamDevice = true, totalRamBytes = twoGb).tier)
    }

    @Test
    fun `1 GB of RAM is LOW even with a large memory class, as on the API 25 emulator`() {
        assertEquals(DeviceTier.LOW, BufferPolicy.forDevice(memoryClassMb = 256, isLowRamDevice = false, totalRamBytes = oneGb).tier)
    }

    @Test
    fun `2 GB devices (memory class 192, as the API 36 emulator) get the normal tier and 2160p`() {
        val c = BufferPolicy.forDevice(memoryClassMb = 192, isLowRamDevice = false, totalRamBytes = twoGb)

        assertEquals(DeviceTier.NORMAL, c.tier)
        assertEquals(2160, c.maxVideoHeight)
    }

    @Test
    fun `durations are ordered as Media3 requires`() {
        for (c in listOf(BufferPolicy.forDevice(96, false, oneGb), BufferPolicy.forDevice(512, false, twoGb))) {
            assertTrue(c.bufferForPlaybackMs <= c.minBufferMs)
            assertTrue(c.bufferForPlaybackAfterRebufferMs <= c.minBufferMs)
            assertTrue(c.minBufferMs <= c.maxBufferMs)
        }
    }
}
