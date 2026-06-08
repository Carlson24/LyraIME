/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.util.AttributeSet
import com.osfans.trime.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * SphereRippleView - 球形中心发散声波样式（低功耗、线程冬眠优化版）
 * 中心一个克制果冻球，声音转化为向四周扩散、边缘渐隐的涟漪声波。
 */
class SphereRippleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RenderView(context, attrs, defStyleAttr) {

    private companion object {
        private const val DEFAULT_OFFSET_SPEED = 200f
        private const val DEFAULT_SENSIBILITY = 5
        private const val SILENT_IDLE_THRESHOLD = 30
    }

    private var offsetSpeed = DEFAULT_OFFSET_SPEED
    private var volume = 0f
    private var targetVolume = 0
    private var perVolume = 0f
    private var sensibility = DEFAULT_SENSIBILITY

    private val renderLock = Object()
    private var silentFrameCount = 0

    @Volatile
    private var isEngineSleeping = false

    @Volatile
    private var isViewAttached = false

    @Volatile
    private var _backGroundColor = Color.WHITE

    @Volatile
    private var _lineColor = Color.parseColor("#2ED184")

    var backGroundColor: Int
        get() = _backGroundColor
        set(value) {
            _backGroundColor = value
            isTransparentMode = value == Color.TRANSPARENT
        }

