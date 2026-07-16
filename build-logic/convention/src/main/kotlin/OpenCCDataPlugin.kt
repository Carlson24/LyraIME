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

class OpenCCDataPlugin : Plugin<Project> {
    companion object {
        const val INSTALL_TASK = "installOpenCCData"
        const val CLEAN_TASK = "cleanOpenCCData"
    }

    private val Project.dataBaseDir
        get() = file("src/main/jni/OpenCC/data")

    override fun apply(target: Project) {
        registerInstallTask(target)
        registerCleanTask(target)
    }

    private fun registerInstallTask(project: Project) {
        val task =
            project.tasks.register<InstallOpenCCDataTask>(INSTALL_TASK) {
                inputDir.set(project.dataBaseDir)
                outputDir.set(project.layout.projectDirectory.dir("src/main/assets/shared"))
            }
        project.tasks.getByName(DataChecksumsPlugin.TASK).dependsOn(task)
    }

    private fun registerCleanTask(project: Project) {
        project
            .tasks.register<Delete>(CLEAN_TASK) {
                delete(project.projectDir.resolve("src/main/assets/shared/opencc"))
            }.also {
                project.cleanTask.dependsOn(it)
            }
    }

    abstract class InstallOpenCCDataTask : DefaultTask() {
        @get:PathSensitive(PathSensitivity.NAME_ONLY)
        @get:InputDirectory
        abstract val inputDir: DirectoryProperty

        @get:OutputDirectory
        abstract val outputDir: DirectoryProperty

        private val input by lazy { inputDir.get().asFile }

        companion object {
            private val DICTS_RAW =
                arrayOf(
                    "STCharacters",
                    "STPhrases",
                    "TSCharacters",
                    "TSPhrases",
                    "TWPhrases",
                    "TWPhrasesRev",
                    "TWVariants",
                    "TWVariantsPhrases",
                    "TWVariantsRevPhrases",
                    "HKPhrases",
                    "HKPhrasesRev",
                    "HKVariants",
                    "HKVariantsPhrases",
                    "HKVariantsRevPhrases",
                    "JPShinjitaiCharacters",
                    "JPShinjitaiPhrases",
                    "CJK_Compatibility_Ideographs",
                )

            private val DICTS_GENERATED = arrayOf("TWVariantsRev", "HKVariantsRev", "JPShinjitaiCharacters")
        }

        private fun installOpenCC(dir: File) {
            dir.mkdirs()
            val configDir = input.resolve("config")
            configDir.listFiles { f -> f.extension == "json" }
                ?.forEach { f -> f.copyTo(File(dir, f.name), overwrite = true) }

            val dictionary = input.resolve("dictionary")
            for (raw in DICTS_RAW) {
                val basename = "$raw.txt"
                dictionary.resolve(basename).copyTo(File(dir, basename), overwrite = true)
            }
            val reverse = input.resolve("scripts/reverse.py").absolutePath
            for (dict in DICTS_GENERATED) {
                val inputName = dict.substringBefore("Rev")
                val inputFile = dictionary.resolve("$inputName.txt")
                val outputFile = File(dir, "$dict.txt")
                project.providers.exec {
                    workingDir = dir
                    commandLine = listOf("python3", reverse, inputFile.absolutePath, outputFile.name)
                }.result.get()
            }
        }

        @TaskAction
        fun execute() {
            installOpenCC(outputDir.get().asFile.resolve("opencc"))
        }
    }
}
