// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui

import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dk.backrow.core.model.Logger
import dk.backrow.ui.home.HomeScreen
import dk.backrow.ui.player.PlaybackDependencies
import dk.backrow.ui.player.PlayerScreen
import dk.backrow.ui.settings.SettingsScreen
import dk.backrow.ui.theme.BackrowTheme
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object SettingsRoute

/**
 * Full-screen playback. [selectedAtMs] is `SystemClock.elapsedRealtime()` when the user pressed
 * Select, so the player can log select-to-first-frame (docs/DEVICE-QUIRKS.md).
 */
@Serializable
data class PlayerRoute(val videoId: String, val selectedAtMs: Long)

/**
 * The spike plays one fixed video from every card until Home has real content: "Caminandes 3:
 * Llamigos" by the Blender Foundation (CC BY), a public, stable upload with 16:9 1080p at 24 fps
 * in H.264, VP9 and AV1, like most YouTube videos. (Big Buck Bunny 60fps, used for the
 * fixtures, has 1080p only at 60 fps, which the emulators' decoders do not claim to handle.)
 */
const val SPIKE_VIDEO_ID = "SkVqJ1SGeL0"

// Short cross-fades: the defaults are slow enough to feel laggy with a remote, and
// cheap sticks drop frames on long animations.
private const val TRANSITION_MILLIS = 150

/**
 * App root: theme plus the navigation graph (ADR-0010). Back is handled by the navigation
 * back stack; on Home it leaves the app.
 */
@Composable
fun BackrowApp(logger: Logger, playback: PlaybackDependencies) {
    BackrowTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                enterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
                exitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
                popEnterTransition = { fadeIn(tween(TRANSITION_MILLIS)) },
                popExitTransition = { fadeOut(tween(TRANSITION_MILLIS)) },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
                        onCardSelected = { card ->
                            logger.debug("Selected placeholder card row=${card.row} index=${card.index}")
                            navController.navigate(PlayerRoute(SPIKE_VIDEO_ID, SystemClock.elapsedRealtime())) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen()
                }
                composable<PlayerRoute> { entry ->
                    val route = entry.toRoute<PlayerRoute>()
                    PlayerScreen(videoId = route.videoId, selectedAtMs = route.selectedAtMs, dependencies = playback)
                }
            }
        }
    }
}
