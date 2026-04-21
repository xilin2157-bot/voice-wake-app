package com.openclaw.voicewake

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * OpenClaw API 网络接口定义
 */
interface OpenClawApi {
    @retrofit2.http.POST("/api/chat")
    suspend fun chat(@retrofit2.http.Body request: ChatRequest): ChatResponse
}

/**
 * OpenClaw 网络客户端
 *
 * 职责：
 * - 管理 Retrofit / OkHttp 实例
 * - 处理自签 HTTPS 证书（仅 DEBUG 内网测试用）
 * - 提供简洁的 suspend 方法供 UI / ViewModel 调用
 */
class OpenClawClient(
    baseUrl: String = "https://100.86.120.87:8443"
) : AutoCloseable {

    private val client: OkHttpClient
    private val api: OpenClawApi

    init {
        client = buildOkHttpClient()

        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenClawApi::class.java)
    }

    /**
     * 发送文本消息并获取回复
     *
     * @param text 用户输入的文本
     * @return Result<String> — 成功时返回回复内容，失败时返回异常
     */
    suspend fun sendMessage(text: String): Result<String> {
        return runCatching {
            val response = api.chat(ChatRequest(text = text))
            response.reply
        }
    }

    /**
     * 释放资源
     *
     * 关闭 OkHttp 的线程池和连接池。
     */
    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    /**
     * 构建 OkHttpClient
     *
     * DEBUG 模式下信任自签证书（仅内网测试），RELEASE 模式下使用正常 SSL。
     */
    private fun buildOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        // ✅ 修复：仅在 DEBUG 模式下信任自签证书
        if (BuildConfig.DEBUG) {
            // ⚠️ 安全警告：此配置会信任任意 HTTPS 证书，仅适用于内网开发 / 测试环境
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }
            )

            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }

            builder
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true } // 跳过主机名校验
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
        }

        return builder.build()
    }
}
