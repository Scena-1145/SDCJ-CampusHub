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

    /**
     * 校内系统地址。2026-11-26 从校园网实测可达（公网同样能打开），
     * 全部走 HTTPS，直接在内置 WebView 里登录使用。
     */
    const val JWXT = "https://jwxt.sduc.edu.cn"        // 教务系统（正方 jsxsd）
    const val HALL = "https://ehall.sduc.edu.cn"       // 一站式服务大厅（联奕）
    const val CAS = "https://cas.sduc.edu.cn"          // 统一身份认证（lyuapServer）
    const val PORTAL = "https://portal.sduc.edu.cn"    // 智慧校园（校外入口）
    const val WEBVPN = "https://vpn.sduc.edu.cn"       // WebVPN
    const val LIBRARY = "https://tsg.sduc.edu.cn"      // 图书馆
    const val SCHOOL_MAIL = "https://mail.sduc.edu.cn" // 校园邮箱
    const val SCHOOL_WWW = "https://www.sduc.edu.cn"   // 学校官网

    /**
     * 校园网探测点：这些 RFC1918 地址只在连上校园网时可连，
     * 公网访问不到，是最可靠的「在不在校内」判断依据。
     */
    val CAMPUS_PROBES = listOf(
        "192.168.65.14" to 8090,
        "192.168.68.15" to 443,
        "192.168.82.5" to 80
    )

    /** 今日校园（金智，加固）。 */
    const val JRCAMPUS_PKG = "com.wisedu.cpdaily"

    /** 通知聚合要监听的包名。 */
    val NOTIFY_PACKAGES = setOf(CAMPUS_PKG, CHAOXING_PKG, JRCAMPUS_PKG)

    /**
     * 允许在 WebView 里调用相机 / 麦克风 / 定位的域名（含子域）。
     *
     * WebView 可以点到任意链接，不设白名单的话，用户一旦跳到恶意页面，
     * 而 App 已持有 CAMERA / RECORD_AUDIO / ACCESS_FINE_LOCATION，
     * 页面就能无提示地开摄像头。加域名前先想清楚它是否真的需要传感器。
     */
    val WEB_SENSOR_DOMAINS = listOf("chaoxing.com")

    /** 来源是否落在 [WEB_SENSOR_DOMAINS] 内。origin 形如 https://mooc1.chaoxing.com。 */
    fun isTrustedSensorOrigin(origin: String?): Boolean {
        val host = try {
            android.net.Uri.parse(origin ?: return false).host?.lowercase()
        } catch (e: Exception) {
            null
        } ?: return false
        return WEB_SENSOR_DOMAINS.any { host == it || host.endsWith(".$it") }
    }

    const val PREFS = "campushub"
}
