package com.scena.campushub.data

/** 需要跳转的外部 App。 */
data class ThirdPartyApp(
    val id: String,
    val name: String,
    val packageName: String,
    val note: String
)

object ThirdPartyApps {

    val CHAOXING = ThirdPartyApp(
        id = "chaoxing",
        name = "学习通",
        packageName = "com.chaoxing.mobile",
        note = "拍照签到、定位签到必须用原版"
    )

    val JRCAMPUS = ThirdPartyApp(
        id = "jrcampus",
        name = "今日校园",
        packageName = "com.wisedu.cpdaily",
        note = "打卡、请假、签到必须用原版"
    )

    val all = listOf(CHAOXING, JRCAMPUS)
}

/**
 * 今日校园的候选深链。
 *
 * 来源：AndroidManifest 里 com.wisorg.wisedu.common.SchemeActivity 注册了
 * campusnextins / yiban / sac1d7ffb4 三个 scheme（未限定 host/path），
 * CpPushBrandActivity 注册了 campusgetuipush://com.wisedu.cpdaily/detail。
 * 具体参数格式因加固无法静态确定，需真机逐个验证。
 */
data class DeepLinkProbe(val label: String, val uri: String)

object JrcDeepLinks {
    const val PKG = "com.wisedu.cpdaily"

    val probes: List<DeepLinkProbe> = listOf(
        DeepLinkProbe("仅启动（最稳）", "package:$PKG"),
        DeepLinkProbe("推送页 campusgetuipush", "campusgetuipush://$PKG/detail"),
        DeepLinkProbe("通用跳板 campusnextins", "campusnextins://$PKG"),
        DeepLinkProbe("通用跳板 campusnextins/首页", "campusnextins://$PKG/home"),
        DeepLinkProbe("旧版 scheme yiban", "yiban://$PKG"),
        DeepLinkProbe("微信支付回调 sac1d7ffb4", "sac1d7ffb4://$PKG")
    )
}
