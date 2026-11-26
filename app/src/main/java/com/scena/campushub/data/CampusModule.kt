package com.scena.campushub.data

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
    val reason: String = ""
)

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

    val modules: List<CampusModule> = listOf(
        CampusModule(ID_SCHEDULE, "课表", "今日课程", ModuleState.AVAILABLE),
        CampusModule(ID_NEWS, "通知公告", "校园新闻", ModuleState.AVAILABLE),
        CampusModule(ID_PROFILE, "个人信息", "学籍资料", ModuleState.AVAILABLE),

        CampusModule(
            ID_SCORE, "成绩", "考试成绩", ModuleState.NEEDS_INTRANET,
            "成绩数据在教务系统（jwglxt）里，与校园门户是两套服务。\n" +
                "需要教务系统地址，且通常只能在校园网内访问。\n\n" +
                "拿到地址后可在「项目审查与技术路线.md §8.2」里按步骤接入。"
        ),
        CampusModule(
            ID_EXAM, "考试安排", "考场与时间", ModuleState.NEEDS_INTRANET,
            "考试安排同样来自教务系统，需要教务系统地址与校园网访问权限。"
        ),
        CampusModule(
            ID_TODO, "待办审批", "流程待办", ModuleState.NEEDS_INTRANET,
            "待办走「移动办公」服务（接口前缀 /ly-pd-mb），它不在 mcp.sduc.edu.cn:8090 这个网关上。\n" +
                "需要该服务的独立地址。"
        ),
        CampusModule(
            ID_MEETING, "会议", "会议通知与签到", ModuleState.NEEDS_INTRANET,
            "会议模块属于移动办公服务（/ly-pd-mb 或 /open/meeting），需要该服务地址。"
        ),
        CampusModule(
            ID_ATTENDANCE, "考勤签到", "签到/查岗", ModuleState.NEEDS_INTRANET,
            "考勤签到需要定位与内网服务，且通常只在校园范围内可用。"
        ),
        CampusModule(
            ID_CONTACT, "通讯录", "部门与班级联系", ModuleState.NEEDS_INTRANET,
            "通讯录接口依赖内网的组织架构服务，当前网络无法访问。"
        ),
        CampusModule(
            ID_CARD, "一卡通", "余额与消费", ModuleState.NEEDS_THIRDPARTY,
            "一卡通是独立的 H5 微应用，需要它自己的系统地址（通常由学校单独发布）。"
        ),
        CampusModule(
            ID_FEEDBACK, "报修反馈", "意见与报修", ModuleState.NEEDS_INTRANET,
            "报修/反馈走内网工单服务，需要该服务地址。"
        ),
        CampusModule(
            ID_SCHOOL, "校情与地图", "校园导览", ModuleState.NEEDS_THIRDPARTY,
            "校情/校园地图是 H5 页面（原 App 里对应 /school/map、/school/situation 路由），" +
                "需要门户的 H5 服务地址。"
        )
    )

    fun byId(id: String): CampusModule? = modules.firstOrNull { it.id == id }
}