    var lineColor: Int
        get() = _lineColor
        set(value) {
            _lineColor = value
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    private var viewWidth = 0
    private var viewHeight = 0
    private var centerX = 0f
    private var centerY = 0f

    private var baseRadius = 0f
    private var maxRippleRadius = 0f

    private val rippleProgress = floatArrayOf(0.0f, 0.33f, 0.66f)
    private var isTransparentMode = false

    init {
        initAttr(attrs)
    }

    private fun initAttr(attrs: AttributeSet?) {
        val t = context.obtainStyledAttributes(attrs, R.styleable.WaveLineView)
        _backGroundColor = t.getColor(R.styleable.WaveLineView_wlvBackgroundColor, Color.WHITE)
        _lineColor = t.getColor(R.styleable.WaveLineView_wlvLineColor, Color.parseColor("#2ED184"))
        offsetSpeed = t.getFloat(R.styleable.WaveLineView_wlvMoveSpeed, DEFAULT_OFFSET_SPEED)
        sensibility = t.getInt(R.styleable.WaveLineView_wlvSensibility, DEFAULT_SENSIBILITY)
        isTransparentMode = _backGroundColor == Color.TRANSPARENT
        t.recycle()

        setZOrderOnTop(true)
        holder?.setFormat(PixelFormat.TRANSLUCENT)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.viewWidth = w
        this.viewHeight = h
        if (w == 0 || h == 0) return

        centerX = w / 2f
        centerY = h / 2f

        baseRadius = min(w, h) * 0.12f
        maxRippleRadius = min(w, h) * 0.55f
        perVolume = sensibility * 0.45f
    }

    override fun doDrawBackground(canvas: Canvas) {
        if (isTransparentMode) {
            canvas.drawColor(_backGroundColor, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(_backGroundColor)
        }
    }

    override fun onRender(canvas: Canvas, millisPassed: Long) {
        if (viewWidth == 0 || viewHeight == 0 || baseRadius <= 0) return

        synchronized(renderLock) {
            while (isEngineSleeping && isViewAttached) {
                try {
                    renderLock.wait()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }

        softerChangeVolume()
        val haloTimeFactor = millisPassed / offsetSpeed

        if (targetVolume == 0 && volume == 0f) {
            silentFrameCount++
            if (silentFrameCount >= SILENT_IDLE_THRESHOLD) {
                drawStaticScene(canvas, haloTimeFactor)

                synchronized(renderLock) {
                    isEngineSleeping = true
                }
                silentFrameCount = 0
                return
            }
        } else {
            silentFrameCount = 0
        }

        drawActiveScene(canvas, haloTimeFactor)
    }

    private fun drawActiveScene(canvas: Canvas, haloTimeFactor: Float) {
        val vPercent = this.volume * 0.01f

        val r = Color.red(_lineColor)
        val g = Color.green(_lineColor)
        val b = Color.blue(_lineColor)

        paint.style = Paint.Style.STROKE
        paint.shader = null

        val dynamicSpeed = 0.005f + (0.023f * vPercent)
        val currentMaxRadius =
            baseRadius + (maxRippleRadius - baseRadius) * (0.25f + 0.75f * vPercent)

        for (i in rippleProgress.indices) {
            rippleProgress[i] += dynamicSpeed
            if (rippleProgress[i] > 1.0f) {
                rippleProgress[i] = 0.0f
            }

            val progress = rippleProgress[i]
            val rippleRadius = baseRadius + (currentMaxRadius - baseRadius) * progress
            val alphaFactor = 1.0f - progress

            var rippleAlpha = (160 * alphaFactor * (0.15f + 0.85f * vPercent)).toInt()
            if (rippleAlpha < 0) rippleAlpha = 0

            paint.strokeWidth = 3.5f * (1.0f - progress * 0.6f)
            paint.color = Color.argb(rippleAlpha, r, g, b)

            canvas.drawCircle(centerX, centerY, rippleRadius, paint)
        }

        paint.style = Paint.Style.FILL
        val haloPulse = sin(haloTimeFactor * 1.2f).toFloat() * (baseRadius * 0.06f)
        paint.color = Color.argb(35, r, g, b)
        canvas.drawCircle(centerX, centerY, baseRadius * 1.3f + haloPulse, paint)

        paint.color = Color.argb(166, r, g, b)
        canvas.drawCircle(centerX, centerY, baseRadius, paint)
    }

    private fun drawStaticScene(canvas: Canvas, haloTimeFactor: Float) {
        val r = Color.red(_lineColor)
        val g = Color.green(_lineColor)
        val b = Color.blue(_lineColor)

        paint.style = Paint.Style.FILL
        val haloPulse = sin(haloTimeFactor * 1.2f).toFloat() * (baseRadius * 0.06f)
        paint.color = Color.argb(35, r, g, b)
        canvas.drawCircle(centerX, centerY, baseRadius * 1.3f + haloPulse, paint)

        paint.color = Color.argb(166, r, g, b)
        canvas.drawCircle(centerX, centerY, baseRadius, paint)
    }

    private fun softerChangeVolume() {
        val localPerVolume = perVolume
        val localTargetVolume = targetVolume
        if (volume < localTargetVolume - localPerVolume) {
            volume += localPerVolume
        } else if (volume > localTargetVolume + localPerVolume) {
            volume = (volume - localPerVolume).coerceAtLeast(0f)
        } else {
            volume = localTargetVolume.toFloat()
        }
    }

    override fun stopAnim() {
        super.stopAnim()
        clearDraw()
        synchronized(renderLock) {
            isEngineSleeping = false
            renderLock.notifyAll()
        }
    }

    fun clearDraw() {
        var canvas: Canvas? = null
        try {
            canvas = holder?.lockCanvas(null)
            canvas?.let {
                if (isTransparentMode) {
                    it.drawColor(_backGroundColor, PorterDuff.Mode.CLEAR)
                } else {
                    it.drawColor(_backGroundColor)
                }
            }
        } catch (ignored: Exception) {
        } finally {
            canvas?.let { holder?.unlockCanvasAndPost(it) }
        }
    }

    fun setMoveSpeed(moveSpeed: Float) {
        this.offsetSpeed = moveSpeed
    }

    fun setWaveformColor(color: Int) {
        this.lineColor = color
    }

    fun setVolume(volume: Int) {
        val inputVolume = volume.coerceIn(0, 100)
        if (abs((this.targetVolume - inputVolume).toFloat()) > perVolume || inputVolume > 0) {
            this.targetVolume = inputVolume + 20
            checkVolumeValue()

            if (inputVolume > 0 && isEngineSleeping) {
                synchronized(renderLock) {
                    isEngineSleeping = false
                    renderLock.notifyAll()
                }
            }
        }
    }

    fun setSensibility(sensibility: Int) {
        this.sensibility = sensibility
        checkSensibilityValue()
    }

    override fun release() {
        stopAnim()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isViewAttached = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isViewAttached = false
        release()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            stopAnim()
        } else {
            synchronized(renderLock) {
                isEngineSleeping = false
                renderLock.notifyAll()
            }
            startAnim()
        }
    }

    private fun checkVolumeValue() {
        if (targetVolume > 100) targetVolume = 100
        if (targetVolume < 0) targetVolume = 0
    }

    private fun checkSensibilityValue() {
        if (sensibility > 10) sensibility = 10
        if (sensibility < 1) sensibility = 1
    }
}
