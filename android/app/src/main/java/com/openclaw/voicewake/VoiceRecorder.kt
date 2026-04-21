package com.openclaw.voicewake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * 语音识别封装类
 *
 * 基于 Android SpeechRecognizer API，支持中文识别、离线优先、实时 partial 结果。
 * 延迟目标：< 500ms 启动。
 *
 * 使用前需确保已在 AndroidManifest 中声明：
 *   <uses-permission android:name="android.permission.RECORD_AUDIO" />
 */
class VoiceRecorder(
    private val context: Context,
    private var callback: Callback? = null
) {
    /** 识别回调接口 */
    interface Callback {
        /** 最终识别结果（识别结束） */
        fun onResult(text: String)
        /** 识别出错 */
        fun onError(error: String)
        /** 实时识别结果（识别过程中的中间结果） */
        fun onPartialResult(text: String)
    }

    /** 底层 SpeechRecognizer 实例 */
    private var speechRecognizer: SpeechRecognizer? = null

    /** 当前是否正在监听中 */
    @Volatile
    private var isListening = false

    /** 主线程 Handler */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 开始监听语音
     *
     * 创建 SpeechRecognizer 实例，配置中文识别 intent，设置离线优先参数，
     * 然后调用 startListening 触发系统录音并识别。
     */
    fun startListening() {
        // ✅ 修复1: 运行时权限检查
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            callback?.onError("缺少录音权限，请先授予 RECORD_AUDIO 权限")
            return
        }

        if (isListening) {
            callback?.onError("正在监听中，请勿重复调用")
            return
        }

        // ✅ 修复2: 确保在主线程创建 SpeechRecognizer
        if (Looper.getMainLooper() != Looper.myLooper()) {
            Log.w("VoiceRecorder", "startListening called from background thread, posting to main thread")
            mainHandler.post { startListening() }
            return
        }

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            // 构建中文识别 Intent
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000)
            }

            recognizer.setRecognitionListener(createRecognitionListener())
            recognizer.startListening(intent)
        } catch (e: Exception) {
            // ✅ 修复3: 创建失败时清理状态并记录日志
            speechRecognizer?.destroy()
            speechRecognizer = null
            isListening = false
            Log.e("VoiceRecorder", "startListening failed", e)
            callback?.onError("语音识别启动失败: ${e.localizedMessage}")
        }
    }

    private fun createRecognitionListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
        }

        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            isListening = false
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "缺少录音权限"
                SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                SpeechRecognizer.ERROR_NO_MATCH -> "未识别到有效语音"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别引擎繁忙"
                SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到语音输入"
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "语言不支持"
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "语言包不可用"
                else -> "未知错误 (code=$error)"
            }
            callback?.onError(errorMsg)
            cleanup()
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                callback?.onResult(matches.first())
            } else {
                callback?.onError("未获取到识别结果")
            }
            cleanup()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                callback?.onPartialResult(matches.first())
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** 统一清理逻辑 */
    private fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun stopListening() {
        if (!isListening && speechRecognizer == null) {
            return
        }

        try {
            // ✅ 修复4: 根据状态选择 stop 或 cancel，避免冲突
            if (isListening) {
                speechRecognizer?.stopListening()
            } else {
                speechRecognizer?.cancel()
            }
        } catch (e: Exception) {
            // ✅ 修复5: 记录异常而非吞掉
            Log.w("VoiceRecorder", "stopListening failed", e)
        }

        cleanup()
        isListening = false
    }

    fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun release() {
        stopListening()
        callback = null
    }
}
