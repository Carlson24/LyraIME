/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.link.VoiceModelManager
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class LocalVoiceSettingsFragment : PreferenceDelegateFragment(AppPrefs.defaultInstance().localVoice) {

    private var downloadPref: Preference? = null
    private var importPref: Preference? = null
    private var downloadJob: Job? = null

    @Volatile private var downloadCancelled = false

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
    }

    private fun maybeConfirmThenDownload() {
        if (VoiceModelManager.checkModelFiles()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.voice_model_exists_title)
                .setMessage(R.string.voice_model_exists_re_download)
                .setPositiveButton(android.R.string.ok) { _, _ -> startDownload() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            startDownload()
        }
    }

    private fun maybeConfirmThenImport() {
        if (VoiceModelManager.checkModelFiles()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.voice_model_exists_title)
                .setMessage(R.string.voice_model_exists_re_import)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    selectFileLauncher.launch(
                        importMimeTypes,
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            selectFileLauncher.launch(
                importMimeTypes,
            )
        }
    }

    private fun startDownload() {
        downloadCancelled = false
        val progressDialog =
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.voice_model_downloading)
                .setMessage("0%")
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    downloadCancelled = true
                    downloadJob?.cancel()
                }
                .setCancelable(false)
                .create()
        progressDialog.show()

        downloadJob = lifecycleScope.launch {
            val downloadUrl = VoiceModelManager.getDownloadUrl()
            val ext = VoiceModelManager.archiveExtensionFromUrl(downloadUrl)
            val destName = "voice_model_download$ext"
            try {
                val destFile = File(requireContext().cacheDir, destName)
                val activity = requireActivity()
                withContext(Dispatchers.IO) {
                    VoiceModelManager.downloadFile(
                        downloadUrl,
                        destFile,
                        { downloadCancelled },
                    ) { progress ->
                        activity.runOnUiThread {
                            progressDialog.setMessage("${(progress * 100).toInt()}%")
                        }
                    }
                }

                progressDialog.setTitle(R.string.voice_model_verifying)
                val verified =
                    withContext(Dispatchers.IO) {
                        VoiceModelManager.verifySha256(destFile)
                    }
                if (!verified) {
                    destFile.delete()
                    throw SecurityException(getString(R.string.voice_model_verification_failed))
                }

                progressDialog.setTitle(R.string.voice_model_extracting)
                withContext(Dispatchers.IO) {
                    VoiceModelManager.autoExtract(destFile, VoiceModelManager.voiceDir)
                }

                withContext(Dispatchers.IO) {
                    destFile.delete()
                }

                progressDialog.dismiss()
                requireContext().toast(R.string.voice_model_installed)
            } catch (_: CancellationException) {
                progressDialog.dismiss()
                withContext(Dispatchers.IO) {
                    File(requireContext().cacheDir, destName).delete()
                }
            } catch (e: Exception) {
                if (downloadCancelled) {
                    progressDialog.dismiss()
                    withContext(Dispatchers.IO) {
                        File(requireContext().cacheDir, destName).delete()
                    }
                } else {
                    Timber.e(e, "Voice model download/install failed")
                    progressDialog.dismiss()
                    requireContext().toast(e.message ?: getString(R.string.voice_model_download_failed, ""))
                }
            }
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
        val statusText =
            TextView(ctx).apply {
                setText(R.string.voice_model_importing)
            }
        val progressBar =
            ProgressBar(ctx).apply {
                isIndeterminate = true
            }
        val layout =
            LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(72, 32, 72, 32)
                addView(progressBar)
                addView(statusText)
            }

        val progressDialog =
            AlertDialog.Builder(ctx)
                .setView(layout)
                .setCancelable(false)
                .create()
        progressDialog.show()

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

                statusText.setText(R.string.voice_model_verifying)
                val valid =
                    withContext(Dispatchers.IO) {
                        VoiceModelManager.verifySha256AnyVariant(tempFile)
                    }
                if (!valid) {
                    tempFile.delete()
                    throw SecurityException(getString(R.string.voice_model_verification_failed))
                }

                statusText.setText(R.string.voice_model_extracting)
                withContext(Dispatchers.IO) {
                    VoiceModelManager.autoExtract(tempFile, VoiceModelManager.voiceDir)
                    tempFile.delete()
                }

                progressDialog.dismiss()
                ctx.toast(R.string.voice_model_installed)
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

        AlertDialog.Builder(ctx)
            .setTitle(R.string.voice_model_type_mismatch_title)
            .setMessage(ctx.getString(R.string.voice_model_type_mismatch_message, fileType, selectedType))
            .setPositiveButton(android.R.string.ok) { _, _ -> onProceed() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun detectVariantFromName(name: String): VoiceModelManager.ModelVariant {
        return when {
            name.contains("qnn", ignoreCase = true) || name.contains("SM8850") -> VoiceModelManager.ModelVariant.QNN
            name.contains("int8", ignoreCase = true) -> VoiceModelManager.ModelVariant.INT8
            else -> VoiceModelManager.ModelVariant.STANDARD
        }
    }

    private fun variantToStringResName(variant: VoiceModelManager.ModelVariant): Int {
        return when (variant) {
            VoiceModelManager.ModelVariant.QNN -> R.string.voice_model_type_qnn_name
            VoiceModelManager.ModelVariant.INT8 -> R.string.voice_model_type_int8_name
            else -> R.string.voice_model_type_standard_name
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
}
