package com.osfans.trime.ime.voice

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import jaygoo.widget.wlv.WaveLineView
import timber.log.Timber
import kotlin.math.ln

/**
 * 实时音频波形视图（封装 WaveLineView）。
 * API 与说点啥/小企鹅保持一致：start/stop/updateAmplitude/setWaveformColor。
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val AMPLITUDE_GAIN = 1.6
        private const val AMPLITUDE_LOG_K = 18.0
    }

    private var isActive = false

    private val waveView: WaveLineView =
        WaveLineView(context).apply {
            setBackGroundColor(Color.TRANSPARENT)
            setSensibility(10)
            setMoveSpeed(250f)
        }

    init {
        addView(waveView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        visibility = View.GONE
    }

    fun setWaveformColor(@ColorInt color: Int) {
        waveView.setLineColor(color)
        invalidate()
    }

    fun updateAmplitude(amplitude: Float) {
        if (!isActive) return
        waveView.setVolume(amplitudeToVolume(amplitude))
    }

    private fun amplitudeToVolume(amplitude: Float): Int {
        val a = (amplitude.coerceIn(0f, 1f).toDouble() * AMPLITUDE_GAIN).coerceIn(0.0, 1.0)
        val mapped = ln(1.0 + AMPLITUDE_LOG_K * a) / ln(1.0 + AMPLITUDE_LOG_K)
        return (mapped * 100.0).toInt().coerceIn(0, 100)
    }

    fun start() {
        if (isActive) return
        isActive = true
        runCatching { waveView.startAnim() }
            .onFailure { Timber.w(it, "WaveformView startAnim failed") }
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        runCatching { waveView.stopAnim() }
            .onFailure { Timber.w(it, "WaveformView stopAnim failed") }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        runCatching { waveView.onWindowFocusChanged(hasWindowFocus) }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this && visibility != View.VISIBLE) {
            if (isActive) stop()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        runCatching {
            stop()
            waveView.release()
        }.onFailure { Timber.w(it, "WaveformView release failed") }
    }
}
