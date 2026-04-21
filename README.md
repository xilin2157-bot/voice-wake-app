# 🎙️ 语音对话阿智

Android 原生 App，按住说话，语音对话 OpenClaw「阿智」Agent。

## 功能特性

- 🎤 **按住说话**：像微信语音一样按住按钮说话
- ⚡ **快速识别**：Android 系统级语音识别，延迟 < 500ms
- 🤖 **智能回复**：调用 OpenClaw 网关，阿智 Agent 回复
- 🔊 **语音播报**：自动 TTS 播报回复内容
- ⏹️ **打断功能**：播报时可点击打断

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose
- **语音识别**: Android SpeechRecognizer（系统级，支持离线）
- **语音合成**: Android TextToSpeech
- **网络**: Retrofit + OkHttp
- **最低 SDK**: API 26 (Android 8.0)
- **目标 SDK**: API 34 (Android 14)

## 快速开始

### 方法 1：下载 APK（推荐）

1. 进入 [GitHub Releases](../../releases)
2. 下载 `app-debug.apk` 或 `app-release-unsigned.apk`
3. 传到手机安装
4. 允许「安装未知来源应用」
5. 打开 App，允许麦克风权限

### 方法 2：源码编译

```bash
# 克隆仓库
git clone <repo-url>
cd voice-wake-app/android

# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK
./gradlew assembleRelease

# APK 输出路径
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

## 使用说明

1. **首次启动**：允许麦克风权限
2. **按住说话**：按住屏幕中央的大按钮
3. **松开发送**：松开按钮自动识别并发送
4. **等待回复**：阿智思考中...
5. **语音播报**：自动播报回复内容
6. **打断**：播报时点击「打断」按钮可停止

## 配置

如需修改 OpenClaw 服务器地址，编辑：
`app/src/main/java/com/openclaw/voicewake/OpenClawClient.kt`

默认地址：`https://100.86.120.87:8443`

## 项目结构

```
app/src/main/java/com/openclaw/voicewake/
├── MainActivity.kt          # 主界面 + 业务逻辑
├── VoiceRecorder.kt         # 语音识别封装
├── TTSPlayer.kt             # 语音合成封装
├── OpenClawClient.kt        # 网络请求封装
├── ChatRequest.kt           # 请求数据类
└── ChatResponse.kt          # 响应数据类
```

## 安全说明

- 自签 HTTPS 证书仅在 `DEBUG` 模式下信任
- Release 版本使用正常 SSL 验证
- 内网测试环境专用，生产环境请使用正式证书

## 开发团队

- **Codex** (OpenAI)：项目初始化 + UI 布局
- **OpenCode**：业务逻辑 + 集成
- **Subagent**：语音模块 + 网络模块
- **Code Review**：权限检查、线程安全、生命周期修复

## CI/CD

GitHub Actions 自动构建：
- 每次 push 到 main 分支自动编译
- 生成 Debug 和 Release APK
- 自动发布到 GitHub Releases

## 许可证

MIT License
