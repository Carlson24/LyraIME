/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.graphics.Bitmap
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import androidx.core.net.toUri
import com.osfans.trime.R
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.ime.clipboard.loadThumbnailBitmap
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.rippleDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalMargin
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable

class ClipboardSuggestionUi(
    override val ctx: Context,
) : Ui {
    private val icon =
        imageView {
            imageDrawable =
                drawable(R.drawable.ic_baseline_clipboard_24)!!.apply {
                    setTint(ColorManager.getColor("candidate_text_color"))
                }
        }

    private val imageView = imageView {
        visibility = View.GONE
    }

    val text =
        textView {
            isSingleLine = true
            maxWidth = dp(220)
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(ColorManager.getColor("candidate_text_color"))
        }

    val dismiss =
        imageView {
            isFocusable = false
            imageDrawable = drawable(R.drawable.ic_baseline_outline_cancel_24)!!.apply {
                setTint(ColorManager.getColor("candidate_text_color"))
            }
        }

    private val layout =
        horizontalLayout {
            val spacing = dp(4)
            gravity = Gravity.CENTER_VERTICAL
            add(
                icon,
                lParams(dp(20), dp(20)) {
                    rightMargin = spacing
                },
            )
            add(
                imageView,
                lParams(dp(32), dp(32)) {
                    rightMargin = spacing
                },
            )
            add(
                text,
                lParams(wrapContent, wrapContent) {
                    rightMargin = spacing
                },
            )
            add(
                dismiss,
                lParams(dp(20), dp(20)),
            )
        }

    val suggestionView = GestureFrame(ctx).apply {
        add(layout, lParams(wrapContent, matchParent))
        background = rippleDrawable(ColorManager.getColor("hilited_candidate_back_color"))
    }

    override val root =
        constraintLayout {
            add(
                suggestionView,
                lParams(wrapContent, matchConstraints) {
                    centerInParent()
                    verticalMargin = dp(4)
                },
            )
        }

    private var isImage: Boolean = false
    private var thumbnailBitmap: Bitmap? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    var currentBean: DatabaseBean? = null
        private set

    fun updateClipboardContent(bean: DatabaseBean?) {
        currentBean = bean
        if (bean == null) {
            text.text = ""
            isImage = false
            thumbnailBitmap = null
            updateDisplay()
            return
        }

        isImage = bean.isUriEntry() && bean.type.startsWith("image/")

        if (isImage) {
            loadThumbnail(bean)
        } else {
            text.text = bean.text.take(42)
            thumbnailBitmap = null
            updateDisplay()
        }
    }

    private fun updateDisplay() {
        if (isImage) {
            imageView.setImageBitmap(thumbnailBitmap)
            imageView.visibility = if (thumbnailBitmap != null) View.VISIBLE else View.GONE
            text.visibility = View.GONE
        } else {
            text.visibility = View.VISIBLE
            imageView.visibility = View.GONE
        }
    }

    private fun loadThumbnail(bean: DatabaseBean) {
        scope.launch {
            thumbnailBitmap = withContext(Dispatchers.IO) {
                bean.loadThumbnailBitmap(ctx)
            }
            updateDisplay()
        }
    }
}
