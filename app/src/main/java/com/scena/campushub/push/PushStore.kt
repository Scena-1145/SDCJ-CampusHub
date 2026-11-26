package com.scena.campushub.push

import android.content.Context

/** 推送 token 的本地记录（用于在 App 内展示，方便自测发消息）。 */
object PushStore {

    private const val SP = "campushub_push"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(SP, Context.MODE_PRIVATE)

    fun saveToken(ctx: Context, channel: String, token: String) {
        prefs(ctx).edit().putString("channel", channel).putString("token", token).apply()
    }

    fun channel(ctx: Context): String? = prefs(ctx).getString("channel", null)

    fun token(ctx: Context): String? = prefs(ctx).getString("token", null)

    fun clear(ctx: Context) = prefs(ctx).edit().clear().apply()
}
