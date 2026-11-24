// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.player

/** What [BufferPolicy] decides for one device. */
data class BufferConfig(
    val tier: DeviceTier,
    /** ExoPlayer keeps at least this much media buffered once playing. */
    val minBufferMs: Int,
    /** ...and stops loading beyond this. */
    val maxBufferMs: Int,
    /** Media needed before playback starts; small, so first frame comes fast. */
    val bufferForPlaybackMs: Int,
    /** Media needed to resume after a stall. */
    val bufferForPlaybackAfterRebufferMs: Int,
    /** Hard cap on buffered bytes: the real memory limit on a 1 GB device. */
    val targetBufferBytes: Int,
    /** Highest video height offered to the player on this tier. */
    val maxVideoHeight: Int,
)

enum class DeviceTier { LOW, NORMAL }

/**
 * Buffer sizes from the device's memory (ADR-0006: runtime tuning from
 * `ActivityManager.memoryClass` / `isLowRamDevice`, here also total RAM).
 *
 * LOW is the 1 GB tier. The memory class alone does not find it: measured 2026-11-24, the 2 GB
 * API 36 Android TV emulator reports 192 MB but the 1 GB API 25 one reports 256 MB (its image
 * sets no heap growth limit; docs/DEVICE-QUIRKS.md). So a device is LOW if it says it is low-RAM,
 * has a memory class below 192 MB, or has less than 1.5 GiB of RAM. Real Fire TV values are
 * still to be recorded. A 1080p H.264 stream is
 * about 4–6 Mbit/s, so LOW's 16 MB holds roughly 20–30 s of 1080p video plus audio. NORMAL
 * devices get 30–60 s and up to 2160p.
 */
object BufferPolicy {
    /** Below this memory class (MB) a device is LOW. */
    const val NORMAL_MEMORY_CLASS_MB = 192

    /** Below this much RAM a device is LOW, whatever its memory class says. */
    const val NORMAL_TOTAL_RAM_BYTES = 1536L * 1024 * 1024

    fun forDevice(memoryClassMb: Int, isLowRamDevice: Boolean, totalRamBytes: Long): BufferConfig =
        if (isLowRamDevice || memoryClassMb < NORMAL_MEMORY_CLASS_MB || totalRamBytes < NORMAL_TOTAL_RAM_BYTES) {
            BufferConfig(
                tier = DeviceTier.LOW,
                minBufferMs = 15_000,
                maxBufferMs = 30_000,
                bufferForPlaybackMs = 1_000,
                bufferForPlaybackAfterRebufferMs = 2_500,
                targetBufferBytes = 16 * 1024 * 1024,
                maxVideoHeight = 1080,
            )
        } else {
            BufferConfig(
                tier = DeviceTier.NORMAL,
                minBufferMs = 30_000,
                maxBufferMs = 60_000,
                bufferForPlaybackMs = 1_000,
                bufferForPlaybackAfterRebufferMs = 2_500,
                targetBufferBytes = 64 * 1024 * 1024,
                maxVideoHeight = 2160,
            )
        }
}
