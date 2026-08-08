/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.baselineToBaselineOf
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.bottomToTopOf
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToBottomOf
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.horizontalPadding

class CandidateItemUi(
    override val ctx: Context,
    private val theme: Theme,
    var contentGravity: Int = Gravity.CENTER,
    showLabel: Boolean = true,
) : Ui {

    private val showLabel = showLabel

    private val textSize = theme.generalStyle.fonts.candidate_size
    private val commentSize = theme.generalStyle.fonts.comment_size

    private val textFont = FontManager.getTypeface("candidate_font")
    private val commentFont = FontManager.getTypeface("comment_font")
    private val labelFont = FontManager.getTypeface("label_font")

    private val textColor = ColorManager.getColor("candidate_text_color")
    private val commentColor = ColorManager.getColor("comment_text_color")
    private val labelColor = ColorManager.getColor("label_color")

    private val hlCommentColor = ColorManager.getColor("hilited_comment_text_color")
    private val hlTextColor = ColorManager.getColor("hilited_candidate_text_color")
    private val hlLabelColor = ColorManager.getColor("hilited_label_color")
    private val hlBackColor = ColorManager.getColor("hilited_candidate_back_color")

    private val commentPosition = theme.generalStyle.commentPosition
    private val commentVerticalBias = theme.generalStyle.commentVerticalBias
    private val candidateTextVerticalBias = theme.generalStyle.candidateTextVerticalBias

    private val text =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            this.textSize = this@CandidateItemUi.textSize
            typeface = textFont
            fontFeatureSettings = FontManager.fontFeatureSettings
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    private val comment =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            this.textSize = commentSize
            typeface = commentFont
            fontFeatureSettings = FontManager.fontFeatureSettings
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
        }

    private val label =
        view(::AutoScaleTextView) {
            id = View.generateViewId()
            this.textSize = this@CandidateItemUi.theme.generalStyle.fonts.candidate_label_size
            typeface = labelFont
            fontFeatureSettings = FontManager.fontFeatureSettings
            isSingleLine = true
            gravity = gravityCenter
            scaleMode = AutoScaleTextView.Mode.Proportional
            visibility = if (showLabel) View.VISIBLE else View.GONE
        }

    private val content = constraintLayout {
        horizontalPadding = dp(theme.generalStyle.candidatePadding)
        when (commentPosition) {
            GeneralStyle.CommentPosition.RIGHT -> {
                add(
                    label,
                    lParams(wrapContent, wrapContent) {
                        centerVertically()
                        startOfParent()
                        endToStartOf(text)
                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    },
                )
                add(
                    text,
                    lParams(wrapContent, wrapContent) {
                        centerVertically()
                        startToEndOf(label, ctx.dp(1))
                        endToStartOf(comment)
                        constrainedWidth = true
                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, wrapContent) {
                        startToEndOf(text, ctx.dp(1))
                        endOfParent()
                        baselineToBaselineOf(text)
                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    },
                )
            }
            GeneralStyle.CommentPosition.TOP -> {
                add(
                    comment,
                    lParams(wrapContent, matchConstraints) {
                        matchConstraintPercentHeight = 0.4f
                        topOfParent()
                        centerHorizontally()
                        bottomToTopOf(label)
                    },
                )
                add(
                    label,
                    lParams(wrapContent, wrapContent) {
                        bottomOfParent()
                        topToBottomOf(comment)
                        centerVertically()
                        startOfParent()
                        endToStartOf(text)
                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    },
                )
                add(
                    text,
                    lParams(wrapContent, wrapContent) {
                        bottomOfParent()
                        topToBottomOf(comment)
                        centerVertically()
                        startToEndOf(label, ctx.dp(1))
                        endOfParent()
                        horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED
                    },
                )
            }
            GeneralStyle.CommentPosition.OVERLAY -> {
                add(
                    label,
                    lParams(wrapContent, wrapContent) {
                        centerInParent()
                        verticalBias = candidateTextVerticalBias
                    },
                )
                add(
                    text,
                    lParams(wrapContent, wrapContent) {
                        centerInParent()
                        verticalBias = candidateTextVerticalBias
                    },
                )
                add(
                    comment,
                    lParams(wrapContent, wrapContent) {
                        centerInParent()
                        verticalBias = commentVerticalBias
                    },
                )
            }
        }
    }

    override val root = view(::GestureFrame) {
        foreground = null
        /**
         * candidate long press feedback is handled by `showCandidateActionMenu`
         */
        add(
            content,
            lParams(wrapContent, dp(theme.generalStyle.candidateViewHeight)) {
                gravity = contentGravity
            },
        )
    }

    @SuppressLint("UseKtx")
    fun update(
        item: CandidateProto,
        highlighted: Boolean,
    ) {
        val tColor = if (highlighted) hlTextColor else textColor
        val cColor = if (highlighted) hlCommentColor else commentColor
        val lColor = if (highlighted) hlLabelColor else labelColor
        val cornerRadius = ctx.dp(theme.generalStyle.candidateCornerRadius)
        val contentColor = if (highlighted) hlBackColor else Color.TRANSPARENT

        content.background = roundedRippleDrawable(hlBackColor, cornerRadius, contentColor)

        if (showLabel) {
            label.text = item.label
            label.setTextColor(lColor)
        }

        text.text = item.text
        text.setTextColor(tColor)

        val commentText = item.comment
        comment.text = commentText
        comment.setTextColor(cColor)
        comment.isVisible = commentText.isNotEmpty()
    }
}
