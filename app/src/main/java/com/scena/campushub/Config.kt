package com.scena.campushub

/** 集中管理两个平台用到的地址与常量。 */
object Config {
    /** 校园平台（联奕移动门户）服务端。 */
    const val CAMPUS_BASE = "http://mcp.sduc.edu.cn:8090"
    const val CAMPUS_API = CAMPUS_BASE + "/mobileapi_ydxy"
    const val CAMPUS_PKG = "com.lysoft.android.lyyd.report.mobile.sdcj"

    /** 超星学习通。 */
    const val CHAOXING_HOME = "https://i.chaoxing.com"
    const val CHAOXING_PKG = "com.chaoxing.mobile"

    /** 今日校园（金智，加固）。 */
    const val JRCAMPUS_PKG = "com.wisedu.cpdaily"

    /** 通知聚合要监听的包名。 */
    val NOTIFY_PACKAGES = setOf(CAMPUS_PKG, CHAOXING_PKG, JRCAMPUS_PKG)

    const val PREFS = "campushub"
}
