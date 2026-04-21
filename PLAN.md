# 语音对话阿智 - Android 原生 App 开发计划

## 目标
开发 Android 原生 App，实现：
- 按住说话 → 语音识别 → 调 OpenClaw API → 语音回复
- 体验对标微信语音，延迟 < 1秒，识别准确

## 技术栈
- **语言**: Kotlin
- **最低 SDK**: API 24 (Android 7.0)
- **目标 SDK**: API 34 (Android 14)
- **语音识别**: Android SpeechRecognizer (系统级，离线+在线)
- **TTS**: Android TextToSpeech
- **网络**: Retrofit + OkHttp
- **UI**: Jetpack Compose

## 架构
```
MainActivity (UI)
    ├── VoiceRecorder (语音识别)
    ├── OpenClawClient (网络层)
    └── TTSPlayer (语音播报)
```

## API 接口
```kotlin
interface OpenClawApi {
    @POST("/api/chat")
    suspend fun chat(@Body req: ChatRequest): ChatResponse
}

data class ChatRequest(val text: String, val agent: String = "小智")
data class ChatResponse(val reply: String, val agent: String)
```

## 开发分工

### Agent 1: Codex (OpenAI)
- 负责: 项目初始化 + 基础架构 + UI 布局
- 输出: 可编译的空壳项目

### Agent 2: Claude Code (Anthropic)
- 负责: 语音识别模块 (SpeechRecognizer) + TTS 模块
- 输出: VoiceRecorder.kt + TTSPlayer.kt

### Agent 3: OpenCode
- 负责: 网络层 (Retrofit) + 业务逻辑 + 集成测试
- 输出: OpenClawClient.kt + MainActivity 完整逻辑

## 验收标准
- [ ] APK 能安装到 Android 手机
- [ ] 按住按钮说话，松开后 1 秒内开始识别
- [ ] 识别结果准确，不丢字
- [ ] 调 OpenClaw API 成功，返回回复
- [ ] TTS 播报清晰
- [ ] 全程延迟 < 3 秒

## 交付物
- `/voice-wake-app/android/` 完整项目
- `app-release.apk` 安装包
- README 使用说明
