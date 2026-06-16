/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import org.gradle.api.Plugin
import org.gradle.api.Project

@Suppress("unused")
class AndroidAppConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.configureEach {
            if (name.contains("ArtProfile", ignoreCase = true)) {
                enabled = false
            }
        }
    }
}
