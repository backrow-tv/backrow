// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

package dk.backrow.di

import dk.backrow.core.innertube.Innertube
import dk.backrow.core.innertube.JsRuntime
import dk.backrow.core.innertube.PlayerRepository
import dk.backrow.core.jsengine.QuickJsRuntime
import dk.backrow.core.model.Logger
import dk.backrow.core.player.BackrowPlayerFactory
import dk.backrow.log.AndroidLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Background work that outlives a screen (e.g. warming up the player path). */
class AppScope(val scope: CoroutineScope)

val appModule =
    module {
        single<Logger> { AndroidLogger() }
        single { AppScope(CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
        single<JsRuntime> { QuickJsRuntime(get()) }
        single<PlayerRepository> { Innertube.playerRepository(get(), androidContext().cacheDir, get()) }
        single { BackrowPlayerFactory(androidContext(), get()) }
    }
