// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.core.innertube

/**
 * Who Backrow presents itself as when it talks to InnerTube (ADR-0005 §2: client identity is data).
 *
 * This is the file that breaks most often. Change a profile or the chain here, add or update the
 * fixture that proves it, and add a dated entry to docs/INNERTUBE-CHANGELOG.md.
 *
 * Source for every value: reference/yt-dlp/yt_dlp/extractor/youtube/_base.py, `INNERTUBE_CLIENTS`
 * (clone of 2026-11-17), cross-checked against reference/MediaServiceCore
 * youtubeapi/.../innertube/utils/Constants.kt (`TV_SAMSUNG`, `VISIONOS`).
 */
data class ClientProfile(
    /** Backrow's name for the profile, used in logs; yt-dlp's client key. */
    val key: String,
    /** `context.client.clientName`. */
    val clientName: String,
    /** Numeric id sent as `X-YouTube-Client-Name` (yt-dlp `INNERTUBE_CONTEXT_CLIENT_NAME`). */
    val clientNameId: Int,
    val clientVersion: String,
    val userAgent: String,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    /** Whether stream URLs need the player JS (signature / n-parameter), ADR-0009. */
    val requiresPlayerJs: Boolean,
)

object ClientProfiles {
    /**
     * A Samsung Tizen TV running YouTube's TV web app. yt-dlp's first default client
     * (`_video.py`, `_DEFAULT_CLIENTS`). Needs the player JS: URLs carry `n`, and some videos
     * use `signatureCipher`. Spike 2026-11-24: `OK` with visitor data, up to 2160p.
     */
    val TV_SAMSUNG =
        ClientProfile(
            key = "tv_samsung",
            clientName = "TVHTML5",
            clientNameId = 7,
            clientVersion = "5.20260707",
            userAgent =
                "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) " +
                    "Version/2.4.0 TV Safari/538.1",
            deviceMake = "Samsung",
            deviceModel = "UKS9800",
            osName = "Tizen",
            osVersion = "2.4.0",
            requiresPlayerJs = true,
        )

    /**
     * Apple Vision Pro. JS-free and, per yt-dlp, no PO token; yt-dlp's only client without a
     * JS runtime (`_DEFAULT_JSLESS_CLIENTS`). "Made for kids" videos fail with it.
     * Spike 2026-11-24: `OK` with visitor data, up to 2160p, URLs playable as they are.
     */
    val VISIONOS =
        ClientProfile(
            key = "visionos",
            clientName = "VISIONOS",
            clientNameId = 101,
            clientVersion = "1.02",
            userAgent =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                    "Version/26.0 Safari/605.1.15",
            deviceMake = "Apple",
            deviceModel = "RealityDevice17,1",
            osName = "visionOS",
            osVersion = "26.5.23O471",
            requiresPlayerJs = false,
        )

    /**
     * The user agent for fetching `https://www.youtube.com/tv`, which hands out visitor data and
     * the current player id (yt-dlp `_base.py` `_download_ytcfg`, client `tv`).
     */
    const val TV_PAGE_USER_AGENT =
        "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), " +
            "Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"

    /**
     * Playback tries these in order and uses the first one that returns playable streams.
     * Not in the chain: `tv` and `tv_downgraded` ("The page needs to be reloaded" on 2026-11-24).
     */
    val PLAYBACK_CHAIN: List<ClientProfile> = listOf(TV_SAMSUNG, VISIONOS)
}
