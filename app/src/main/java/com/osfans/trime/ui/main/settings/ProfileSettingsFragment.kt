/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.R
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.backup.BackupRestoreDialog
import com.osfans.trime.data.backup.WebDavSync
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.packaging.SchemaPackageManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.common.buildDialog
import com.osfans.trime.ui.common.pickMultiple
import com.osfans.trime.ui.common.withLoadingDialog
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.buildAppDataFolderIntent
import com.osfans.trime.util.customFormatTimeInDefault
import com.osfans.trime.util.getFileFromUri
import com.osfans.trime.util.getUriForFile
import com.osfans.trime.util.toast
import com.osfans.trime.worker.BackgroundSyncWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.resources.styledColor
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.core.add
import splitties.views.dsl.core.editText
import splitties.views.dsl.core.imageButton
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import splitties.views.topPadding
import java.io.File

class ProfileSettingsFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs = AppPrefs.Companion.defaultInstance().profile
    private val backgroundSyncEnable = prefs.periodicBackgroundSync
    private val lastSyncTime by prefs.lastBackgroundSyncTime
    private val lastSyncStatus by prefs.lastBackgroundSyncStatus

    private val webdavEnabled = prefs.webdavEnabled

    private val backupRestoreDialog = BackupRestoreDialog(this)

    private val onBackgroundSyncEnable = PreferenceDelegate.OnChangeListener<Boolean> { _, v ->
        editSyncTimePreference.isEnabled = v
        if (v) {
            BackgroundSyncWork.scheduleNext(requireContext())
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    BackgroundSyncWork.backupSettingsToSyncDir()
                }
                viewModel.rime.launchOnReady { it.syncUserData() }
                withContext(Dispatchers.IO) {
                    BackgroundSyncWork.syncWebDavIfEnabled()
                }
            }
        } else {
            BackgroundSyncWork.scheduleNext(requireContext())
        }
    }

    private val onWebdavEnable = PreferenceDelegate.OnChangeListener<Boolean> { _, v ->
        webdavUrlPreference.isEnabled = v
        webdavUsernamePreference.isEnabled = v
        webdavPasswordPreference.isEnabled = v
        webdavRemotePathPreference.isEnabled = v
        webdavTestPreference.isEnabled = v
        webdavSyncPreference.isEnabled = v
    }

    private val onSyncTimeChange =
        PreferenceDelegate.OnChangeListener<String> { _, _ ->
            if (backgroundSyncEnable.getValue()) {
                BackgroundSyncWork.scheduleNext(requireContext())
            }
        }

    private val onUserDataDirChange = PreferenceDelegate.OnChangeListener<String> { _, newValue ->
        findPreference<Preference>(AppPrefs.Profile.USER_DATA_DIR)?.summary = newValue
    }

    private lateinit var browseLauncher: ActivityResultLauncher<Uri?>
    private var launcherResultCallback: ((path: String) -> Unit)? = null

    private lateinit var editSyncTimePreference: TimePreference
    private lateinit var webdavUrlPreference: EditTextPreference
    private lateinit var webdavUsernamePreference: EditTextPreference
    private lateinit var webdavPasswordPreference: EditTextPreference
    private lateinit var webdavRemotePathPreference: EditTextPreference
    private lateinit var webdavTestPreference: Preference
    private lateinit var webdavSyncPreference: Preference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backupRestoreDialog.setupLaunchers()
        prefs.periodicBackgroundSync.registerOnChangeListener(onBackgroundSyncEnable)
        prefs.periodicBackgroundSyncTime.registerOnChangeListener(onSyncTimeChange)
        prefs.userDataDir.registerOnChangeListener(onUserDataDirChange)
        prefs.webdavEnabled.registerOnChangeListener(onWebdavEnable)

        browseLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it ?: return@registerForActivityResult
            val uri =
                DocumentsContract.buildDocumentUriUsingTree(
                    it,
                    DocumentsContract.getTreeDocumentId(it),
                ) ?: return@registerForActivityResult
            val path = requireContext().getFileFromUri(uri)?.absolutePath ?: return@registerForActivityResult
            launcherResultCallback?.invoke(path)
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addCategory(R.string.storage) {
                isIconSpaceReserved = false
                addPreference(
                    Preference(requireContext()).apply {
                        key = AppPrefs.Profile.USER_DATA_DIR
                        isIconSpaceReserved = false
                        setTitle(R.string.user_data_dir)
                        setDefaultValue(DataManager.defaultDataDir.absolutePath)
                        summary = prefs.userDataDir.getValue()
                        setOnPreferenceClickListener {
                            val dirNameText = ctx.editText {
                                setText(prefs.userDataDir.getValue())
                            }
                            launcherResultCallback = { path ->
                                dirNameText.setText(path)
                            }
                            val browseButton = ctx.imageButton {
                                imageDrawable = ctx.drawable(R.drawable.ic_baseline_more_horiz_24)!!.apply {
                                    setTint(styledColor(android.R.attr.colorControlNormal))
                                }
                                setOnClickListener {
                                    val currentValue = prefs.userDataDir.getValue()
                                    browseLauncher.launch(ctx.getUriForFile(File(currentValue)))
                                }
                            }
                            val dialogContent = ctx.constraintLayout {
                                layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
                                topPadding = dp(8)
                                add(
                                    dirNameText,
                                    lParams(matchConstraints, wrapContent) {
                                        centerVertically()
                                        startOfParent(dp(20))
                                        endToStartOf(browseButton, dp(2))
                                    },
                                )
                                val size = dp(48)
                                add(
                                    browseButton,
                                    lParams(size, size) {
                                        centerVertically()
                                        startToEndOf(dirNameText, dp(2))
                                        endOfParent(dp(20))
                                    },
                                )
                            }
                            ctx.buildDialog(R.string.user_data_dir)
                                .setView(dialogContent)
                                .setPositiveButton(android.R.string.ok) { _, _ ->
                                    prefs.userDataDir.setValue(dirNameText.text.toString())
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .setNeutralButton(R.string.default_) { _, _ ->
                                    prefs.userDataDir.setValue(DataManager.defaultDataDir.absolutePath)
                                }
                                .setOnDismissListener {
                                    launcherResultCallback = null
                                }
                                .show()
                            true
                        }
                    },
                )
                addPreference(R.string.app_data_folder, R.string.app_data_folder_summary) {
                    startActivity(requireContext().buildAppDataFolderIntent())
                }
            }
            addCategory(R.string.synchronization) {
                isIconSpaceReserved = false
                addPreference(R.string.sync_user_data_immediately) {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            BackgroundSyncWork.backupSettingsToSyncDir()
                        }
                        viewModel.rime.launchOnReady { it.syncUserData() }
                        withContext(Dispatchers.IO) {
                            BackgroundSyncWork.syncWebDavIfEnabled()
                        }
                    }
                }
                addPreference(
                    SwitchPreferenceCompat(ctx).apply {
                        key = AppPrefs.Profile.PERIODIC_BACKGROUND_SYNC
                        isIconSpaceReserved = false
                        setTitle(R.string.periodic_background_sync)
                        setDefaultValue(false)
                        summaryProvider = Preference.SummaryProvider<SwitchPreferenceCompat> {
                            if (backgroundSyncEnable.getValue()) {
                                val lastTime: String
                                val lastStatus: String
                                if (lastSyncTime != 0L) {
                                    lastTime = customFormatTimeInDefault("yyyy-MM-dd HH:mm", lastSyncTime)
                                    lastStatus = getString(if (lastSyncStatus) R.string.success else R.string.failure)
                                } else {
                                    lastTime = "N/A"
                                    lastStatus = "N/A"
                                }
                                getString(
                                    R.string.periodic_background_sync_status,
                                    lastTime,
                                    lastStatus,
                                )
                            } else {
                                ""
                            }
                        }
                    },
                )
                addPreference(
                    TimePreference(ctx).apply {
                        editSyncTimePreference = this
                        key = AppPrefs.Profile.PERIODIC_BACKGROUND_SYNC_TIME
                        isIconSpaceReserved = false
                        setTitle(R.string.periodic_background_sync_time)
                        setDefaultTime("02:00")
                        isEnabled = backgroundSyncEnable.getValue()
                        setOnPreferenceClickListener {
                            val timeParts = time.split(":")
                            val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: 2
                            val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(ctx, { _, h, m ->
                                setTimeAndPersist(String.format("%02d:%02d", h, m))
                            }, hour, minute, true).show()
                            true
                        }
                    },
                )
                addPreference(
                    SwitchPreferenceCompat(ctx).apply {
                        key = AppPrefs.Profile.WEBDAV_ENABLED
                        isIconSpaceReserved = false
                        setTitle(R.string.webdav_enabled)
                        setSummary(R.string.webdav_sync_hint)
                        setDefaultValue(false)
                    },
                )
                addPreference(
                    EditTextPreference(ctx).apply {
                        webdavUrlPreference = this
                        key = AppPrefs.Profile.WEBDAV_URL
                        isIconSpaceReserved = false
                        setTitle(R.string.webdav_url)
                        setDialogTitle(R.string.webdav_url)
                        setDefaultValue("")
                        isEnabled = webdavEnabled.getValue()
                        summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                            it.text?.takeIf { t -> t.isNotEmpty() } ?: getString(R.string.disable)
                        }
                    },
                )
                addPreference(
                    EditTextPreference(ctx).apply {
                        webdavUsernamePreference = this
                        key = AppPrefs.Profile.WEBDAV_USERNAME
                        isIconSpaceReserved = false
                        setTitle(R.string.webdav_username)
                        setDialogTitle(R.string.webdav_username)
                        setDefaultValue("")
                        isEnabled = webdavEnabled.getValue()
                        summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                            it.text?.takeIf { t -> t.isNotEmpty() } ?: getString(R.string.disable)
                        }
                    },
                )
                addPreference(
                    EditTextPreference(ctx).apply {
                        webdavPasswordPreference = this
                        key = AppPrefs.Profile.WEBDAV_PASSWORD
                        isIconSpaceReserved = false
                        setTitle(R.string.webdav_password)
                        setDialogTitle(R.string.webdav_password)
                        setDefaultValue("")
                        isEnabled = webdavEnabled.getValue()
                        summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                            it.text?.takeIf { t -> t.isNotEmpty() }
                                ?.let { "••••••••" } ?: getString(R.string.disable)
                        }
                    },
                )
                addPreference(
                    EditTextPreference(ctx).apply {
                        webdavRemotePathPreference = this
                        key = AppPrefs.Profile.WEBDAV_REMOTE_PATH
                        isIconSpaceReserved = false
                        setTitle(R.string.webdav_remote_path)
                        setDialogTitle(R.string.webdav_remote_path)
                        setDefaultValue("Rime")
                        isEnabled = webdavEnabled.getValue()
                        summaryProvider = Preference.SummaryProvider<EditTextPreference> {
                            it.text?.takeIf { t -> t.isNotEmpty() } ?: getString(R.string.disable)
                        }
                    },
                )
                addPreference(
                    Preference(requireContext()).apply {
                        webdavTestPreference = this
                        key = "webdav_test_connection"
                        isIconSpaceReserved = false
                        setTitle(R.string.webdav_test_connection)
                        isEnabled = webdavEnabled.getValue()
                        setOnPreferenceClickListener {
                            if (prefs.webdavUrl.getValue().isEmpty()) {
                                ctx.toast(R.string.webdav_invalid_url)
                            } else {
                                lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        WebDavSync.testConnection()
                                    }.fold(
                                        onSuccess = { ctx.toast(R.string.webdav_test_success) },
                                        onFailure = {
                                            ctx.toast(ctx.getString(R.string.webdav_test_failure, it.message))
                                        },
                                    )
                                }
                            }
                            true
                        }
                    },
                )
                addPreference(
                    Preference(requireContext()).apply {
                        webdavSyncPreference = this
                        key = "webdav_sync_now"
                        isIconSpaceReserved = false
                        setTitle(R.string.webdav_sync_now)
                        setSummary(R.string.webdav_sync_hint)
                        isEnabled = webdavEnabled.getValue()
                        setOnPreferenceClickListener {
                            ctx.buildDialog(R.string.webdav_sync_direction)
                                .setItems(
                                    arrayOf(
                                        getString(R.string.webdav_push),
                                        getString(R.string.webdav_pull),
                                    ),
                                ) { _, which ->
                                    lifecycleScope.launch {
                                        val result =
                                            withContext(Dispatchers.IO) {
                                                if (which == 0) {
                                                    WebDavSync.pushToServer()
                                                } else {
                                                    WebDavSync.pullFromServer()
                                                }
                                            }
                                        result.fold(
                                            onSuccess = { count ->
                                                ctx.toast(
                                                    getString(R.string.webdav_sync_success) +
                                                        " ($count files)",
                                                )
                                            },
                                            onFailure = {
                                                ctx.toast(
                                                    ctx.getString(
                                                        R.string.webdav_sync_failure,
                                                        it.message,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                            true
                        }
                    },
                )
            }
            addCategory(R.string.maintenance) {
                isIconSpaceReserved = false
                addPreference(R.string.reset, R.string.reset_hint) {
                    val pkgId = SchemaPackageManager.activePackageId
                    val items = ctx.assets.list(pkgId) ?: return@addPreference
                    val checked = BooleanArray(items.size) { false }
                    ctx.pickMultiple(
                        title = R.string.reset,
                        items = items,
                        checked = checked,
                    )
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            var res = true
                            lifecycleScope.withLoadingDialog(context) {
                                withContext(Dispatchers.IO) {
                                    res =
                                        items
                                            .filterIndexed { index, _ -> checked[index] }
                                            .fold(true) { acc, asset ->
                                                val destPath =
                                                    DataManager.sharedDataDir.resolve(asset).absolutePath
                                                ResourceUtils
                                                    .copyFile("$pkgId/$asset", destPath)
                                                    .fold({ acc and true }, { acc and false })
                                            }
                                }
                                ctx.toast(if (res) R.string.reset_success else R.string.reset_failure)
                            }
                        }.show()
                }
            }
            addCategory(R.string.backup_and_restore) {
                isIconSpaceReserved = false
                addPreference(R.string.backup, R.string.backup_hint) {
                    backupRestoreDialog.showBackupDialog()
                }
                addPreference(R.string.restore, R.string.restore_hint) {
                    backupRestoreDialog.showRestoreDialog()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.periodicBackgroundSync.unregisterOnChangeListener(onBackgroundSyncEnable)
        prefs.periodicBackgroundSyncTime.unregisterOnChangeListener(onSyncTimeChange)
        prefs.userDataDir.unregisterOnChangeListener(onUserDataDirChange)
        prefs.webdavEnabled.unregisterOnChangeListener(onWebdavEnable)
    }
}
