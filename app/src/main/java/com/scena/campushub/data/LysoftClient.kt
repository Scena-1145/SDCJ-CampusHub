package com.scena.campushub.data

import android.content.Context
import android.provider.Settings
import android.util.Base64
import com.scena.campushub.Config
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 联奕「ydxy」移动门户协议客户端。
 *
 * 流程：
 *  1. GET  /open/security/asym/key (Asym-Key: public)  取 RSA 公钥
 *  2. POST /open/security/asym/key (Asym-Key: private) RSA 包住随机 AES-128 密钥，换回 onlyCode
 *  3. POST /open/auth/login                            AES 加密账号密码登录，换回 session_key
 *  4. 其它 API                                          AES 加密请求体，Access-Token 携带会话
 */
class LysoftClient(private val ctx: Context) {

    data class Session(
        val aesKeyB64: String,
        val onlyCode: String,
        val sessionKey: String,
        val userId: String,
        val userType: String,
        val xxdm: String,
        val schoolId: String,
        val displayName: String,
        val className: String = "",
        val department: String = "",
        val schoolName: String = "",
        val professionName: String = "",
        val grade: String = "",
        val mobile: String = "",
        val identity: String = "",
        val bjid: String = ""
    )

    class ApiException(message: String, val code: String = "") : Exception(message)

