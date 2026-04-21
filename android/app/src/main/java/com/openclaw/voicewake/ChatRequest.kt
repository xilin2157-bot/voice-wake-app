package com.openclaw.voicewake

/**
 * 发送给 OpenClaw API 的请求体
 */
data class ChatRequest(
    val text: String,
    val agent: String = "小智"
)
