/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.link.SherpaSpeechClient
import com.osfans.trime.link.VoiceModelManager
import com.osfans.trime.ui.common.buildDialog
import com.osfans.trime.ui.common.confirmDialog
import com.osfans.trime.ui.common.progressDialog
import com.osfans.trime.util.FileDownloader
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class VoiceInputSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().voiceInput) {

    private val voiceInputPrefs = AppPrefs.defaultInstance().voiceInput
    private var downloadPref: Preference? = null
    private var importPref: Preference? = null
    private var deletePref: Preference? = null
    private var downloadJob: Job? = null

    private val importMimeTypes = arrayOf(
        "application/zip",
        "application/x-bzip2",
        "application/x-tar",
        "application/octet-stream",
        "*/*",
    )

    private val selectFileLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri ?: return@registerForActivityResult
            handleSelectedFile(uri)
        }

    private val onVoiceInputChangeListener = PreferenceDelegateProvider.OnChangeListener { key ->
        if (key == AppPrefs.VoiceInput.PREFERRED_VOICE_INPUT ||
            key == AppPrefs.VoiceInput.ASRKB_AIDL_VOICE_INPUT
        ) {
            updateLocalVoiceEnabled()
        }
    }

    private fun updateLocalVoiceEnabled() {
        val isBuiltin = voiceInputPrefs.preferredVoiceInput.getValue() == InputMethodUtils.BUILTIN_VOICE_INPUT
        val isAidlEnabled = voiceInputPrefs.asrkbAidlVoiceInputEnabled.getValue()
        val enabled = isBuiltin && !isAidlEnabled
        downloadPref?.isEnabled = enabled
        importPref?.isEnabled = enabled
        refreshDeletePrefState()
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        voiceInputPrefs.registerOnChangeListener(onVoiceInputChangeListener)
    }

    override fun onDestroy() {
        voiceInputPrefs.unregisterOnChangeListener(onVoiceInputChangeListener)
        super.onDestroy()
    }

    override fun onPreferenceUiCreated(screen: PreferenceScreen) {
        downloadPref =
            Preference(requireContext()).apply {
                key = "voice_model_download"
                isIconSpaceReserved = false
                title = getString(R.string.voice_model_download_action)
                setOnPreferenceClickListener {
                    maybeConfirmThenDownload()
                    true
                }
            }.also { screen.addPreference(it) }

        importPref =
            Preference(requireContext()).apply {
                key = "voice_model_import"
                isIconSpaceReserved = false
                title = getString(R.string.voice_model_import_action)
                setOnPreferenceClickListener {
                    maybeConfirmThenImport()
                    true
                }
            }.also { screen.addPreference(it) }

        deletePref =
            Preference(requireContext()).apply {
                key = "voice_model_delete"
                isIconSpaceReserved = false
                title = getString(R.string.voice_model_delete_action)
                setOnPreferenceClickListener {
                    maybeConfirmThenDelete()
                    true
                }
            }.also { screen.addPreference(it) }

        refreshModelUi()
        updateLocalVoiceEnabled()
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppPrefs.VoiceInput.VOICE_CHUNK_SIZE) {
            refreshModelUi()
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences
            ?.registerOnSharedPreferenceChangeListener(prefsListener)
        refreshModelUi()
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences
            ?.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun refreshModelUi() {
        val installed = VoiceModelManager.checkModelFiles()

        downloadPref?.title = getString(
            if (installed) {
                R.string.voice_model_download_action_reinstall
            } else {
                R.string.voice_model_download_action
            },
        )
        downloadPref?.summary = getString(
            if (installed) {
                R.string.voice_model_status_installed
            } else {
                R.string.voice_model_status_not_installed
            },
        )

        importPref?.title = getString(R.string.voice_model_import_action)
        importPref?.summary = getString(
            if (installed) {
                R.string.voice_model_status_installed
            } else {
                R.string.voice_model_status_not_installed
            },
        )

        refreshDeletePrefState()
    }

    private fun refreshDeletePrefState() {
        val installed = VoiceModelManager.checkModelFiles()
        val builtinVoice =
            voiceInputPrefs.preferredVoiceInput.getValue() == InputMethodUtils.BUILTIN_VOICE_INPUT &&
                !voiceInputPrefs.asrkbAidlVoiceInputEnabled.getValue()
        deletePref?.isEnabled = builtinVoice && installed
        deletePref?.summary = getString(
            if (installed) {
                R.string.voice_model_status_installed
            } else {
                R.string.voice_model_status_not_installed
            },
        )
    }

    private fun maybeConfirmThenDelete() {
        requireContext().confirmDialog(
            title = R.string.voice_model_delete_title,
            message = R.string.voice_model_delete_message,
            onConfirm = { doDeleteModel() },
        )
    }

    private fun doDeleteModel() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                SherpaSpeechClient.resetEngine()
                VoiceModelManager.deleteModel()
            }
            requireContext().toast(R.string.voice_model_deleted)
            refreshModelUi()
        }
    }

    private fun maybeConfirmThenDownload() {
        val fileName = VoiceModelManager.getDownloadFileName()
        if (VoiceModelManager.checkModelFiles()) {
            requireContext().confirmDialog(
                title = R.string.voice_model_exists_title,
                message = getString(R.string.voice_model_exists_re_download) + "\n\n" + fileName,
                onConfirm = { startDownload() },
            )
        } else {
            requireContext().confirmDialog(
                title = R.string.voice_model_download_action,
                message = fileName,
                onConfirm = { startDownload() },
            )
        }
    }

    private fun maybeConfirmThenImport() {
        if (VoiceModelManager.checkModelFiles()) {
            requireContext().confirmDialog(
                title = R.string.voice_model_exists_title,
                message = R.string.voice_model_exists_re_import,
                onConfirm = { selectFileLauncher.launch(importMimeTypes) },
            )
        } else {
            selectFileLauncher.launch(importMimeTypes)
        }
    }

    private fun startDownload() {
        val ctx = requireContext()
        val downloadUrl = VoiceModelManager.getDownloadUrl()
        val ext = VoiceModelManager.archiveExtensionFromUrl(downloadUrl)
        val destName = "voice_model_download$ext"
        val taskTitle = getString(R.string.voice_model_download_action)
        val dp = ctx.resources.displayMetrics.density
        fun Int.dp() = (this * dp).toInt()

        val taskText = TextView(ctx).apply {
            textSize = 14f
            text = taskTitle
        }
        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            max = 100
            isIndeterminate = true
        }
        val taskContainer = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp(), 16.dp(), 24.dp(), 16.dp())
            addView(taskText)
            addView(progressBar)
        }

        val dialog = ctx.buildDialog(R.string.voice_model_downloading)
            .setView(ScrollView(ctx).also { it.addView(taskContainer) })
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> downloadJob?.cancel() }
            .create()
        dialog.show()

        downloadJob = lifecycleScope.launch {
            try {
                val destFile = File(ctx.cacheDir, destName)
                val expectedSha256 = VoiceModelManager.getExpectedSha256()
                withContext(Dispatchers.IO) {
                    FileDownloader.download(
                        url = downloadUrl,
                        destFile = destFile,
                        expectedSha256 = expectedSha256,
                        onProgress = { progress, downloaded, total ->
                            launch(Dispatchers.Main) {
                                val status = formatDownloadStatus(progress, downloaded, total)
                                taskText.text = getString(
                                    R.string.custom_dl_progress_line,
                                    taskTitle,
                                    status,
                                )
                                if (progress < 0f) {
                                    progressBar.isIndeterminate = true
                                } else {
                                    progressBar.isIndeterminate = false
                                    progressBar.progress = (progress * 100).toInt().coerceIn(0, 100)
                                }
                            }
                        },
                        isCancelled = { downloadJob?.isActive != true },
                    )
                }

                taskText.text = getString(
                    R.string.custom_dl_progress_line,
                    taskTitle,
                    getString(R.string.voice_model_extracting),
                )
                progressBar.isIndeterminate = true
                withContext(Dispatchers.IO) {
                    VoiceModelManager.autoExtract(destFile, VoiceModelManager.voiceDir)
                    destFile.delete()
                }

                taskText.text = getString(
                    R.string.custom_dl_progress_line,
                    taskTitle,
                    getString(R.string.voice_model_initializing),
                )
                withContext(Dispatchers.IO) {
                    SherpaSpeechClient.initializeAfterInstall()
                }

                progressBar.isIndeterminate = false
                progressBar.progress = 100
                taskText.text = getString(
                    R.string.custom_dl_progress_line,
                    taskTitle,
                    getString(R.string.voice_model_installed),
                )

                val button = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                button.text = getString(android.R.string.ok)
                button.setOnClickListener { dialog.dismiss() }
            } catch (_: CancellationException) {
                withContext(Dispatchers.IO) {
                    File(ctx.cacheDir, destName).delete()
                }
                dialog.dismiss()
            } catch (e: Exception) {
                Timber.e(e, "Voice model download/install failed")
                withContext(Dispatchers.IO) {
                    File(ctx.cacheDir, destName).delete()
                }
                dialog.dismiss()
                ctx.toast(e.message ?: getString(R.string.voice_model_download_failed, ""))
            }
        }

        dialog.setOnDismissListener {
            refreshModelUi()
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        val ctx = requireContext()
        val displayName = queryDisplayName(ctx, uri) ?: "voice_model_selected"
        doImportSelectedFile(ctx, uri, displayName)
    }

    private fun doImportSelectedFile(
        ctx: android.content.Context,
        uri: Uri,
        displayName: String,
    ) {
        val progressDialog = ctx.progressDialog(
            message = ctx.getString(R.string.voice_model_importing),
        ).show()

        lifecycleScope.launch {
            val tempName = "voice_selected_$displayName"
            try {
                val tempFile = File(ctx.cacheDir, tempName)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw RuntimeException("Cannot open file")
                }

                progressDialog.text = ctx.getString(R.string.voice_model_verifying)
                val valid =
                    withContext(Dispatchers.IO) {
                        VoiceModelManager.verifySha256AnyVariant(tempFile)
                    }
                if (!valid) {
                    tempFile.delete()
                    throw SecurityException(getString(R.string.voice_model_verification_failed))
                }

                progressDialog.text = ctx.getString(R.string.voice_model_extracting)
                withContext(Dispatchers.IO) {
                    VoiceModelManager.autoExtract(tempFile, VoiceModelManager.voiceDir)
                    tempFile.delete()
                }

                progressDialog.text = ctx.getString(R.string.voice_model_initializing)
                withContext(Dispatchers.IO) {
                    SherpaSpeechClient.initializeAfterInstall()
                }

                progressDialog.dismiss()
                ctx.toast(R.string.voice_model_installed)
                refreshModelUi()
            } catch (e: Exception) {
                Timber.e(e, "Voice model selection/install failed")
                progressDialog.dismiss()
                ctx.toast(e.message ?: getString(R.string.voice_model_download_failed, ""))
            }
        }
    }

    private fun queryDisplayName(ctx: android.content.Context, uri: Uri): String? = ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) cursor.getString(idx) else null
        } else {
            null
        }
    }

    private fun formatDownloadStatus(progress: Float, downloaded: Long, total: Long): String {
        val pct = (progress * 100).toInt()
        return if (progress >= 0f && total > 0) {
            "$pct% — ${formatBytes(downloaded)}/${formatBytes(total)}"
        } else if (progress >= 0f) {
            "$pct%"
        } else {
            "…"
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fM", bytes / (1024.0 * 1024.0))
    }
}
