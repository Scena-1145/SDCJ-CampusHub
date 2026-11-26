package com.scena.campushub.data

data class NewsItem(val title: String, val date: String = "", val source: String = "")

/** 学习通课程（从已登录页面的课程列表 JSON 里解析出来的）。 */
data class CxCourse(
    val name: String,
    val courseId: String = "",
    val clazzId: String = "",
    val teacher: String = ""
)
