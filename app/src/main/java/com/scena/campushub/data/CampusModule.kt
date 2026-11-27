package com.scena.campushub.data

import com.scena.campushub.Config

/**
 * 校园平台的一个功能模块。
 *
 * 目录按被逆向 APK（SDCJ 3.2.0）里的实际 Activity/接口清单整理，
 * 不是凭空编的——每一项目前对应原 App 里真实存在的模块。
 */
enum class ModuleState {
    /** 已打通，点了能进 */
    AVAILABLE,

    /** 依赖校园内网或教务/移动办公服务，当前网络拿不到 */
    NEEDS_INTRANET,

    /** 需要第三方系统地址（H5 微应用 / 一卡通 / 图书馆等） */
    NEEDS_THIRDPARTY,

    /** 接口存在但服务端未部署或返回异常 */
    NOT_READY
}

data class CampusModule(
    val id: String,
    val name: String,
    val subtitle: String,
    val state: ModuleState,
    val url: String? = null,
    val reason: String = ""
) {
    /** 点击后在 App 内打开网页，而不是弹「暂不可用」。 */
    val openable: Boolean get() = state == ModuleState.AVAILABLE && url != null
}

object CampusCatalog {

    const val ID_SCHEDULE = "schedule"
    const val ID_SCORE = "score"
    const val ID_EXAM = "exam"
    const val ID_NEWS = "news"
    const val ID_TODO = "todo"
    const val ID_MEETING = "meeting"
    const val ID_ATTENDANCE = "attendance"
    const val ID_CONTACT = "contact"
    const val ID_CARD = "card"
    const val ID_FEEDBACK = "feedback"
    const val ID_SCHOOL = "school"
    const val ID_PROFILE = "profile"
    const val ID_LIBRARY = "library"
    const val ID_MAIL = "mail"
    const val ID_WEBVPN = "webvpn"

    val modules: List<CampusModule> = listOf(
        // ---- 原生接入：走移动门户接口 ----
        CampusModule(ID_SCHEDULE, "课表", "今日课程", ModuleState.AVAILABLE),
        CampusModule(ID_NEWS, "通知公告", "校园新闻", ModuleState.AVAILABLE),
        CampusModule(ID_PROFILE, "个人信息", "学籍资料", ModuleState.AVAILABLE),

        // ---- 教务系统（正方 jsxsd）----
        CampusModule(
            ID_SCORE, "成绩", "教务系统", ModuleState.AVAILABLE,
            url = Config.JWXT + "/jsxsd/framework/xsMain.jsp",
            reason = "成绩在教务系统里，首次打开需登录一次（学号 + 教务密码），之后免登录。"
        ),
        CampusModule(
            ID_EXAM, "考试安排", "教务系统", ModuleState.AVAILABLE,
            url = Config.JWXT + "/jsxsd/framework/xsMain.jsp",
            reason = "考试安排在教务系统 → 考试报名 / 考试安排。"
        ),

        // ---- 一站式服务大厅（联奕）----
        CampusModule(
            ID_TODO, "待办审批", "办事大厅", ModuleState.AVAILABLE,
            url = Config.HALL,
            reason = "待办与审批走一站式服务大厅，用统一身份认证登录。"
        ),
        CampusModule(
            ID_MEETING, "会议", "办事大厅", ModuleState.AVAILABLE,
            url = Config.HALL,
            reason = "会议通知与签到在办事大厅的应用列表里。"
        ),
        CampusModule(
            ID_ATTENDANCE, "考勤签到", "办事大厅", ModuleState.AVAILABLE,
            url = Config.HALL,
            reason = "网页端可查考勤记录；需要 GPS 定位的签到仍要用原版 App。"
        ),
        CampusModule(
            ID_CONTACT, "通讯录", "办事大厅", ModuleState.AVAILABLE,
            url = Config.HALL,
            reason = "部门与班级联系人在办事大厅 / 门户的组织架构里。"
        ),
        CampusModule(
            ID_CARD, "一卡通", "办事大厅", ModuleState.AVAILABLE,
            url = Config.HALL,
            reason = "一卡通余额与消费在办事大厅的一卡通应用里。"
        ),
        CampusModule(
            ID_FEEDBACK, "报修反馈", "办事大厅", ModuleState.AVAILABLE,
            url = Config.HALL,
            reason = "报修工单在办事大厅提交与跟踪。"
        ),

        // ---- 其它已确认的校内站点 ----
        CampusModule(ID_LIBRARY, "图书馆", "馆藏与借阅", ModuleState.AVAILABLE, url = Config.LIBRARY),
        CampusModule(ID_MAIL, "校园邮箱", "学校邮箱", ModuleState.AVAILABLE, url = Config.SCHOOL_MAIL),
        CampusModule(ID_SCHOOL, "学校官网", "校情与新闻", ModuleState.AVAILABLE, url = Config.SCHOOL_WWW),
        CampusModule(
            ID_WEBVPN, "WebVPN", "校外访问内网", ModuleState.AVAILABLE,
            url = Config.WEBVPN,
            reason = "在校外用 WebVPN 登录后，才能打开只对内网开放的系统（如校园网盘、部分业务系统）。"
        )
    )

    fun byId(id: String): CampusModule? = modules.firstOrNull { it.id == id }
}
