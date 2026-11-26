package com.scena.campushub.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 校园平台业务接口封装（全部走 LysoftClient 的加密通道）。 */
class CampusApi(ctx: Context) {

    private val client = LysoftClient(ctx)

    fun login(userId: String, password: String): LysoftClient.Session =
        client.login(userId, password)

    /** 今日课程。接口返回今天的课表，参数不生效。 */
    fun todayCourses(session: LysoftClient.Session): List<Course> {
        val raw = client.call("/api/homepageModule/getSchedule", JSONObject(), session)
        val root = JSONObject(raw)
        val arr = root.optJSONArray("result") ?: JSONArray()
        val list = ArrayList<Course>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val kssj = o.optString("KSSJ")
            val jssj = o.optString("JSSJ")
            list.add(
                Course(
                    name = o.optString("KCMC"),
                    start = clock(kssj),
                    end = clock(jssj),
                    room = o.optString("SKDD"),
                    teacher = o.optString("SKLS"),
                    dayOfWeek = o.optString("XQJ"),
                    weeks = o.optString("QSJSZ"),
                    sections = o.optString("DJJ")
                )
            )
        }
        return list.sortedWith(compareBy({ it.start.isBlank() }, { it.start }))
    }

    /** 首页各类未读数（通知公告 / 待阅件 / 流程待办）。 */
    fun tabCounts(session: LysoftClient.Session): Map<String, Int> {
        val raw = client.call("/api/homepageModule/getTabsJb", JSONObject(), session)
        val arr = JSONArray(raw)
        val out = LinkedHashMap<String, Int>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out[o.optString("tabId")] = o.optInt("num")
        }
        return out
    }

    /** 通知公告。该校需要 yyid 参数，当前账号可能返回空列表。 */
    fun newsList(session: LysoftClient.Session, pageSize: Int = 20): List<NewsItem> {
        val body = JSONObject().put("pageNum", 1).put("pageSize", pageSize)
        val raw = client.call("/api/newstab/getNewsListByYYID", body, session)
        val arr = try {
            JSONArray(raw)
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val out = ArrayList<NewsItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title").ifBlank { o.optString("TITLE") }
            if (title.isBlank()) continue
            out.add(
                NewsItem(
                    title = title,
                    date = o.optString("publishTime").ifBlank {
                        o.optString("PUBLISH_TIME").ifBlank { o.optString("createTime") }
                    },
                    source = o.optString("source").ifBlank { o.optString("NEWS_TYPE") }
                )
            )
        }
        return out
    }

    /** 把 "2026-11-24 08:10" 取成 "08:10"，无效返回空串。 */
    private fun clock(s: String): String {
        val m = Regex("(\\d{2}:\\d{2})").find(s) ?: return ""
        return m.groupValues[1]
    }
}
