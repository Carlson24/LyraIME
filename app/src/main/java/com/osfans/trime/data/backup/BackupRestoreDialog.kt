/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.ui.common.buildDialog
import com.osfans.trime.ui.common.confirmDialog
import com.osfans.trime.ui.common.pickMultiple
import com.osfans.trime.ui.common.withLoadingDialog
import com.osfans.trime.util.getFileFromUri
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BackupRestoreDialog(private val fragment: Fragment) {
    private lateinit var saveLauncher: ActivityResultLauncher<String>
    private lateinit var openLauncher: ActivityResultLauncher<Array<String>>

    private var isBackupInProgress = false
    private var isRestoreInProgress = false

    fun setupLaunchers() {
        saveLauncher =
            fragment.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                uri ?: return@registerForActivityResult
                handleBackupSave(uri)
            }

        openLauncher =
            fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri ?: return@registerForActivityResult
                handleRestoreOpen(uri)
            }
    }

    fun showBackupDialog() {
        if (isBackupInProgress) return

        val context = fragment.requireContext()
        val items =
            arrayOf(
                context.getString(R.string.preferences),
                context.getString(R.string.clipboard_data),
                context.getString(R.string.wanxiang_data),
                context.getString(R.string.custom_tasks_data),
            )
        val checked = booleanArrayOf(true, true, true, true)

        context.buildDialog(R.string.select_backup_items)
            .apply {
                setMultiChoiceItems(items, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    if (isBackupInProgress) return@setPositiveButton
                    val timestamp = System.currentTimeMillis()
                    val fileName = "trime_backup_$timestamp.json"
                    performBackup(checked[0], checked[1], checked[2], checked[3], fileName)
                }
            }.show()
    }

    fun showRestoreDialog() {
        if (isRestoreInProgress) return
        openLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    private fun performBackup(
        includePreferences: Boolean,
        includeClipboard: Boolean,
        includeWanxiang: Boolean,
        includeCustomTasks: Boolean,
        fileName: String,
    ) {
        if (isBackupInProgress) return

        val context = fragment.requireContext()
        isBackupInProgress = true

        // Launch backup process in a coroutine
        fragment.lifecycleScope.launch {
            var tempFile: File? = null
            try {
                // Show loading dialog during backup creation
                fragment.lifecycleScope.withLoadingDialog(context, threshold = 0L) {
                    withContext(Dispatchers.IO) {
                        tempFile = File(context.cacheDir, "temp_backup.json")
                        tempFile.delete()

                        val backupData =
                            BackupManager.createBackup(
                                includePreferences = includePreferences,
                                includeClipboard = includeClipboard,
                                includeWanxiang = includeWanxiang,
                                includeCustomTasks = includeCustomTasks,
                            )

                        BackupManager.saveBackupToFile(backupData, tempFile).getOrThrow()
                    }
                }

                // Launch file picker after backup is complete (loading dialog already dismissed)
                withContext(Dispatchers.Main) {
                    saveLauncher.launch(fileName)
                }
            } catch (e: Exception) {
                tempFile?.delete()
                withContext(Dispatchers.Main) {
                    context.toast(R.string.backup_failure)
                }
            } finally {
                isBackupInProgress = false
            }
        }
    }

    private fun handleBackupSave(uri: Uri) {
        val context = fragment.requireContext()

        // Use withLoadingDialog with 0 threshold to show immediately
        fragment.lifecycleScope.withLoadingDialog(context, threshold = 0L) {
            try {
                withContext(Dispatchers.IO) {
                    val tempFile = File(context.cacheDir, "temp_backup.json")
                    if (!tempFile.exists()) {
                        withContext(Dispatchers.Main) {
                            context.toast(R.string.backup_failure)
                        }
                        return@withContext
                    }

                    val inputStream = tempFile.inputStream()
                    val outputStream = context.contentResolver.openOutputStream(uri)
                    outputStream?.use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                    tempFile.delete()
                }

                withContext(Dispatchers.Main) {
                    context.toast(R.string.backup_success)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context.toast(R.string.backup_failure)
                }
            }
        }
    }

    private fun handleRestoreOpen(uri: Uri) {
        if (isRestoreInProgress) return

        val context = fragment.requireContext()
        isRestoreInProgress = true

        // Use withLoadingDialog with 0 threshold to show immediately
        fragment.lifecycleScope.withLoadingDialog(context, threshold = 0L) {
            try {
                val file = withContext(Dispatchers.IO) {
                    context.getFileFromUri(uri)
                }

                if (file == null || !file.exists()) {
                    withContext(Dispatchers.Main) {
                        context.toast(R.string.backup_file_invalid)
                    }
                    return@withLoadingDialog
                }

                val backupData = withContext(Dispatchers.IO) {
                    BackupManager.loadBackupFromFile(file).getOrThrow()
                }

                if (backupData.version > BackupData.CURRENT_VERSION) {
                    withContext(Dispatchers.Main) {
                        context.toast(R.string.backup_version_too_new)
                    }
                    return@withLoadingDialog
                }

                val hasPreferences = backupData.preferences != null
                val hasClipboard = backupData.clipboard != null
                val hasWanxiang = backupData.wanxiangPrefs != null
                val hasCustomTasks = backupData.customTasks != null

                if (!hasPreferences && !hasClipboard && !hasWanxiang && !hasCustomTasks) {
                    withContext(Dispatchers.Main) {
                        context.toast(R.string.backup_file_invalid)
                    }
                    return@withLoadingDialog
                }

                withContext(Dispatchers.Main) {
                    showRestoreItemsDialog(backupData, hasPreferences, hasClipboard, hasWanxiang, hasCustomTasks)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context.toast(R.string.backup_file_invalid)
                }
            } finally {
                isRestoreInProgress = false
            }
        }
    }

    private fun showRestoreItemsDialog(
        backupData: BackupData,
        hasPreferences: Boolean,
        hasClipboard: Boolean,
        hasWanxiang: Boolean,
        hasCustomTasks: Boolean,
    ) {
        val context = fragment.requireContext()
        val items = mutableListOf<String>()
        val checked = mutableListOf<Boolean>()

        if (hasPreferences) {
            items.add(context.getString(R.string.preferences))
            checked.add(true)
        }
        if (hasClipboard) {
            items.add(context.getString(R.string.clipboard_data))
            checked.add(true)
        }
        if (hasWanxiang) {
            items.add(context.getString(R.string.wanxiang_data))
            checked.add(true)
        }
        if (hasCustomTasks) {
            items.add(context.getString(R.string.custom_tasks_data))
            checked.add(true)
        }

        context.buildDialog(R.string.select_restore_items)
            .apply {
                setMultiChoiceItems(items.toTypedArray(), checked.toBooleanArray()) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    var idx = 0
                    performRestore(
                        backupData,
                        if (hasPreferences) checked[idx++] else false,
                        if (hasClipboard) checked[idx++] else false,
                        if (hasWanxiang) checked[idx++] else false,
                        if (hasCustomTasks) checked[idx] else false,
                    )
                }
            }.show()
    }

    private fun performRestore(
        backupData: BackupData,
        restorePreferences: Boolean,
        restoreClipboard: Boolean,
        restoreWanxiang: Boolean,
        restoreCustomTasks: Boolean,
    ) {
        val context = fragment.requireContext()

        // Use withLoadingDialog with 0 threshold to show immediately
        fragment.lifecycleScope.withLoadingDialog(context, threshold = 0L) {
            try {
                withContext(Dispatchers.IO) {
                    BackupManager.restoreBackup(
                        backupData,
                        restorePreferences = restorePreferences,
                        restoreClipboard = restoreClipboard,
                        restoreWanxiang = restoreWanxiang,
                        restoreCustomTasks = restoreCustomTasks,
                    ).getOrThrow()
                }

                withContext(Dispatchers.Main) {
                    context.toast(R.string.restore_success)
                    if (restorePreferences) {
                        showRestartDialog(context)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    context.toast(R.string.restore_failure)
                }
            }
        }
    }

    private fun showRestartDialog(context: Context) {
        context.confirmDialog(
            title = R.string.restart_app,
            message = R.string.restart_app_hint,
            positiveText = R.string.restart,
            onConfirm = { restartApp(context) },
        )
    }

    private fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
