/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.net.Uri
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegateFragment
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.link.VoiceModelManager
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
    private var downloadJob: Job? = null

    @Volatile private var downloadCancelled = false

    @Keep
    private val onVoiceInputChangeListener = PreferenceDelegateProvider.OnChangeListener { key ->
        if (key == AppPrefs.VoiceInput.PREFERRED_VOICE_INPUT ||
            key == AppPrefs.VoiceInput.ASRKB_AIDL_VOICE_INPUT
        ) {
            updateModelActionEnabled()
        }
    }

    private fun updateModelActionEnabled() {
        val isBuiltin = voiceInputPrefs.preferredVoiceInput.getValue() == InputMethodUtils.BUILTIN_VOICE_INPUT
        val isAidlEnabled = voiceInputPrefs.asrkbAidlVoiceInputEnabled.getValue()
        val enabled = isBuiltin && !isAidlEnabled
        downloadPref?.isEnabled = enabled
        importPref?.isEnabled = enabled
    }

    init {
        voiceInputPrefs.registerOnChangeListener(onVoiceInputChangeListener)
    }

    override fun onDestroy() {
        voiceInputPrefs.unregisterOnChangeListener(onVoiceInputChangeListener)
        super.onDestroy()
    }

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

        updateModelActionEnabled()
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
                        arrayOf("application/zip", "application/octet-stream", "*/*"),
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            selectFileLauncher.launch(
                arrayOf("application/zip", "application/octet-stream", "*/*"),
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
            try {
                val destFile = File(requireContext().cacheDir, "voice_model_download.zip")
                val activity = requireActivity()
                withContext(Dispatchers.IO) {
                    VoiceModelManager.downloadFile(
                        VoiceModelManager.DOWNLOAD_URL,
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
                    VoiceModelManager.extractZip(destFile, VoiceModelManager.voiceDir)
                }

                withContext(Dispatchers.IO) {
                    destFile.delete()
                }

                progressDialog.dismiss()
                requireContext().toast(R.string.voice_model_installed)
            } catch (e: CancellationException) {
                progressDialog.dismiss()
                withContext(Dispatchers.IO) {
                    File(requireContext().cacheDir, "voice_model_download.zip").delete()
                }
            } catch (e: Exception) {
                if (downloadCancelled) {
                    progressDialog.dismiss()
                    withContext(Dispatchers.IO) {
                        File(requireContext().cacheDir, "voice_model_download.zip").delete()
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
            try {
                val tempFile = File(ctx.cacheDir, "voice_model_selected.zip")
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
                        VoiceModelManager.verifySha256(tempFile)
                    }
                if (!valid) {
                    tempFile.delete()
                    throw SecurityException(getString(R.string.voice_model_verification_failed))
                }

                statusText.setText(R.string.voice_model_extracting)
                withContext(Dispatchers.IO) {
                    VoiceModelManager.extractZip(tempFile, VoiceModelManager.voiceDir)
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
}
