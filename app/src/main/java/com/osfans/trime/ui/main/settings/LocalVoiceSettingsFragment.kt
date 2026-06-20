/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.link.VoiceModelManager
import com.osfans.trime.ui.common.ProgressDialog
import com.osfans.trime.ui.common.confirmDialog
import com.osfans.trime.ui.common.progressDialog
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
        downloadCancelled = false
        val progressDialog = requireContext().progressDialog(
            title = R.string.voice_model_downloading,
            message = "0%",
            cancelable = true,
            onCancel = {
                downloadCancelled = true
                downloadJob?.cancel()
            },
        ).show()

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
                            progressDialog.text = "${(progress * 100).toInt()}%"
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
}
