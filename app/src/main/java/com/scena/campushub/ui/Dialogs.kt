package com.scena.campushub.ui

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scena.campushub.data.CampusModule
import com.scena.campushub.data.LysoftClient
import com.scena.campushub.data.ModuleState
import com.scena.campushub.data.NewsItem

/** 统一的功能提示窗。 */
object Dialogs {

    fun featureUnavailable(ctx: Context, module: CampusModule) {
        val (title, hint) = when (module.state) {
            ModuleState.NEEDS_INTRANET -> "需要校园内网" to "该功能依赖校内服务，当前网络访问不到。"
            ModuleState.NEEDS_THIRDPARTY -> "需要第三方系统地址" to "该功能由独立系统提供，需要它的地址才能接入。"
            ModuleState.NOT_READY -> "服务端未就绪" to "接口存在，但服务端当前返回异常。"
            ModuleState.AVAILABLE -> "暂不可用" to ""
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("${module.name} · $title")
            .setMessage(
                if (module.reason.isNotBlank()) "${module.reason}\n\n$hint"
                else hint
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    fun profile(ctx: Context, s: LysoftClient.Session?, onLogout: () -> Unit = {}) {
        if (s == null) {
            MaterialAlertDialogBuilder(ctx)
                .setTitle("个人信息")
                .setMessage("尚未登录校园平台。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        val rows = listOf(
            "姓名" to s.displayName,
            "学号" to s.userId,
            "班级" to s.className,
            "专业" to s.professionName,
            "系部" to s.department,
            "年级" to s.grade,
            "学校" to s.schoolName,
            "手机" to maskMobile(s.mobile),
            "身份证" to maskId(s.identity)
        ).filter { it.second.isNotBlank() }

        val text = rows.joinToString("\n") { (k, v) -> "$k：$v" }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("个人信息")
            .setMessage(if (text.isBlank()) "未获取到学籍资料。" else text)
            .setPositiveButton("关闭", null)
            .setNegativeButton("退出登录") { _, _ -> onLogout() }
            .show()
    }

    fun news(ctx: Context, items: List<NewsItem>, note: String?) {
        val body = when {
            items.isNotEmpty() -> items.joinToString("\n\n") { n ->
                if (n.date.isBlank()) "· ${n.title}" else "· ${n.title}\n  ${n.date}"
            }
            note != null -> note
            else -> "暂无通知公告。"
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle("通知公告")
            .setMessage(body)
            .setPositiveButton("关闭", null)
            .show()
    }

    fun info(ctx: Context, title: String, message: String) {
        MaterialAlertDialogBuilder(ctx)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun maskMobile(s: String): String =
        if (s.length >= 7) s.substring(0, 3) + "****" + s.substring(s.length - 4) else s

    private fun maskId(s: String): String =
        if (s.length >= 8) s.substring(0, 4) + "**********" + s.substring(s.length - 4) else s
}
