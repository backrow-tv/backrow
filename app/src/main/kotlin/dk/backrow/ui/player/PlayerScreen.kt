// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.ui.player

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.compose.ContentFrame
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dk.backrow.R
import dk.backrow.core.innertube.PlayerRepository
import dk.backrow.core.model.Logger
import dk.backrow.core.player.BackrowPlayerFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** What the player screen needs from the rest of the app; created on first use. */
class PlaybackDependencies(
    repository: Lazy<PlayerRepository>,
    playerFactory: Lazy<BackrowPlayerFactory>,
    val logger: Logger,
) {
    val repository: PlayerRepository by repository
    val playerFactory: BackrowPlayerFactory by playerFactory
}

private sealed interface PlayerUiState {
    data object Loading : PlayerUiState

    data object Ready : PlayerUiState

    data object Failed : PlayerUiState
}

object PlayerTestTags {
    const val SCREEN = "player-screen"
}

private const val OSD_VISIBLE_MS = 2_500L

/**
 * Full-screen playback of one video (Session 2 spike).
 *
 * Remote: Select / Play-Pause toggles, Left/Right seek 10 s, Back returns to Home (the back
 * stack restores Home's focus). The screen holds focus itself and consumes D-pad keys, so focus
 * can never wander off the video.
 *
 * Logs (tag Backrow) the time from Select to the first rendered frame, split into its stages,
 * and every video format the player switches to; docs/DEVICE-QUIRKS.md records those numbers.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(videoId: String, selectedAtMs: Long, dependencies: PlaybackDependencies) {
    val logger = dependencies.logger
    val player = remember { dependencies.playerFactory.createPlayer() }
    var state by remember { mutableStateOf<PlayerUiState>(PlayerUiState.Loading) }
    var isPlaying by remember { mutableStateOf(false) }
    var osdUntil by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    val timings = remember { Timings(selectedAtMs) }
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (timings.firstFrameAt == 0L) {
                        timings.firstFrameAt = SystemClock.elapsedRealtime()
                        logger.info("First frame for $videoId: ${timings.summary()}")
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onTracksChanged(tracks: Tracks) {
                    tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }.forEach { group ->
                        val support =
                            (0 until group.length).joinToString { i ->
                                val f = group.getTrackFormat(i)
                                "${f.height}p${f.frameRate.toInt()} ${f.codecs} ${C.getFormatSupportString(group.getTrackSupport(i))}"
                            }
                        logger.debug("Video tracks: $support")
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    logger.error("Playback failed for $videoId: ${error.errorCodeName}", error)
                    state = PlayerUiState.Failed
                }
            }
        val analytics =
            object : AnalyticsListener {
                override fun onVideoInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: Format,
                    decoderReuseEvaluation: DecoderReuseEvaluation?,
                ) {
                    logger.info("Video format ${format.width}x${format.height}@${format.frameRate} ${format.codecs} ${format.bitrate / 1000} kbit/s (itag ${format.id})")
                }

                override fun onAudioInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: Format,
                    decoderReuseEvaluation: DecoderReuseEvaluation?,
                ) {
                    logger.info("Audio format ${format.codecs} ${format.sampleRate} Hz ${format.channelCount} ch (itag ${format.id})")
                }

                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializedTimestampMs: Long,
                    initializationDurationMs: Long,
                ) {
                    logger.info("Video decoder $decoderName")
                }

                override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                    logger.info("Dropped $droppedFrames video frames in $elapsedMs ms")
                }
            }
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
            player.release()
        }
    }

    LaunchedEffect(videoId) {
        try {
            val info = withContext(Dispatchers.IO) { dependencies.repository.streams(videoId) }
            timings.streamsAt = SystemClock.elapsedRealtime()
            val prepared = withContext(Dispatchers.Default) { dependencies.playerFactory.prepare(info) }
            timings.preparedAt = SystemClock.elapsedRealtime()
            player.setMediaSource(prepared.source)
            player.playWhenReady = true
            player.prepare()
            state = PlayerUiState.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Could not start $videoId: ${e.message}", e)
            state = PlayerUiState.Failed
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Hide the position overlay a moment after the last seek; while paused it stays.
    LaunchedEffect(osdUntil) {
        val wait = osdUntil - SystemClock.elapsedRealtime()
        if (wait > 0) delay(wait)
        osdUntil = 0L
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag(PlayerTestTags.SCREEN)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val code = event.nativeKeyEvent.keyCode
                    if (event.type == KeyEventType.KeyDown && state == PlayerUiState.Ready) {
                        when (PlayerKeys.actionFor(code, event.nativeKeyEvent.repeatCount)) {
                            PlayerAction.TOGGLE_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                            PlayerAction.PLAY -> player.play()
                            PlayerAction.PAUSE -> player.pause()
                            PlayerAction.SEEK_BACK -> player.seekBack()
                            PlayerAction.SEEK_FORWARD -> player.seekForward()
                            null -> Unit
                        }
                        positionMs = player.currentPosition
                        if (code != android.view.KeyEvent.KEYCODE_DPAD_UP && code != android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                            osdUntil = SystemClock.elapsedRealtime() + OSD_VISIBLE_MS
                        }
                    }
                    PlayerKeys.consumes(code)
                },
    ) {
        ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

        when (state) {
            PlayerUiState.Loading -> CenterText(stringResource(R.string.player_loading))
            PlayerUiState.Failed ->
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.player_error_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(stringResource(R.string.player_error_hint), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
            PlayerUiState.Ready ->
                if (!isPlaying || osdUntil > 0L) {
                    Column(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 48.dp, bottom = 36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        if (!isPlaying) Text(stringResource(R.string.player_paused), style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            stringResource(
                                R.string.player_position,
                                PlayerKeys.formatTime(positionMs),
                                PlayerKeys.formatTime(player.duration.takeIf { it > 0 } ?: 0L),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White,
                        )
                    }
                }
        }
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Color.White)
    }
}

/** Timestamps (elapsedRealtime) of the stages between Select and the first frame. */
private class Timings(val selectedAt: Long) {
    var streamsAt = 0L
    var preparedAt = 0L
    var firstFrameAt = 0L

    fun summary(): String =
        "${firstFrameAt - selectedAt} ms after Select " +
            "(streams ${streamsAt - selectedAt} ms, manifest ${preparedAt - streamsAt} ms, player ${firstFrameAt - preparedAt} ms)"
}
