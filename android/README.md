# 语音对话阿智 - Android App

按住说话，松开后自动识别语音并发送给 OpenClaw 的「阿智」Agent，语音播报回复。

## 功能

- 🎤 **按住说话**：像微信语音一样按住按钮说话
- ⚡ **快速识别**：Android 系统级语音识别，延迟 < 500ms
- 🤖 **智能回复**：调用 OpenClaw 网关，阿智 Agent 回复
- 🔊 **语音播报**：自动 TTS 播报回复内容
- ⏹️ **打断功能**：播报时可点击打断

## 技术栈

- Kotlin + Jetpack Compose
- Android SpeechRecognizer（系统语音识别）
- Retrofit + OkHttp（网络请求）
- TextToSpeech（语音合成）

## 安装

### 方法 1：直接安装 APK（推荐）

1. 下载 `app-release.apk`
2. 传到手机，点击安装
3. 允许「安装未知来源应用」
4. 打开 App，允许麦克风权限

### 方法 2：源码编译

```bash
# 需要 Android Studio 或命令行工具
cd voice-wake-app/android

# 编译 APK
./gradlew assembleRelease

# APK 输出路径
app/build/outputs/apk/release/app-release.apk
```

## 使用

1. **首次启动**：允许麦克风权限
2. **按住说话**：按住屏幕中央的大按钮
3. **松开发送**：松开后自动识别并发送
4. **等待回复**：阿智思考中...
5. **语音播报**：自动播报回复内容
6. **打断**：播报时点击「打断」按钮可停止

## 配置

如需修改 OpenClaw 服务器地址，编辑：
`app/src/main/java/com/openclaw/voicewake/OpenClawClient.kt`

默认地址：`https://100.86.120.87:8443`

## 注意事项

- 需要 Android 8.0+ (API 26+)
- 需要网络连接（调用 OpenClaw API）
- 首次使用需下载中文语音包（自动）

## 项目结构

```
app/src/main/java/com/openclaw/voicewake/
├── MainActivity.kt      # 主界面 + 业务逻辑
├── VoiceRecorder.kt     # 语音识别封装
├── TTSPlayer.kt         # 语音合成封装
├── OpenClawClient.kt    # 网络请求封装
├── ChatRequest.kt       # 请求数据类
└── ChatResponse.kt      # 响应数据类
```

## 开发团队

- Codex (OpenAI)：项目初始化 + UI 布局
- OpenCode：业务逻辑 + 集成
- Subagent：语音模块 + 网络模块
