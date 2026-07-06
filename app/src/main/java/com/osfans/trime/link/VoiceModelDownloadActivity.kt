/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.ui.common.buildDialog
import com.osfans.trime.ui.common.confirmDialog
import com.osfans.trime.ui.common.progressDialog
import com.osfans.trime.util.FileDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class VoiceModelDownloadActivity : ComponentActivity() {

    private val selectFileLauncher =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { handleSelectedFile(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (VoiceModelManager.checkModelFiles()) {
            setResult(RESULT_OK)
            finish()
            return
        }

        val message = getString(R.string.voice_model_missing_message) +
            "\n\n" + VoiceModelManager.getDownloadFileName()
        buildDialog(R.string.voice_model_missing_title)
            .setMessage(message)
            .setPositiveButton(R.string.voice_model_download) { _, _ ->
                startDownload()
            }
            .setNeutralButton(R.string.voice_model_select_file) { _, _ ->
                selectFileLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-bzip2",
                        "application/x-tar",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun startDownload() {
        val progressDialog = progressDialog(
            title = R.string.voice_model_downloading,
            message = "0%",
        ).show()

        lifecycleScope.launch {
            val downloadUrl = VoiceModelManager.getDownloadUrl()
            val ext = VoiceModelManager.archiveExtensionFromUrl(downloadUrl)
            val destName = "voice_model_download$ext"
            try {
                val destFile = File(cacheDir, destName)
                val expectedSha256 = VoiceModelManager.getExpectedSha256()
                withContext(Dispatchers.IO) {
                    FileDownloader.download(
                        url = downloadUrl,
                        destFile = destFile,
                        expectedSha256 = expectedSha256,
                        onProgress = { progress, downloaded, total ->
                            val pct = (progress * 100).toInt()
                            val status = if (progress >= 0f && total > 0) {
                                "$pct% - ${formatBytes(downloaded)}/${formatBytes(total)}"
                            } else if (progress >= 0f) {
                                "$pct%"
                            } else {
                                "…"
                            }
                            runOnUiThread {
                                progressDialog.text = status
                                progressDialog.progress = if (progress < 0) -1 else pct.coerceIn(0, 100)
                            }
                        },
                    )
                }

                progressDialog.setTitle(R.string.voice_model_extracting)
                withContext(Dispatchers.IO) {
                    VoiceModelManager.autoExtract(destFile, VoiceModelManager.voiceDir)
                }

                withContext(Dispatchers.IO) {
                    destFile.delete()
                }

                progressDialog.dismiss()
                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Timber.e(e, "Voice model download/install failed")
                progressDialog.dismiss()
                showError(e.message ?: getString(R.string.voice_model_download_failed, ""))
            }
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        val displayName = queryDisplayName(uri) ?: "voice_model_selected"
        val onProceed = { doImportSelectedFile(uri, displayName) }
        checkModelTypeMismatch(displayName, onProceed)
    }

    private fun doImportSelectedFile(uri: Uri, displayName: String) {
        lifecycleScope.launch {
            val tempName = "voice_selected_$displayName"
            try {
                val verified =
                    withContext(Dispatchers.IO) {
                        val tempFile = File(cacheDir, tempName)
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw RuntimeException("Cannot open file")

                        val valid = VoiceModelManager.verifySha256AnyVariant(tempFile)
                        if (!valid) {
                            tempFile.delete()
                            throw SecurityException(getString(R.string.voice_model_verification_failed))
                        }

                        VoiceModelManager.autoExtract(tempFile, VoiceModelManager.voiceDir)
                        tempFile.delete()
                        valid
                    }

                setResult(RESULT_OK)
                finish()
            } catch (e: Exception) {
                Timber.e(e, "Voice model selection/install failed")
                showError(e.message ?: getString(R.string.voice_model_download_failed, ""))
            }
        }
    }

    private fun checkModelTypeMismatch(displayName: String, onProceed: () -> Unit) {
        val selectedVariant = VoiceModelManager.getSelectedVariant()
        val detectedVariant = detectVariantFromName(displayName)

        if (detectedVariant == selectedVariant) {
            onProceed()
            return
        }

        val fileType = getString(variantToStringResName(detectedVariant))
        val selectedType = getString(variantToStringResName(selectedVariant))

        confirmDialog(
            title = R.string.voice_model_type_mismatch_title,
            message = getString(R.string.voice_model_type_mismatch_message, fileType, selectedType),
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

    private fun queryDisplayName(uri: Uri): String? = contentResolver.query(uri, null, null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }

    private fun showError(message: String) {
        buildDialog(R.string.voice_model_missing_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> String.format("%.1fM", bytes / (1024.0 * 1024.0))
    }
}