    private val deviceId: String by lazy {
        try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "android"
        } catch (e: Exception) {
            "android"
        }
    }

    // ---------------------------------------------------------------- 密码学

    private fun aesEncrypt(text: String, keyB64: String): String {
        val key = SecretKeySpec(Base64.decode(keyB64, Base64.DEFAULT), "AES")
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, key)
        return Base64.encodeToString(c.doFinal(text.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun aesDecrypt(b64: String, keyB64: String): String {
        val key = SecretKeySpec(Base64.decode(keyB64, Base64.DEFAULT), "AES")
        val c = Cipher.getInstance("AES/ECB/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, key)
        return String(c.doFinal(Base64.decode(b64.trim(), Base64.DEFAULT)), Charsets.UTF_8)
    }

    private fun rsaEncrypt(plain: String, pubKeyB64: String): String {
        val kf = KeyFactory.getInstance("RSA")
        val pub = kf.generatePublic(X509EncodedKeySpec(Base64.decode(pubKeyB64.trim(), Base64.DEFAULT)))
        val c = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        c.init(Cipher.ENCRYPT_MODE, pub)
        return Base64.encodeToString(c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    // ---------------------------------------------------------------- HTTP

    private fun http(
        url: String,
        method: String,
        body: String?,
        headers: Map<String, String>
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "okhttp/3.12.0")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) throw ApiException("HTTP $code", code.toString())
            return text
        } finally {
            conn.disconnect()
        }
    }

    // ---------------------------------------------------------------- 协议

    /** 返回 (aesKeyB64, onlyCode) */
    private fun keyExchange(): Pair<String, String> {
        val url = Config.CAMPUS_API + "/open/security/asym/key"
        val pubRes = JSONObject(http(url, "GET", null, mapOf("Asym-Key" to "public")))
        if (pubRes.optString("code") != "0") {
            throw ApiException("获取公钥失败: " + pubRes.optString("msg"), pubRes.optString("code"))
        }
        val pubKey = pubRes.getString("data")
        val aesKeyB64 = Base64.encodeToString(ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
        val payload = rsaEncrypt(JSONObject().put("sk", aesKeyB64).toString(), pubKey)
        val upRes = JSONObject(http(url, "POST", payload, mapOf("Asym-Key" to "private")))
        if (upRes.optString("code") != "0") {
            throw ApiException("上传密钥失败: " + upRes.optString("msg"), upRes.optString("code"))
        }
        val plain = aesDecrypt(upRes.getString("data"), aesKeyB64).trim()
        val onlyCode = try {
            val o = JSONObject(plain)
            o.optString("onlyCode").ifBlank { o.optString("onlycode").ifBlank { o.optString("code", plain) } }
        } catch (e: Exception) {
            plain
        }
        if (onlyCode.isBlank()) throw ApiException("onlyCode 为空")
        return aesKeyB64 to onlyCode
    }

    private fun accessToken(session: Session): String {
        val head = JSONObject()
            .put("clientVersion", CLIENT_VERSION)
            .put("sessionKey", session.sessionKey)
            .put("userId", session.userId)
            .put("userType", session.userType)
            .put("xxdm", session.xxdm)
        return aesEncrypt(head.toString(), session.aesKeyB64)
    }

    fun login(userId: String, password: String, schoolId: String = DEFAULT_SCHOOL_ID): Session {
        val (aesKey, onlyCode) = keyExchange()
        val head = JSONObject()
            .put("clientVersion", CLIENT_VERSION)
            .put("sessionKey", "")
            .put("userId", "")
            .put("userType", "")
            .put("xxdm", schoolId)
        val body = JSONObject()
            .put("passw", password)
            .put("schoolId", schoolId)
            .put("userId", userId)
            .put("androidClientVersionCode", "52")
            .put("checkCode", "")
            .put("city", "")
            .put("clientVersion", CLIENT_VERSION)
            .put("deviceId", deviceId)
            .put("deviceModel", android.os.Build.MODEL ?: "Android")
            .put("deviceType", "1")
            .put("deviceVersion", android.os.Build.VERSION.RELEASE ?: "13")
            .put("imei", "")
            .put("latitude", "")
            .put("longitude", "")
            .put("phoneNum", "")
            .put("province", "")
            .put("serverApiVersion", CLIENT_VERSION)

        val resText = http(
            Config.CAMPUS_API + "/open/auth/login", "POST",
            aesEncrypt(body.toString(), aesKey),
            mapOf(
                "Access-Token" to aesEncrypt(head.toString(), aesKey),
                "Unique-Code" to onlyCode,
                "AppType" to "ydxy"
            )
        )
        val env = JSONObject(resText)
        if (env.optString("code") != "0") {
            throw ApiException(env.optString("msg").ifBlank { "登录失败" }, env.optString("code"))
        }
        val me = JSONObject(aesDecrypt(env.getString("data"), aesKey))
        val sessionKey = me.optString("session_key").ifBlank { me.optString("sessionKey") }
        if (sessionKey.isBlank()) throw ApiException("登录成功但没有 session_key")
        return Session(
            aesKeyB64 = aesKey,
            onlyCode = onlyCode,
            sessionKey = sessionKey,
            userId = me.optString("userId").ifBlank { userId },
            userType = me.optString("userType"),
            xxdm = me.optString("schoolId").ifBlank { schoolId },
            schoolId = me.optString("schoolId").ifBlank { schoolId },
            displayName = me.optString("userName").ifBlank {
                me.optString("xm").ifBlank { me.optString("name") }
            },
            className = me.optString("className"),
            department = me.optString("department"),
            schoolName = me.optString("schoolName"),
            professionName = me.optString("professionName"),
            grade = me.optString("grade"),
            mobile = me.optString("mobileNo"),
            identity = me.optString("identity"),
            bjid = me.optString("bjid")
        )
    }

    /**
     * 调用需要登录的接口。body 为明文 JSON，返回解密后的 data（可能是对象或数组）。
     */
    fun call(path: String, body: JSONObject, session: Session): String {
        val url = if (path.startsWith("http")) path else Config.CAMPUS_API + path
        val resText = http(
            url, "POST",
            aesEncrypt(body.toString(), session.aesKeyB64),
            mapOf(
                "Access-Token" to accessToken(session),
                "Unique-Code" to session.onlyCode,
                "AppType" to "ydxy"
            )
        )
        val env = JSONObject(resText)
        if (env.optString("code") != "0") {
            throw ApiException(env.optString("msg").ifBlank { "请求失败: $path" }, env.optString("code"))
        }
        val data = env.optString("data")
        if (data.isBlank()) return "{}"
        return try {
            aesDecrypt(data, session.aesKeyB64)
        } catch (e: Exception) {
            data
        }
    }

    companion object {
        const val CLIENT_VERSION = "3.2.0"
        const val DEFAULT_SCHOOL_ID = "SDCJ"

        /**
         * 会话已作废的错误码：`0006` = 服务端解密数据出错（onlyCode / AES 密钥失效），
         * `401` / `403` 由 HTTP 层抛出，握手在本地断掉时 code 是空串。
         * 其余如 `0001`「系统异常」属于接口本身不可用，重登治不了它。
         */
        private val SESSION_DEAD_CODES = setOf("", "0006", "401", "403")

        fun isSessionDead(code: String): Boolean = code in SESSION_DEAD_CODES
    }
}
