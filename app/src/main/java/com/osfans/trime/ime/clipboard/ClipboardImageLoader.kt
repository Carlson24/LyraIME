/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.clipboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import com.osfans.trime.data.db.DatabaseBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

suspend fun DatabaseBean.loadThumbnailBitmap(context: Context): Bitmap? {
    if (!isUriEntry() || !type.startsWith("image/")) return null
    val originalUri = runCatching { text.toUri() }.getOrNull() ?: run {
        Timber.w("loadThumbnailBitmap: failed to parse URI from entry.text")
        return null
    }

    return withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(originalUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Timber.w("loadThumbnailBitmap: bounds invalid (${bounds.outWidth}x${bounds.outHeight})")
                return@runCatching null
            }
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 192, 192)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            resolver.openInputStream(originalUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.onFailure { e ->
            if (e is SecurityException) {
                Timber.d("loadThumbnailBitmap: permission denied for URI: $originalUri")
            } else {
                Timber.w(e, "loadThumbnailBitmap: failed for URI: $originalUri")
            }
        }.getOrNull()
    }
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentHeight / 2 >= reqHeight && currentWidth / 2 >= reqWidth) {
        currentHeight /= 2
        currentWidth /= 2
        sampleSize *= 2
    }
    return sampleSize
}
