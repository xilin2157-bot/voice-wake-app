package com.openclaw.voicewake

import com.google.gson.annotations.SerializedName

/**
 * OpenClaw API 返回的响应体
 */
data class ChatResponse(
    val reply: String,
    val agent: String,
    @SerializedName("agent_id")
    val agentId: String
)
