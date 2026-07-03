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
import com.osfans.trime.link.QnnDspManager
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
    private var dspInitPref: Preference? = null
    private var downloadJob: Job? = null
    private var dspJob: Job? = null

    @Volatile private var dspDownloadCancelled = false

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
        refreshDspUi(enabled)
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

        dspInitPref =
            Preference(requireContext()).apply {
                key = "voice_qnn_dsp_init"
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    startDspDownload()
                    true
                }
            }.also { screen.addPreference(it) }

        refreshModelUi()
        updateLocalVoiceEnabled()
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppPrefs.VoiceInput.VOICE_MODEL_TYPE) {
            refreshModelUi()
            refreshDspUi(
                voiceInputPrefs.preferredVoiceInput.getValue() == InputMethodUtils.BUILTIN_VOICE_INPUT &&
                    !voiceInputPrefs.asrkbAidlVoiceInputEnabled.getValue(),
            )
        }
    }

    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences
            ?.registerOnSharedPreferenceChangeListener(prefsListener)
        refreshModelUi()
        val isBuiltin = voiceInputPrefs.preferredVoiceInput.getValue() == InputMethodUtils.BUILTIN_VOICE_INPUT
        val isAidlEnabled = voiceInputPrefs.asrkbAidlVoiceInputEnabled.getValue()
        refreshDspUi(isBuiltin && !isAidlEnabled)
    }

    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences
            ?.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun refreshDspUi(enabled: Boolean) {
        val selected = voiceInputPrefs.voiceModelType.getValue()
        val isQnn = selected == AppPrefs.VoiceInput.VoiceModelType.QNN
        dspInitPref?.isEnabled = enabled && isQnn
        if (isQnn) {
            val installed = QnnDspManager.isInstalled()
            dspInitPref?.title = getString(
                if (installed) {
                    R.string.voice_qnn_dsp_reinitialize
                } else {
                    R.string.voice_qnn_dsp_initialize
                },
            )
            dspInitPref?.summary = getString(
                if (installed) {
                    R.string.voice_qnn_dsp_installed
                } else {
                    R.string.voice_qnn_dsp_not_installed
                },
            )
        } else {
            dspInitPref?.title = getString(R.string.voice_qnn_dsp_initialize)
            dspInitPref?.summary = getString(R.string.voice_qnn_dsp_require_qnn)
        }
    }

    private fun startDspDownload() {
        dspDownloadCancelled = false
        val ctx = requireContext()
        val taskTitle = getString(R.string.voice_qnn_dsp_initialize)
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

        val dialog = ctx.buildDialog(R.string.voice_qnn_dsp_downloading)
            .setView(ScrollView(ctx).also { it.addView(taskContainer) })
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                dspDownloadCancelled = true
                dspJob?.cancel()
            }
            .create()
        dialog.show()

        dspJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    QnnDspManager.ensureInstalled(ctx)
                }

                progressBar.isIndeterminate = false
                progressBar.progress = 100
                val status = if (result != null) {
                    getString(R.string.voice_qnn_dsp_installed)
                } else {
                    getString(R.string.voice_qnn_dsp_download_failed)
                }
                taskText.text = getString(R.string.wanxiang_dl_progress_line, taskTitle, status)

                val button = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
                button.text = getString(android.R.string.ok)
                button.setOnClickListener { dialog.dismiss() }
            } catch (_: CancellationException) {
                dialog.dismiss()
            }
        }

        dialog.setOnDismissListener {
            val isBuiltin = voiceInputPrefs.preferredVoiceInput.getValue() == InputMethodUtils.BUILTIN_VOICE_INPUT
            val isAidlEnabled = voiceInputPrefs.asrkbAidlVoiceInputEnabled.getValue()
            refreshDspUi(isBuiltin && !isAidlEnabled)
        }
    }

    private fun refreshModelUi() {
        val installed = VoiceModelManager.checkModelFiles()
        val variant = VoiceModelManager.getSelectedVariant()
        val variantName = getString(variantToStringRes(variant))

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
            variantName,
        )

        importPref?.title = getString(R.string.voice_model_import_action)
        importPref?.summary = getString(
            if (installed) {
                R.string.voice_model_status_installed
            } else {
                R.string.voice_model_status_not_installed
            },
            variantName,
        )
    }

    private fun variantToStringRes(variant: VoiceModelManager.ModelVariant): Int = when (variant) {
        VoiceModelManager.ModelVariant.QNN -> R.string.voice_model_type_qnn
        VoiceModelManager.ModelVariant.INT8 -> R.string.voice_model_type_int8
        else -> R.string.voice_model_type_standard
    }

    private fun maybeConfirmThenDownload() {
        if (VoiceModelManager.checkModelFiles()) {
            requireContext().confirmDialog(
                title = R.string.voice_model_exists_title,
                message = R.string.voice_model_exists_re_download,
                onConfirm = { startDownload() },
            )
        } else {
            startDownload()
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
                                    R.string.wanxiang_dl_progress_line,
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
                    R.string.wanxiang_dl_progress_line,
                    taskTitle,
                    getString(R.string.voice_model_extracting),
                )
                progressBar.isIndeterminate = true
                withContext(Dispatchers.IO) {
                    VoiceModelManager.autoExtract(destFile, VoiceModelManager.voiceDir)
                    destFile.delete()
                }

                progressBar.isIndeterminate = false
                progressBar.progress = 100
                taskText.text = getString(
                    R.string.wanxiang_dl_progress_line,
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
        val onProceed: () -> Unit = { doImportSelectedFile(ctx, uri, displayName) }
        checkModelTypeMismatch(displayName, ctx, onProceed)
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

    private fun checkModelTypeMismatch(
        displayName: String,
        ctx: android.content.Context,
        onProceed: () -> Unit,
    ) {
        val selectedVariant = VoiceModelManager.getSelectedVariant()
        val detectedVariant = detectVariantFromName(displayName)

        if (detectedVariant == selectedVariant) {
            onProceed()
            return
        }

        val fileType = ctx.getString(variantToStringResName(detectedVariant))
        val selectedType = ctx.getString(variantToStringResName(selectedVariant))

        ctx.confirmDialog(
            title = R.string.voice_model_type_mismatch_title,
            message = ctx.getString(R.string.voice_model_type_mismatch_message, fileType, selectedType),
            onConfirm = onProceed,
        )
    }

    private fun detectVariantFromName(name: String): VoiceModelManager.ModelVariant = when {
        name.contains("qnn", ignoreCase = true) || name.contains("SM8850") -> VoiceModelManager.ModelVariant.QNN
        name.contains("int8", ignoreCase = true) -> VoiceModelManager.ModelVariant.INT8
        else -> VoiceModelManager.ModelVariant.STANDARD
    }

    private fun variantToStringResName(variant: VoiceModelManager.ModelVariant): Int = when (variant) {
        VoiceModelManager.ModelVariant.QNN -> R.string.voice_model_type_qnn_name
        VoiceModelManager.ModelVariant.INT8 -> R.string.voice_model_type_int8_name
        else -> R.string.voice_model_type_standard_name
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
