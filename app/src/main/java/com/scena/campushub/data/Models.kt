package com.scena.campushub.data

data class Course(
    val name: String,
    val start: String = "",
    val end: String = "",
    val room: String = "",
    val teacher: String = "",
    val dayOfWeek: String = "",
    val weeks: String = "",
    val sections: String = ""
) {
    val timeText: String
        get() = when {
            start.isNotBlank() && end.isNotBlank() -> "$start-$end"
            sections.isNotBlank() -> "第 $sections 节"
            else -> ""
        }
}

/** 聚合首页所需的全部数据。null 表示该项尚未取到。 */
data class Dashboard(
    val userName: String? = null,
    val className: String? = null,
    val courses: List<Course>? = null,
    val unread: Int? = null,
    val todoCount: Int? = null,
    val chaoxing: String? = null,
    val error: String? = null
)
