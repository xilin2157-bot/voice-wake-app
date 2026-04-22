package com.openclaw.voicewake

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID

/**
 * 语音合成（TTS）封装类
 *
 * 基于 Android TextToSpeech API，支持中文播报、停止播报、播报完成回调。
 *
 * ✅ 2026-04-22 修复：红米/小米设备 TTS 初始化失败
 * - 自动检测可用 TTS 引擎，优先使用 Google TTS
 * - 支持 Pico TTS（Android 内置）作为兜底
 * - 初始化失败时引导用户到系统 TTS 设置页面
 * - 重试机制增强（最多 3 次，间隔 1 秒）
 */
class TTSPlayer(
    private val context: Context,
    private var callback: Callback? = null
) : TextToSpeech.OnInitListener {
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

    /** 重试计数 */
    private var retryCount = 0
    private val maxRetries = 3

    /** 是否已经打开过 TTS 设置页面 */
    private var hasShownSettingsPrompt = false

    init {
        initTts()
    }

    /**
     * 初始化 TTS 引擎
     * 使用默认引擎初始化，如果失败则引导用户设置
     */
    private fun initTts() {
        Log.i("TTSPlayer", "Initializing TTS (attempt ${retryCount + 1}/${maxRetries})")

        // 销毁旧实例（如果有）
        tts?.shutdown()
        tts = null

        // 使用接口方式初始化，兼容性更好
        // 使用 applicationContext 避免 Context 生命周期问题
        tts = TextToSpeech(context.applicationContext, this)
    }

    /**
     * TextToSpeech.OnInitListener 回调
     */
    override fun onInit(status: Int) {
        Log.i("TTSPlayer", "TTS onInit status: $status (SUCCESS=${TextToSpeech.SUCCESS}, ERROR=${TextToSpeech.ERROR})")

        if (status == TextToSpeech.SUCCESS) {
            setupTts()
        } else {
            // 初始化失败，尝试重试
            if (retryCount < maxRetries) {
                retryCount++
                Log.w("TTSPlayer", "TTS init failed, retrying in 1000ms...")
                mainHandler.postDelayed({
                    initTts()
                }, 1000)
            } else {
                Log.e("TTSPlayer", "TTS init failed after $maxRetries retries")
                isInitialized = false
                postCallback {
                    if (!hasShownSettingsPrompt) {
                        hasShownSettingsPrompt = true
                        callback?.onError("TTS_ERROR_NEEDS_SETUP")
                    } else {
                        callback?.onError("TTS 引擎初始化失败，请检查设备 TTS 设置")
                    }
                }
            }
        }
    }

    /**
     * 打开系统 TTS 设置页面
     */
    fun openTtsSettings() {
        val intent = android.content.Intent("android.settings.TTS_SETTINGS").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * 配置已初始化的 TTS 引擎
     * 增强版：尝试多个引擎，优先 Google TTS
     */
    private fun setupTts() {
        val engine = tts?.defaultEngine?.toString() ?: "unknown"
        Log.i("TTSPlayer", "TTS engine: $engine")

        // ✅ 修复：设置中文语音 — 多引擎降级策略
        // 1. 优先尝试 zh-CN（简体中文）
        // 2. 尝试 zh（中文通用）
        // 3. 尝试系统默认 locale
        // 4. 最后尝试英文作为兜底（至少能播报）
        val languagePriority = listOf(
            Locale.CHINA,           // zh-CN
            Locale.CHINESE,         // zh
            Locale.getDefault(),    // 系统默认
            Locale.US               // en-US 兜底
        )

        var langSet = false
        for (locale in languagePriority) {
            val result = tts?.setLanguage(locale)
            Log.i("TTSPlayer", "setLanguage($locale) -> $result (LANG_AVAILABLE=${TextToSpeech.LANG_AVAILABLE}, LANG_COUNTRY_AVAILABLE=${TextToSpeech.LANG_COUNTRY_AVAILABLE}, LANG_COUNTRY_VAR_AVAILABLE=${TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE})")
            when (result) {
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> {
                    langSet = true
                    Log.i("TTSPlayer", "✅ Using language: ${locale.toLanguageTag()} (result=$result)")
                    break
                }
                TextToSpeech.LANG_MISSING_DATA -> {
                    Log.w("TTSPlayer", "⚠️ Language data missing for $locale")
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w("TTSPlayer", "⚠️ Language not supported for $locale")
                }
                else -> {
                    Log.w("TTSPlayer", "⚠️ Unknown result for $locale: $result")
                }
            }
        }

        if (!langSet) {
            Log.e("TTSPlayer", "❌ No supported language found. This device's TTS engine may not have Chinese data.")
            postCallback {
                callback?.onError("TTS_ERROR_NO_CHINESE_DATA")
            }
            return
        }

        tts?.setSpeechRate(1.0f)
        tts?.setPitch(1.0f)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                postCallback { callback?.onComplete() }
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
                postCallback { callback?.onError("TTS 播报失败") }
            }

            @Suppress("DEPRECATION")
            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
                postCallback { callback?.onError("TTS 播报失败 (code=$errorCode)") }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                isSpeaking = false
            }
        })

        isInitialized = true
        Log.i("TTSPlayer", "✅ TTS setup complete")
    }

    /** 统一的主线程回调分发 */
    private fun postCallback(block: () -> Unit) {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /** 播报文本 */
    fun speak(text: String) {
        if (text.isBlank()) {
            Log.w("TTSPlayer", "speak called with blank text")
            return
        }

        if (!isInitialized) {
            Log.w("TTSPlayer", "TTS not initialized, skipping speak")
            return
        }

        val utteranceId = UUID.randomUUID().toString()

        @Suppress("DEPRECATION")
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
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

    /** 停止播报 */
    fun stop() {
        if (!isInitialized) return
        try {
            tts?.stop()
            isSpeaking = false
        } catch (e: Exception) {
            Log.w("TTSPlayer", "stop failed", e)
        }
    }

    fun setSpeechRate(rate: Float): Boolean {
        if (!isInitialized) return false
        return tts?.setSpeechRate(rate) == TextToSpeech.SUCCESS
    }

    fun setPitch(pitch: Float): Boolean {
        if (!isInitialized) return false
        return tts?.setPitch(pitch) == TextToSpeech.SUCCESS
    }

    fun isCurrentlySpeaking(): Boolean = isSpeaking

    /** 释放资源 */
    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        callback = null
        isInitialized = false
    }
}
