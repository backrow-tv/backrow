// Copyright 2026 The Backrow contributors
// SPDX-License-Identifier: Apache-2.0

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Modules may not declare their own repositories; everything comes from these two.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "backrow"

include(":app")
include(":core:model")
include(":core:innertube")
include(":core:player")
include(":core:jsengine")
include(":core:sponsorblock")
include(":core:data")
