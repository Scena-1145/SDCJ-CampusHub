package com.scena.campushub.data

import android.content.Context
import com.scena.campushub.Config

/** 账号、登录会话与学籍资料的本地存储（仅本应用可读）。 */
class Store(ctx: Context) {

    private val sp = ctx.applicationContext.getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE)

    var userId: String
        get() = sp.getString("user_id", "") ?: ""
        set(v) = sp.edit().putString("user_id", v).apply()

    var password: String
        get() = sp.getString("password", "") ?: ""
        set(v) = sp.edit().putString("password", v).apply()

    var displayName: String
        get() = sp.getString("display_name", "") ?: ""
        set(v) = sp.edit().putString("display_name", v).apply()

    var className: String
        get() = sp.getString("class_name", "") ?: ""
        set(v) = sp.edit().putString("class_name", v).apply()

    private fun str(key: String) = sp.getString(key, "") ?: ""
    private fun put(key: String, v: String) = sp.edit().putString(key, v).apply()

    private var aesKey: String?
        get() = sp.getString("aes_key", null)
        set(v) = sp.edit().putString("aes_key", v).apply()

    private var onlyCode: String?
        get() = sp.getString("only_code", null)
        set(v) = sp.edit().putString("only_code", v).apply()

    private var sessionKey: String?
        get() = sp.getString("session_key", null)
        set(v) = sp.edit().putString("session_key", v).apply()

    private var userType: String?
        get() = sp.getString("user_type", null)
        set(v) = sp.edit().putString("user_type", v).apply()

    private var xxdm: String?
        get() = sp.getString("xxdm", null)
        set(v) = sp.edit().putString("xxdm", v).apply()

    fun saveSession(s: LysoftClient.Session) {
        aesKey = s.aesKeyB64
        onlyCode = s.onlyCode
        sessionKey = s.sessionKey
        userType = s.userType
        xxdm = s.xxdm
        displayName = s.displayName
        className = s.className
        put("department", s.department)
        put("school_name", s.schoolName)
        put("profession", s.professionName)
        put("grade", s.grade)
        put("mobile", s.mobile)
        put("identity", s.identity)
        put("bjid", s.bjid)
    }

    fun loadSession(): LysoftClient.Session? {
        val k = aesKey ?: return null
        val oc = onlyCode ?: return null
        val sk = sessionKey ?: return null
        if (userId.isBlank()) return null
        return LysoftClient.Session(
            aesKeyB64 = k,
            onlyCode = oc,
            sessionKey = sk,
            userId = userId,
            userType = userType ?: "",
            xxdm = xxdm ?: LysoftClient.DEFAULT_SCHOOL_ID,
            schoolId = xxdm ?: LysoftClient.DEFAULT_SCHOOL_ID,
            displayName = displayName,
            className = className,
            department = str("department"),
            schoolName = str("school_name"),
            professionName = str("profession"),
            grade = str("grade"),
            mobile = str("mobile"),
            identity = str("identity"),
            bjid = str("bjid")
        )
    }

    fun clearSession() {
        sp.edit().remove("aes_key").remove("only_code").remove("session_key")
            .remove("user_type").remove("xxdm").apply()
    }

    fun isLoggedIn(): Boolean = loadSession() != null
}
