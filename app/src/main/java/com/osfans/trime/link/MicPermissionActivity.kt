package com.osfans.trime.link

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 麦克风权限请求 Activity（用于从 InputMethodService 场景触发运行时权限弹窗）。
 */
class MicPermissionActivity : ComponentActivity() {
    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequest()
    }

    private fun maybeRequest() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            finish()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

