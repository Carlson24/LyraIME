/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import java.io.File

class TypoDataPlugin : Plugin<Project> {
    companion object {
        const val INSTALL_TASK = "installTypoData"
        const val CLEAN_TASK = "cleanTypoData"
    }

    override fun apply(target: Project) {
        registerInstallTask(target)
        registerCleanTask(target)
    }

    private fun registerInstallTask(project: Project) {
        val task =
            project.tasks.register<InstallTypoDataTask>(INSTALL_TASK) {
                inputDir.set(project.layout.projectDirectory.dir("src/main/jni/librime-typo/data"))
                outputDir.set(project.layout.projectDirectory.dir("src/main/assets/shared/typo"))
            }
        project.tasks.getByName(DataChecksumsPlugin.TASK).dependsOn(task)
    }

    private fun registerCleanTask(project: Project) {
        project
            .tasks.register<Delete>(CLEAN_TASK) {
                delete(project.projectDir.resolve("src/main/assets/shared/typo"))
            }.also {
                project.cleanTask.dependsOn(it)
            }
    }

    abstract class InstallTypoDataTask : DefaultTask() {
        @get:PathSensitive(PathSensitivity.NAME_ONLY)
        @get:InputDirectory
        abstract val inputDir: DirectoryProperty

        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        @TaskAction
        fun execute() {
            val dest = outputDir.get().asFile
            dest.mkdirs()
            inputDir.get().asFile.listFiles { f -> f.extension == "txt" }
                ?.forEach { f -> f.copyTo(File(dest, f.name), overwrite = true) }
        }
    }
}
