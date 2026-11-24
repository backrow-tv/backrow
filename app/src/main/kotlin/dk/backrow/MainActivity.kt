// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dk.backrow.core.innertube.PlayerRepository
import dk.backrow.core.model.Logger
import dk.backrow.core.player.BackrowPlayerFactory
import dk.backrow.di.AppScope
import dk.backrow.ui.BackrowApp
import dk.backrow.ui.player.PlaybackDependencies
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val logger: Logger by inject()
    private val appScope: AppScope by inject()

    // Lazy on purpose: building the network and player stack is not part of cold start.
    // The warm-up builds them off the main thread, or the first Select does.
    private val playerRepository: Lazy<PlayerRepository> = inject()
    private val playerFactory: Lazy<BackrowPlayerFactory> = inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logger.info("MainActivity created")
        // Warm up the player path (session, player JS, solver) only once Home is interactive, so it
        // never competes with cold start (CLAUDE.md rule 10). Selecting a video before it finishes
        // simply waits for the same work. On Android 7.1 the "drawn" callback never fires
        // (docs/DEVICE-QUIRKS.md), so a timer starts it too; whichever comes first wins.
        // Both triggers run on the main thread, so a plain flag is enough. Nothing coroutine-related
        // is created before a trigger fires: it measurably slowed cold start (DEVICE-QUIRKS.md).
        var warmUpStarted = false
        val startWarmUp = {
            if (!warmUpStarted) {
                warmUpStarted = true
                appScope.scope.launch { playerRepository.value.warmUp() }
            }
        }
        fullyDrawnReporter.addOnReportDrawnListener { startWarmUp() }
        window.decorView.postDelayed({ startWarmUp() }, WARM_UP_FALLBACK_MS)
        setContent {
            BackrowApp(logger = logger, playback = PlaybackDependencies(playerRepository, playerFactory, logger))
        }
    }

    private companion object {
        const val WARM_UP_FALLBACK_MS = 3_000L
    }
}
