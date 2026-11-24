// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

import java.io.File

/**
 * Stores what Backrow derives from one version of YouTube's player JS, so a new process does not
 * download (3 MB) and preprocess it again: the signature timestamp and the solver's
 * preprocessed player. Keyed by player id; only the most recent [keep] players are kept.
 */
internal interface PlayerJsCache {
    fun get(playerId: String): CachedPlayer?

    fun put(playerId: String, player: CachedPlayer)
}

internal class CachedPlayer(val signatureTimestamp: Int, val preprocessedPlayer: String)

internal class FilePlayerJsCache(private val directory: File, private val keep: Int = 2) : PlayerJsCache {
    override fun get(playerId: String): CachedPlayer? {
        val sts = file(playerId, "sts").takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() ?: return null
        val pp = file(playerId, "js").takeIf { it.isFile }?.readText() ?: return null
        return CachedPlayer(sts, pp)
    }

    override fun put(playerId: String, player: CachedPlayer) {
        directory.mkdirs()
        // Write the large file first, the small one last: get() only returns complete pairs.
        file(playerId, "js").writeText(player.preprocessedPlayer)
        file(playerId, "sts").writeText(player.signatureTimestamp.toString())
        prune()
    }

    private fun prune() {
        directory.listFiles { f -> f.name.endsWith(".sts") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(keep)
            ?.forEach { stale ->
                val id = stale.name.removeSuffix(".sts")
                stale.delete()
                file(id, "js").delete()
            }
    }

    private fun file(playerId: String, extension: String): File {
        require(SAFE_ID.matches(playerId)) { "unexpected player id" }
        return File(directory, "$playerId.$extension")
    }

    private companion object {
        val SAFE_ID = Regex("[a-fA-F0-9]{8,}")
    }
}

internal class InMemoryPlayerJsCache : PlayerJsCache {
    private val entries = mutableMapOf<String, CachedPlayer>()

    override fun get(playerId: String): CachedPlayer? = synchronized(entries) { entries[playerId] }

    override fun put(playerId: String, player: CachedPlayer) {
        synchronized(entries) { entries[playerId] = player }
    }
}
