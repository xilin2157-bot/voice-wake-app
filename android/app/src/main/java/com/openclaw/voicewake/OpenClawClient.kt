package com.openclaw.voicewake

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "OpenClawClient"
private const val WS_TIMEOUT_MS = 15_000L

/**
 * OpenClaw WebSocket 客户端
 *
 * 职责：
 * - 通过 WebSocket 连接 OpenClaw Gateway
 * - 服务器地址从 BuildConfig.SERVER_URL 读取（自动 http:// → ws://）
 * - sendMessage 为同步等待回复模式，超时 15 秒
 * - 每次调用建立独立 WebSocket 连接，用完即关
 */
class OpenClawClient(
    baseUrl: String? = null
) : AutoCloseable {

    private val serverUrl: String = baseUrl ?: BuildConfig.SERVER_URL
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    /**
     * 构建 WebSocket URL
     * 将 BuildConfig.SERVER_URL 的 http:// 替换为 ws://，追加 /ws 路径
     */
    private fun buildWsUrl(): String {
        val base = serverUrl.trim().removeSuffix("/")
        val wsBase = if (base.startsWith("https://")) {
            "ws://" + base.removePrefix("https://")
        } else {
            base.removePrefix("http://").let { "ws://$it" }
        }
        return "$wsBase/ws"
    }

    /**
     * 发送文本消息并同步等待回复
     *
     * @param text 用户输入的文本
     * @return Result<String> — 成功时返回回复内容，失败时返回异常
     */
    suspend fun sendMessage(text: String): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            val wsUrl = buildWsUrl()
            Log.d(TAG, "Connecting WebSocket: $wsUrl")

            val latch = CountDownLatch(1)
            var reply: String? = null
            var error: Throwable? = null

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected")

                    // 发送 A2A JSON-RPC 消息
                    val messageId = System.currentTimeMillis().toString()
                    val requestObj = JsonObject().apply {
                        addProperty("jsonrpc", "2.0")
                        addProperty("id", messageId)
                        addProperty("method", "message/send")
                        add("params", JsonObject().apply {
                            add("message", JsonObject().apply {
                                addProperty("kind", "message")
                                addProperty("messageId", messageId)
                                addProperty("role", "user")
                                add("parts", com.google.gson.JsonArray().apply {
                                    add(JsonObject().apply {
                                        addProperty("kind", "text")
                                        addProperty("text", text)
                                    })
                                })
                            })
                        })
                    }
                    val json = gson.toJson(requestObj)
                    Log.d(TAG, "Sending: $json")
                    webSocket.send(json)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Received: $text")
                    try {
                        val json = gson.fromJson(text, JsonObject::class.java)
                        // 优先从 reply 字段读取，其次 result，兼容不同协议
                        reply = when {
                            json.has("reply") -> json.get("reply").asString
                            json.has("result") -> {
                                val result = json.get("result")
                                if (result.isJsonObject && result.asJsonObject.has("reply")) {
                                    result.asJsonObject.get("reply").asString
                                } else {
                                    result.asString
                                }
                            }
                            json.has("error") -> {
                                val err = json.getAsJsonObject("error")
                                if (err != null) err.get("message").asString
                                else "Server error"
                            }
                            else -> text
                        }
                        Log.d(TAG, "Parsed reply: $reply")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse response", e)
                        error = e
                    } finally {
                        latch.countDown()
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    Log.w(TAG, "Received binary message (ignored)")
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closing: $code $reason")
                    webSocket.close(1000, null)
                    latch.countDown()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $code $reason")
                    latch.countDown()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure: ${t.message}")
                    error = t
                    latch.countDown()
                }
            }

            val request = Request.Builder().url(wsUrl).build()
            val webSocket = client.newWebSocket(request, listener)

            // 等待回复或超时
            val completed = latch.await(WS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (!completed) {
                Log.w(TAG, "sendMessage timed out after ${WS_TIMEOUT_MS}ms")
                error = java.net.SocketTimeoutException("WebSocket reply timeout (${WS_TIMEOUT_MS}ms)")
            }

            webSocket.close(1000, "done")

            if (error != null) {
                continuation.resume(Result.failure(error!!))
            } else if (reply != null) {
                continuation.resume(Result.success(reply!!))
            } else {
                continuation.resume(Result.failure(RuntimeException("No reply received")))
            }
        }
    }

    /**
     * 释放资源
     * 关闭连接池和调度器。
     */
    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
