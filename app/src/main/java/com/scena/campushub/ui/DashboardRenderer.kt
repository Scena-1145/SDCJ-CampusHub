package com.scena.campushub.ui

import android.view.View
import com.scena.campushub.data.Dashboard
import com.scena.campushub.databinding.ViewDashboardBinding

/** 把聚合数据渲染到首页。 */
class DashboardRenderer(private val b: ViewDashboardBinding) {

    fun render(d: Dashboard) {
        val ctx = b.root.context

        val who = listOfNotNull(d.userName?.takeIf { it.isNotBlank() }, d.className?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
        b.tvSubline.text = who

        val courses = d.courses
        b.tvCourseCount.text = courses?.let { "${it.size} 门" } ?: ""
        b.llSchedule.removeAllViews()
        if (courses.isNullOrEmpty()) {
            b.tvScheduleEmpty.visibility = View.VISIBLE
            b.tvScheduleEmpty.text = if (courses == null) "尚未获取" else "今天没有课"
        } else {
            b.tvScheduleEmpty.visibility = View.GONE
            courses.forEach { b.llSchedule.addView(Rows.course(ctx, it)) }
        }

        val parts = ArrayList<String>()
        d.unread?.let { parts.add("通知公告 $it") }
        d.todoCount?.let { parts.add("流程待办 $it") }
        b.tvUnread.text = if (parts.isEmpty()) "暂无数据" else parts.joinToString(" · ")

        d.chaoxing?.let { b.tvCxSummary.text = it }

        if (d.error != null) {
            b.tvStatus.visibility = View.VISIBLE
            b.tvStatus.text = d.error
        } else {
            b.tvStatus.visibility = View.GONE
        }
    }
}
