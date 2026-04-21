package com.openclaw.voicewake

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.RequiresApi
import java.util.Locale
import java.util.UUID

/**
 * 语音合成（TTS）封装类
 *
 * 基于 Android TextToSpeech API，支持中文播报、停止播报、播报完成回调。
 *
 * 使用前需确保 AndroidManifest 中无需额外权限（TTS 为系统 API，无需权限）。
 */
class TTSPlayer(
    private val context: Context,
    private var callback: Callback? = null
) {
    /** TTS 回调接口 */
    interface Callback {
        /** 播报完成 */
        fun onComplete()
        /** 播报出错 */
        fun onError(error: String)
    }

    /** 底层 TextToSpeech 实例 */
    private var tts: TextToSpeech? = null

    /** 初始化是否成功 */
    @Volatile
    private var isInitialized = false

    /** 当前是否正在播报 */
    @Volatile
    private var isSpeaking = false

    /** 主线程 Handler */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 初始化 TTS 引擎
     *
     * 创建 TextToSpeech 实例，设置中文语音，注册播报进度监听。
     * 建议在 Activity/Service 的 onCreate 中调用，异步等待就绪。
     */
    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // 设置中文语音
                val result = tts?.setLanguage(Locale.CHINA)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 中文语言包不可用，尝试使用 CHINESE（通用中文）
                    val fallbackResult = tts?.setLanguage(Locale.CHINESE)
                    if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        isInitialized = false
                        postCallback { callback?.onError("中文语音包不可用，请在系统设置中下载中文语音数据") }
                        return@TextToSpeech
                    }
                }

                // 设置语速和音调
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)

                // ✅ 修复1: 设置播报进度监听，回调切到主线程
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                        // ✅ 修复2: 回调切到主线程
                        postCallback { callback?.onComplete() }
                    }

                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        postCallback { callback?.onError("TTS 播报失败") }
                    }

                    // ✅ 修复3: 加 API 级别注解
                    @RequiresApi(Build.VERSION_CODES.M)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        isSpeaking = false
                        // 主动停止不视为错误
                    }

                    @Deprecated("使用 onDone 中的 error 替代")
                    override fun onError(utteranceId: String) {
                        isSpeaking = false
                        postCallback { callback?.onError("TTS 播报失败") }
                    }
                })

                isInitialized = true
            } else {
                isInitialized = false
                postCallback { callback?.onError("TTS 引擎初始化失败") }
            }
        }
    }

    /** ✅ 修复4: 统一的主线程回调分发 */
    private fun postCallback(block: () -> Unit) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * 播报文本
     *
     * @param text 要播报的文本
     */
    fun speak(text: String) {
        if (text.isBlank()) {
            Log.w("TTSPlayer", "speak called with blank text")
            return
        }

        if (!isInitialized) {
            Log.w("TTSPlayer", "TTS not initialized yet")
            postCallback { callback?.onError("TTS 引擎未初始化") }
            return
        }

        // 使用唯一 ID 标识本次播报
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle()

        @Suppress("DEPRECATION")
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        } else {
            val map = HashMap<String, String>()
            map[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId
            tts?.speak(text, TextToSpeech.QUEUE_ADD, map)
        }

        if (result == TextToSpeech.ERROR) {
            Log.e("TTSPlayer", "speak failed")
            postCallback { callback?.onError("TTS 播报失败") }
        }
    }

    /**
     * 停止播报
         */
    fun stop() {
        if (!isInitialized) return

        try {
            tts?.stop()
            isSpeaking = false
        } catch (e: Exception) {
            // ✅ 修复5: 记录异常
            Log.w("TTSPlayer", "stop failed", e)
        }
    }

    /**
     * 设置语速
     *
     * @param rate 语速倍数（0.5 = 慢速，1.0 = 正常，2.0 = 快速）
     * @return 是否设置成功
     */
    fun setSpeechRate(rate: Float): Boolean {
        if (!isInitialized) return false
        return tts?.setSpeechRate(rate) == TextToSpeech.SUCCESS
    }

    /**
     * 设置音调
     *
     * @param pitch 音调倍数（0.5 = 低音，1.0 = 正常，2.0 = 高音）
     * @return 是否设置成功
     */
    fun setPitch(pitch: Float): Boolean {
        if (!isInitialized) return false
        return tts?.setPitch(pitch) == TextToSpeech.SUCCESS
    }

    /**
     * 当前是否正在播报
     */
    fun isCurrentlySpeaking(): Boolean = isSpeaking

    /**
     * 释放资源
     *
     * 调用此方法后实例不可再用，需要重新创建。
     */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        callback = null
        isInitialized = false
    }
}
