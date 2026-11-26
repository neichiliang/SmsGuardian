package com.smsguardian.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Server酱³（sct.ftqq.com）推送客户端：把关键字短信转发到微信服务号。 */
object ServerChanClient {

    private const val ENDPOINT = "https://sctapi.ftqq.com/%s.send"

    /**
     * 发送一条微信提醒。
     * @return Result.success(服务端返回文本) 或 Result.failure(异常)
     */
    suspend fun send(sendKey: String, title: String, desp: String): Result<String> =
        withContext(Dispatchers.IO) {
            val key = sendKey.trim()
            if (key.isEmpty()) return@withContext Result.failure(IllegalArgumentException("SendKey 为空"))

            var conn: HttpURLConnection? = null
            try {
                val url = URL(String.format(ENDPOINT, key))
                conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.doOutput = true
                conn.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8"
                )

                val body = "title=" + URLEncoder.encode(title, "UTF-8") +
                        "&desp=" + URLEncoder.encode(desp, "UTF-8")
                conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.use {
                    BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { reader ->
                        reader.readText()
                    }
                } ?: ""

                if (code in 200..299) Result.success(text)
                else Result.failure(Exception("HTTP $code : $text"))
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                conn?.disconnect()
            }
        }
}
