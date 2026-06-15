package com.osfans.trime.ime.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import com.osfans.trime.R
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * ParticleWaveView - 粒子喷射环风格声波动效（低功耗、硬件加速友好版）
 * 中心多边形核心 + 声音驱动的喷射几何粒子 + 旋转能量环
 */
class ParticleWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RenderView(context, attrs, defStyleAttr) {

    private companion object {
        private const val SILENT_IDLE_THRESHOLD = 30 // 约 500ms 无声则判定冬眠
        private const val RAD_CONVERT = PI.toFloat() / 180f // 缓存弧度转换常数，免去高频双精度转换
        private const val MIN_ACTIVE_VOLUME = 0.1f // 正常运行时的最低视觉音量底限

        // 粒子形状常量
        private const val SHAPE_TRIANGLE = 0
        private const val SHAPE_SQUARE = 1
    }

    private var offsetSpeed: Float = 600f

    @Volatile
    private var volume = 0f

    @Volatile
    private var targetVolume = 0

    @Volatile
    private var perVolume = 0f
    private var sensibility = 5
    private var polygonSides = 8

    // 线程阻塞锁状态机
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private val renderLock = Object()
    private var silentFrameCount = 0

    @Volatile
    private var isEngineSleeping = false

    @Volatile
    private var isViewAttached = false

    @Volatile
    private var _backGroundColor = Color.BLACK

