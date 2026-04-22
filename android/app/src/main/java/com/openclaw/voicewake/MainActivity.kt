package com.openclaw.voicewake

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val openClawClient by lazy { OpenClawClient() }
    private var voiceRecorder: VoiceRecorder? = null
    private var ttsPlayer: TTSPlayer? = null
    private val conversationHistory = mutableStateListOf<ChatMessage>()
    private var isSpeaking by mutableStateOf(false)
    private var isProcessing by mutableStateOf(false)

    // TTS 设置引导对话框
    private var showTtsSetupDialog by mutableStateOf(false)
    private var ttsSetupMessage by mutableStateOf("")

    data class ChatMessage(
        val isUser: Boolean,
        val text: String
    )

    // ✅ 修复：协程异常处理
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        lifecycleScope.launch {
            conversationHistory.add(ChatMessage(isUser = false, text = "请求异常: ${throwable.message}"))
            Toast.makeText(this@MainActivity, "请求失败: ${throwable.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "需要麦克风权限才能语音对话", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        voiceRecorder = VoiceRecorder(this, object : VoiceRecorder.Callback {
            override fun onResult(text: String) {
                // ✅ 修复：使用 repeatOnLifecycle 确保 Activity 活跃时才执行
                lifecycleScope.launch(coroutineExceptionHandler) {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        onVoiceRecognized(text)
                    }
                }
            }
            override fun onError(error: String) {
                Toast.makeText(this@MainActivity, "识别错误: $error", Toast.LENGTH_SHORT).show()
            }
            override fun onPartialResult(text: String) {
                // 可选：实时显示识别中的文字
            }
        })

        ttsPlayer = TTSPlayer(this, object : TTSPlayer.Callback {
            override fun onComplete() {
                isSpeaking = false
            }
            override fun onError(error: String) {
                isSpeaking = false
                // ✅ 修复：处理 TTS 引擎设置引导
                when (error) {
                    "TTS_ERROR_NEEDS_SETUP" -> {
                        ttsSetupMessage = "TTS 引擎初始化失败，需要设置默认语音引擎。\n\n请点击「去设置」前往 TTS 设置页面，选择一个支持中文的 TTS 引擎（推荐 Google 文字转语音）。"
                        showTtsSetupDialog = true
                    }
                    "TTS_ERROR_NO_CHINESE_DATA" -> {
                        ttsSetupMessage = "当前 TTS 引擎没有中文语音数据。\n\n请点击「去设置」前往 TTS 设置页面，选择 Google 文字转语音或安装中文语言包。"
                        showTtsSetupDialog = true
                    }
                    else -> {
                        Toast.makeText(this@MainActivity, "播报错误: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceChatScreen(
                        conversationHistory = conversationHistory,
                        isSpeaking = isSpeaking,
                        isProcessing = isProcessing,
                        showTtsSetupDialog = showTtsSetupDialog,
                        ttsSetupMessage = ttsSetupMessage,
                        onRecordStart = { voiceRecorder?.startListening() },
                        onRecordStop = { voiceRecorder?.stopListening() },
                        onInterrupt = {
                            ttsPlayer?.stop()
                            isSpeaking = false
                        },
                        onDismissTtsDialog = { showTtsSetupDialog = false },
                        onOpenTtsSettings = {
                            ttsPlayer?.openTtsSettings()
                            showTtsSetupDialog = false
                        }
                    )
                }
            }
        }
    }

    private suspend fun onVoiceRecognized(text: String) {
        if (text.isBlank()) return

        conversationHistory.add(ChatMessage(isUser = true, text = text))
        isProcessing = true

        try {
            val result = openClawClient.sendMessage(text)
            result.onSuccess { reply ->
                conversationHistory.add(ChatMessage(isUser = false, text = reply))
                isSpeaking = true
                ttsPlayer?.speak(reply)
            }.onFailure { error ->
                conversationHistory.add(ChatMessage(isUser = false, text = "网络错误: ${error.message}"))
                Toast.makeText(this@MainActivity, "请求失败: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            isProcessing = false
        }
    }

    // ✅ 修复：onStop 释放，避免旋转屏幕时资源残留
    override fun onStop() {
        super.onStop()
        voiceRecorder?.stopListening()
        ttsPlayer?.stop()
        isSpeaking = false
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceRecorder?.release()
        ttsPlayer?.release()
        openClawClient.close()
    }
}

@Composable
fun VoiceChatScreen(
    conversationHistory: List<MainActivity.ChatMessage>,
    isSpeaking: Boolean,
    isProcessing: Boolean,
    showTtsSetupDialog: Boolean,
    ttsSetupMessage: String,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onInterrupt: () -> Unit,
    onDismissTtsDialog: () -> Unit,
    onOpenTtsSettings: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "语音对话阿智",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val listState = rememberLazyListState()

            // Auto-scroll to bottom when new messages arrive
            LaunchedEffect(conversationHistory.size) {
                if (conversationHistory.isNotEmpty()) {
                    listState.animateScrollToItem(conversationHistory.size - 1)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState
            ) {
                items(conversationHistory) { message ->
                    ChatBubble(message = message)
                }
                // ✅ 修复：处理中的 loading 提示
                if (isProcessing) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSpeaking) {
                    Button(
                        onClick = onInterrupt,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        enabled = !isProcessing
                    ) {
                        Text("打断")
                    }
                } else {
                    // 使用简单的 Button，通过 pointerInteropFilter 处理按下/松开
                    var isPressed by remember { mutableStateOf(false) }

                    Button(
                        onClick = { },
                        modifier = Modifier
                            .size(80.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        when (event.type) {
                                            androidx.compose.ui.input.pointer.PointerEventType.Press -> {
                                                if (!isProcessing) {
                                                    isPressed = true
                                                    onRecordStart()
                                                }
                                            }
                                            androidx.compose.ui.input.pointer.PointerEventType.Release,
                                            androidx.compose.ui.input.pointer.PointerEventType.Exit -> {
                                                if (isPressed) {
                                                    isPressed = false
                                                    onRecordStop()
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPressed) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (isPressed) "录音中..." else "按住说话")
                    }
                }
            }
        }

        // ✅ TTS 设置引导对话框
        if (showTtsSetupDialog) {
            AlertDialog(
                onDismissRequest = onDismissTtsDialog,
                title = { Text("TTS 引擎设置") },
                text = { Text(ttsSetupMessage) },
                confirmButton = {
                    Button(onClick = onOpenTtsSettings) {
                        Text("去设置")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissTtsDialog) {
                        Text("稍后")
                    }
                }
            )
        }
    }
}

@Composable
fun ChatBubble(message: MainActivity.ChatMessage) {
    val backgroundColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val alignment = if (message.isUser) {
        androidx.compose.ui.Alignment.CenterEnd
    } else {
        androidx.compose.ui.Alignment.CenterStart
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            color = backgroundColor,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
