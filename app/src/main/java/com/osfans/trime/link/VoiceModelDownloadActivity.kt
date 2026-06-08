/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
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
            setResult(Activity.RESULT_OK)
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.voice_model_missing_title)
            .setMessage(R.string.voice_model_missing_message)
            .setPositiveButton(R.string.voice_model_download) { _, _ ->
                startDownload()
            }
            .setNeutralButton(R.string.voice_model_select_file) { _, _ ->
                selectFileLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun startDownload() {
        val progressDialog =
            AlertDialog.Builder(this)
                .setTitle(R.string.voice_model_downloading)
                .setMessage("0%")
                .setCancelable(false)
                .create()
        progressDialog.show()

        lifecycleScope.launch {
            try {
                val destFile = File(cacheDir, "voice_model_download.zip")
                withContext(Dispatchers.IO) {
                    VoiceModelManager.downloadFile(
                        VoiceModelManager.DOWNLOAD_URL,
                        destFile,
                    ) { progress ->
                        runOnUiThread {
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
                    VoiceModelManager.extractZip(destFile, VoiceModelManager.voiceDir)
                }

                withContext(Dispatchers.IO) {
                    destFile.delete()
                }

                progressDialog.dismiss()
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: Exception) {
                Timber.e(e, "Voice model download/install failed")
                progressDialog.dismiss()
                showError(e.message ?: getString(R.string.voice_model_download_failed, ""))
            }
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val verified =
                    withContext(Dispatchers.IO) {
                        val tempFile =
                            File(cacheDir, "voice_model_selected.zip")
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw RuntimeException("Cannot open file")

                        val valid = VoiceModelManager.verifySha256(tempFile)
                        if (!valid) {
                            tempFile.delete()
                            throw SecurityException(getString(R.string.voice_model_verification_failed))
                        }

                        VoiceModelManager.extractZip(tempFile, VoiceModelManager.voiceDir)
                        tempFile.delete()
                        valid
                    }

                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: Exception) {
                Timber.e(e, "Voice model selection/install failed")
                showError(e.message ?: getString(R.string.voice_model_download_failed, ""))
            }
        }
    }

    private fun showError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.voice_model_missing_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
}
