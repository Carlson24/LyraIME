// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    val toolbarTitle = MutableLiveData<String>()

    val topOptionsMenu = MutableLiveData<Boolean>()

    val rime = RimeDaemon.createSession(javaClass.name)

    val restartBackgroundSyncWork = MutableLiveData(false)

    val toolbarEditButtonVisible = MutableLiveData(false)

    val toolbarEditButtonOnClickListener = MutableLiveData<(() -> Unit)?>()

    val toolbarEditButtonIcon = MutableLiveData(R.drawable.ic_baseline_edit_24)

    val toolbarDeleteButtonOnClickListener = MutableLiveData<(() -> Unit)?>()

    val toolbarDeleteButtonIcon = MutableLiveData(R.drawable.ic_baseline_delete_24)

    fun setToolbarTitle(title: String) {
        toolbarTitle.value = title
    }

    fun enableTopOptionsMenu() {
        topOptionsMenu.value = true
    }

    fun disableTopOptionsMenu() {
        topOptionsMenu.value = false
    }

    fun enableToolbarEditButton(
        visible: Boolean = true,
        icon: Int = R.drawable.ic_baseline_edit_24,
        onClick: () -> Unit,
    ) {
        toolbarEditButtonIcon.value = icon
        toolbarEditButtonOnClickListener.value = onClick
        toolbarEditButtonVisible.value = visible
    }

    fun disableToolbarEditButton() {
        toolbarEditButtonOnClickListener.value = null
        hideToolbarEditButton()
    }

    fun hideToolbarEditButton() {
        toolbarEditButtonVisible.value = false
    }

    fun showToolbarEditButton() {
        toolbarEditButtonVisible.value = true
    }

    fun enableToolbarDeleteButton(
        icon: Int = R.drawable.ic_baseline_delete_24,
        onClick: () -> Unit,
    ) {
        toolbarDeleteButtonIcon.value = icon
        toolbarDeleteButtonOnClickListener.value = onClick
    }

    fun disableToolbarDeleteButton() {
        toolbarDeleteButtonOnClickListener.value = null
    }

    override fun onCleared() {
        RimeDaemon.destroySession(javaClass.name)
    }
}
