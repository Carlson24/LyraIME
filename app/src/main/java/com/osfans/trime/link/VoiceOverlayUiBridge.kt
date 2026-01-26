package com.osfans.trime.link

/**
 * 语音覆盖层 UI 桥接：
 * - 键盘侧注册回调以更新/关闭覆盖层；
 * - AIDL 客户端在回调线程里调用这些回调以驱动波形动画。
 */
object VoiceOverlayUiBridge {
    @Volatile var onAmplitude: ((Float) -> Unit)? = null
    @Volatile var onDone: (() -> Unit)? = null

    fun clear() {
        onAmplitude = null
        onDone = null
    }
}