    @Volatile
    private var _lineColor = Color.parseColor("#00D4FF")

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
            dirtyGradient = true // 颜色改变时，标记渐变对象需要刷新
        }

    // 复用图形对象，严禁在 onRender 内部实例化
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val polygonPath = Path()
    private val innerPath = Path()
    private val singleParticlePath = Path() // 专用于绘制单个三角形粒子的 Path 复用容器
    private var coreGradient: RadialGradient? = null
    private var dirtyGradient = true
    private var lastGradientRadius = 0f

    private var viewWidth = 0
    private var viewHeight = 0
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var maxRadius = 0f

    // 粒子系统
    private val particles = mutableListOf<Particle>()
    private val random = Random()
    private val particleCount = 30

    private var ringRotation = 0f
    private var coreRotation = 0f
    private var isTransparentMode = false

    init {
        initAttr(attrs)
        initParticles()
    }

    private fun initAttr(attrs: AttributeSet?) {
        val t = context.obtainStyledAttributes(attrs, R.styleable.WaveLineView)
        _backGroundColor = t.getColor(R.styleable.WaveLineView_wlvBackgroundColor, Color.BLACK)
        _lineColor = t.getColor(R.styleable.WaveLineView_wlvLineColor, Color.parseColor("#00D4FF"))
        offsetSpeed = t.getFloat(R.styleable.WaveLineView_wlvMoveSpeed, 200f)
        sensibility = t.getInt(R.styleable.WaveLineView_wlvSensibility, 5)
        polygonSides = t.getInt(R.styleable.WaveLineView_wlvPolygonSides, 8).coerceIn(6, 12)
        isTransparentMode = _backGroundColor == Color.TRANSPARENT
        t.recycle()

        setZOrderOnTop(true)
        holder?.setFormat(PixelFormat.TRANSLUCENT)
    }

    private fun initParticles() {
        particles.clear()
        for (i in 0 until particleCount) {
            particles.add(Particle().also { resetParticle(it, true) })
        }
    }

    private fun resetParticle(p: Particle, randomStart: Boolean) {
        p.angle = random.nextFloat() * 2 * PI.toFloat()
        // 计算粒子喷射的起点：在基础半径外围留出 20% 的空隙作为"一定间隔位置"
        val spawnRadius = baseRadius * 1.2f
        p.radius = if (randomStart) {
            spawnRadius + random.nextFloat() * (maxRadius - spawnRadius)
        } else {
            spawnRadius
        }
        p.speed = 0.5f + random.nextFloat() * 1.5f
        // 几何碎片稍微放大一点（3dp~7dp），视觉上形状更清晰
        p.size = 3f + random.nextFloat() * 4f
        p.life = if (randomStart) random.nextFloat() else 0f
        p.active = randomStart && random.nextFloat() > 0.7f

        // 初始化新加入的几何自旋属性
        p.shapeType = if (random.nextBoolean()) SHAPE_TRIANGLE else SHAPE_SQUARE
        p.rotation = random.nextFloat() * 360f
        p.spinSpeed = (random.nextFloat() - 0.5f) * 10f // 每帧旋转 -5° 到 +5°
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        if (w == 0 || h == 0) return

        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) * 0.12f
        maxRadius = min(w, h) * 0.48f
        perVolume = sensibility * 0.45f
        dirtyGradient = true
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

        // 性能安全熔断
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
        val vPercent = volume * 0.01f
        val timeFactor = millisPassed / offsetSpeed

        // 智能静音判定机
        if (targetVolume == 0 && volume == 0f) {
            silentFrameCount++
            if (silentFrameCount >= SILENT_IDLE_THRESHOLD) {
                drawStaticScene(canvas, timeFactor)
                synchronized(renderLock) {
                    isEngineSleeping = true
                }
                silentFrameCount = 0
                return
            }
        } else {
            silentFrameCount = 0
        }

        drawActiveScene(canvas, vPercent, timeFactor)
    }

    /**
     * 活跃状态高频全功能绘制
     */
    private fun drawActiveScene(canvas: Canvas, vPercent: Float, timeFactor: Float) {
        val r = Color.red(_lineColor)
        val g = Color.green(_lineColor)
        val b = Color.blue(_lineColor)

        // ========== 1. 外围旋转能量环 ==========
        val ringSpeed = 0.3f + vPercent * 2.7f
        ringRotation += ringSpeed
        if (ringRotation > 360f) ringRotation -= 360f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f + vPercent * 3f

        val step = if (vPercent > 0.5f) 8 else 20

        for (layer in 0 until 2) {
            val ringRadius = maxRadius * (0.50f + layer * 0.2f)
            val ringAngle = if (layer == 0) ringRotation else -ringRotation
            val ringAlpha = (80 + 120 * vPercent).toInt()
            paint.color = Color.argb(ringAlpha, r, g, b)

            var a = 0
            while (a < 360) {
                val angleRad = (ringAngle + a) * RAD_CONVERT
                val cosA = cos(angleRad)
                val sinA = sin(angleRad)

                val x1 = centerX + (ringRadius - 6) * cosA
                val y1 = centerY + (ringRadius - 6) * sinA
                val x2 = centerX + ringRadius * cosA
                val y2 = centerY + ringRadius * sinA
                canvas.drawLine(x1, y1, x2, y2, paint)
                a += step
            }

            if (vPercent > 0.6f) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(150, r, g, b)
                var a2 = 0
                val dynamicPulseAngle = timeFactor * 50
                while (a2 < 360) {
                    val angleRad = (ringAngle + a2 + dynamicPulseAngle) * RAD_CONVERT
                    val x = centerX + ringRadius * cos(angleRad)
                    val y = centerY + ringRadius * sin(angleRad)
                    canvas.drawCircle(x, y, 3f + vPercent * 5f, paint)
                    a2 += 30
                }
                paint.style = Paint.Style.STROKE
            }
        }

        // ========== 2. 粒子系统 (允许越过外围圆环版) ==========
        val spawnRadius = baseRadius * 1.2f
        // 新增：允许粒子飞行的绝对最大半径（扩大到外围环的 1.4 倍，使其能明显穿透出去）
        val particleMaxRadius = maxRadius * 1.7f
        val dynamicMaxCount = (3 + (particleCount - 3) * vPercent).toInt().coerceIn(3, particleCount)
        var currentActiveCount = 0
        for (i in 0 until particleCount) {
            if (particles[i].active) currentActiveCount++
        }
        val spawnChance = 0.03f + vPercent * 0.6f
        for (i in 0 until particleCount) {
            val p = particles[i]
            if (!p.active && currentActiveCount < dynamicMaxCount && random.nextFloat() < spawnChance) {
                p.active = true
                p.angle = random.nextFloat() * 2 * PI.toFloat()
                p.radius = spawnRadius
                p.life = 0f
                p.speed = 0.6f + vPercent * 3f + random.nextFloat()

                p.shapeType = if (random.nextBoolean()) SHAPE_TRIANGLE else SHAPE_SQUARE
                p.rotation = random.nextFloat() * 360f
                p.spinSpeed = (random.nextFloat() - 0.5f) * 10f
                currentActiveCount++
            }

            if (p.active) {
                // 粒子向外扩散
                p.radius += p.speed * (0.4f + vPercent)
                // 稍微调小寿命步长（从 0.05f 改为 0.025f），给粒子留出足够的时间飞到外圈更远的地方
                p.life += 0.025f
                p.rotation += p.spinSpeed

                // 边界判定升级：只有超过了扩展后的外圈半径，或者寿命耗尽，才允许销毁
                if (p.radius > particleMaxRadius || p.life > 1.0f) {
                    p.active = false
                    p.radius = spawnRadius
                    p.life = 0f
                    continue
                }

                val cosP = cos(p.angle)
                val sinP = sin(p.angle)
                val x = centerX + p.radius * cosP
                val y = centerY + p.radius * sinP

                // 透明度计算：利用 (1 - p.life) 确保粒子在越过外围圆环奔向 1.4 倍半径的过程中，是完美自然淡出的
                val alpha = (200 * (1 - p.life) * (0.2f + 0.8f * vPercent)).toInt().coerceIn(0, 255)
                particlePaint.color = Color.argb(alpha, r, g, b)

                val particleSize = p.size * (1 - p.life * 0.6f)

                // 绘制主几何粒子
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(p.rotation)
                drawCustomShape(canvas, p.shapeType, particleSize, particlePaint)
                canvas.restore()

                // 绘制拖尾
                if (p.life < 0.8f) {
                    val trailRadius = max(spawnRadius, p.radius - p.speed * 2.5f)
                    val trailX = centerX + trailRadius * cosP
                    val trailY = centerY + trailRadius * sinP
                    particlePaint.alpha = alpha shr 1

                    canvas.save()
                    canvas.translate(trailX, trailY)
                    canvas.rotate(p.rotation - p.spinSpeed * 1.5f)
                    drawCustomShape(canvas, p.shapeType, particleSize * 0.5f, particlePaint)
                    canvas.restore()
                }
            }
        }

        // ========== 3. 中心多边形核心 ==========
        val coreRotateSpeed = 0.2f + vPercent * 0.8f
        coreRotation += coreRotateSpeed
        if (coreRotation > 360f) coreRotation -= 360f

        paint.style = Paint.Style.FILL

        polygonPath.rewind()
        val expansion = 1f + vPercent * 0.1f
        val dynamicRadius = baseRadius * expansion

        for (i in 0 until polygonSides) {
            val angleRad = (coreRotation + i * 360f / polygonSides) * RAD_CONVERT
            val x = centerX + dynamicRadius * cos(angleRad)
            val y = centerY + dynamicRadius * sin(angleRad)
            if (i == 0) polygonPath.moveTo(x, y) else polygonPath.lineTo(x, y)
        }
        polygonPath.close()

        val targetGradientRadius = baseRadius * 1.3f
        if (dirtyGradient || coreGradient == null || lastGradientRadius != targetGradientRadius) {
            coreGradient = RadialGradient(
                centerX,
                centerY,
                targetGradientRadius,
                intArrayOf(
                    _lineColor,
                    Color.argb(220, r, g, b),
                    Color.argb(100, r, g, b),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, 0.4f, 0.7f, 1f),
                Shader.TileMode.CLAMP,
            )
            lastGradientRadius = targetGradientRadius
            dirtyGradient = false
        }
        paint.shader = coreGradient
        canvas.drawPath(polygonPath, paint)

        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = (80 * (0.3f + vPercent * 0.5f)).toInt()

        innerPath.rewind()
        val innerScale = 0.5f + vPercent * 0.1f
        val innerStartAngle = coreRotation + 15f
        val innerDynamicRadius = baseRadius * innerScale

        for (i in 0 until polygonSides) {
            val angleRad = (innerStartAngle + i * 360f / polygonSides) * RAD_CONVERT
            val x = centerX + innerDynamicRadius * cos(angleRad)
            val y = centerY + innerDynamicRadius * sin(angleRad)
            if (i == 0) innerPath.moveTo(x, y) else innerPath.lineTo(x, y)
        }
        innerPath.close()
        canvas.drawPath(innerPath, paint)

        paint.alpha = 200
        canvas.drawCircle(centerX, centerY, baseRadius * 0.12f, paint)
        paint.alpha = 255
    }

    /**
     * 几何粒子形状绘制辅助机（基于局部坐标系 0,0 绘制）
     */
    private fun drawCustomShape(canvas: Canvas, shapeType: Int, size: Float, paint: Paint) {
        if (shapeType == SHAPE_TRIANGLE) {
            // 绘制等边三角形
            singleParticlePath.rewind()
            val r = size * 1.1f
            val p1X = 0f
            val p1Y = -r
            val p2X = r * 0.866f // cos(30°)
            val p2Y = r * 0.5f // sin(30°)
            val p3X = -r * 0.866f // -cos(30°)
            val p3Y = r * 0.5f // sin(30°)

            singleParticlePath.moveTo(p1X, p1Y)
            singleParticlePath.lineTo(p2X, p2Y)
            singleParticlePath.lineTo(p3X, p3Y)
            singleParticlePath.close()
            canvas.drawPath(singleParticlePath, paint)
        } else {
            // 绘制正方形/四边形 (Rect 范围从 -size 到 size)
            canvas.drawRect(-size, -size, size, size, paint)
        }
    }

    /**
     * 极致省电静态场景
     */
    private fun drawStaticScene(canvas: Canvas, timeFactor: Float) {
        val r = Color.red(_lineColor)
        val g = Color.green(_lineColor)
        val b = Color.blue(_lineColor)

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb(100, r, g, b)

        polygonPath.rewind()
        for (i in 0 until polygonSides) {
            val angleRad = (coreRotation + i * 360f / polygonSides) * RAD_CONVERT
            val x = centerX + baseRadius * cos(angleRad)
            val y = centerY + baseRadius * sin(angleRad)
            if (i == 0) polygonPath.moveTo(x, y) else polygonPath.lineTo(x, y)
        }
        polygonPath.close()
        canvas.drawPath(polygonPath, paint)

        paint.color = Color.WHITE
        paint.alpha = 50
        canvas.drawCircle(centerX, centerY, baseRadius * 0.12f, paint)
        paint.alpha = 255
    }

    private fun softerChangeVolume() {
        val localPerVolume = perVolume
        val localTargetVolume = targetVolume.toFloat()

        val realTarget = if (localTargetVolume <= 1f) MIN_ACTIVE_VOLUME else localTargetVolume

        when {
            volume < realTarget - localPerVolume -> volume += localPerVolume
            volume > realTarget + localPerVolume -> {
                volume = max(MIN_ACTIVE_VOLUME, volume - localPerVolume)
            }
            else -> volume = realTarget
        }
    }

    fun setWaveformColor(color: Int) {
        this.lineColor = color
    }

    fun setVolume(volume: Int) {
        val inputVolume = volume.coerceIn(0, 100)
        if (abs((targetVolume - inputVolume).toFloat()) > perVolume || inputVolume > 0) {
            targetVolume = inputVolume + 20 // 作为开始正式接受声音的标志
            checkVolumeValue()

            if (inputVolume > 0 && isEngineSleeping) {
                synchronized(renderLock) {
                    isEngineSleeping = false
                    renderLock.notifyAll()
                }
            }
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

    override fun release() {
        stopAnim()
        particles.clear()
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

    fun clearDraw() {
        var canvas: Canvas? = null
        try {
            canvas = holder?.lockCanvas()
            if (canvas != null) {
                if (isTransparentMode) {
                    canvas.drawColor(_backGroundColor, PorterDuff.Mode.CLEAR)
                } else {
                    canvas.drawColor(_backGroundColor)
                }
            }
        } catch (ignored: Exception) {
        } finally {
            canvas?.let { holder?.unlockCanvasAndPost(it) }
        }
    }

    fun setMoveSpeed(moveSpeed: Float) {
        offsetSpeed = moveSpeed
    }

    fun setSensibility(sensibility: Int) {
        this.sensibility = sensibility
        checkSensibilityValue()
    }

    fun setPolygonSides(sides: Int) {
        polygonSides = sides.coerceIn(6, 12)
    }

    private fun checkVolumeValue() {
        if (targetVolume > 100) targetVolume = 100
        if (targetVolume < 0) targetVolume = 0
    }

    private fun checkSensibilityValue() {
        if (sensibility > 10) sensibility = 10
        if (sensibility < 1) sensibility = 1
    }

    // 扩展数据结构，支持形状类型与自旋转
    private data class Particle(
        var angle: Float = 0f,
        var radius: Float = 0f,
        var speed: Float = 0f,
        var size: Float = 0f,
        var life: Float = 0f,
        var active: Boolean = false,
        var shapeType: Int = SHAPE_TRIANGLE, // 形状类别
        var rotation: Float = 0f, // 自身旋转角度
        var spinSpeed: Float = 0f, // 自旋速度
    )
}
