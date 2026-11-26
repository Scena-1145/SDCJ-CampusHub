package com.scena.campushub.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.scena.campushub.R
import com.scena.campushub.data.Course
import com.scena.campushub.data.CxCourse

/** 列表项与通用小控件的构造工具。颜色一律取自主题，保证深色模式可读。 */
object Rows {

    fun dp(ctx: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    private fun themeColor(ctx: Context, attrRes: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attrRes, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(ctx, tv.resourceId) else tv.data
    }

    /** 课程行：左侧时间，右侧课程名 + 地点/教师。 */
    fun course(ctx: Context, c: Course, detail: Boolean = false): View {
        val primary = themeColor(ctx, android.R.attr.textColorPrimary)
        val secondary = themeColor(ctx, android.R.attr.textColorSecondary)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(ctx, 9f), 0, dp(ctx, 9f))
        }

        root.addView(TextView(ctx).apply {
            text = c.timeText.ifBlank { c.sections.let { if (it.isBlank()) "--" else it } }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ContextCompat.getColor(ctx, R.color.brand))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.TOP
            minWidth = dp(ctx, 66f)
        })

        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(ctx).apply {
            text = c.name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(primary)
            typeface = Typeface.DEFAULT_BOLD
        })
        val sub = buildList {
            if (c.room.isNotBlank()) add(c.room)
            if (c.teacher.isNotBlank()) add(c.teacher)
            if (detail && c.weeks.isNotBlank()) add("第${c.weeks}周")
        }.joinToString(" · ")
        if (sub.isNotBlank()) {
            col.addView(TextView(ctx).apply {
                text = sub
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(secondary)
                setPadding(0, dp(ctx, 3f), 0, 0)
            })
        }
        root.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return root
    }

    fun hint(ctx: Context, text: String): View = TextView(ctx).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(themeColor(ctx, android.R.attr.textColorSecondary))
        setPadding(0, dp(ctx, 10f), 0, dp(ctx, 10f))
    }

    /** 学习通课程行（可点击进入 WebView）。 */
    fun cxCourse(ctx: Context, c: CxCourse, onClick: (CxCourse) -> Unit): View {
        val primary = themeColor(ctx, android.R.attr.textColorPrimary)
        val secondary = themeColor(ctx, android.R.attr.textColorSecondary)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(ctx, 2f), dp(ctx, 11f), dp(ctx, 2f), dp(ctx, 11f))
            setOnClickListener { onClick(c) }
            addView(TextView(ctx).apply {
                text = c.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(primary)
            })
            addView(TextView(ctx).apply {
                text = listOfNotNull(c.teacher.takeIf { it.isNotBlank() }, "点击进入")
                    .joinToString(" · ")
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTextColor(secondary)
                setPadding(0, dp(ctx, 3f), 0, 0)
            })
        }
    }

    /** 通知聚合行。 */
    fun notifyRow(
        ctx: Context,
        appLabel: String,
        title: String,
        text: String,
        time: String
    ): View {
        val primary = themeColor(ctx, android.R.attr.textColorPrimary)
        val secondary = themeColor(ctx, android.R.attr.textColorSecondary)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 2f), dp(ctx, 9f), dp(ctx, 2f), dp(ctx, 9f))

            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(ctx).apply {
                    this.text = appLabel
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(ContextCompat.getColor(ctx, R.color.brand))
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(ctx).apply {
                    this.text = "  $time"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(secondary)
                })
            })

            addView(TextView(ctx).apply {
                this.text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(primary)
                setPadding(0, dp(ctx, 2f), 0, 0)
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            })

            if (text.isNotBlank()) {
                addView(TextView(ctx).apply {
                    this.text = text
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(secondary)
                    setPadding(0, dp(ctx, 2f), 0, 0)
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }
        }
    }

    /** 深链自测行。 */
    fun probeRow(ctx: Context, label: String, uri: String, onClick: () -> Unit): View {
        val primary = themeColor(ctx, android.R.attr.textColorPrimary)
        val secondary = themeColor(ctx, android.R.attr.textColorSecondary)

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setPadding(dp(ctx, 2f), dp(ctx, 10f), dp(ctx, 2f), dp(ctx, 10f))
            setOnClickListener { onClick() }
            addView(TextView(ctx).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(primary)
                typeface = Typeface.DEFAULT_BOLD
            })
            addView(TextView(ctx).apply {
                text = uri
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(secondary)
                setPadding(0, dp(ctx, 2f), 0, 0)
            })
        }
    }
}
