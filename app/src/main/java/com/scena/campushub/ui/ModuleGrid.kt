package com.scena.campushub.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.scena.campushub.R
import com.scena.campushub.data.CampusModule
import com.scena.campushub.data.ModuleState

/** 3 列功能宫格。 */
object ModuleGrid {

    private const val PER_ROW = 3

    fun render(container: LinearLayout, modules: List<CampusModule>, onClick: (CampusModule) -> Unit) {
        val ctx = container.context
        container.removeAllViews()

        modules.chunked(PER_ROW).forEach { rowItems ->
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { m -> row.addView(tile(ctx, m, onClick), cellParams(ctx)) }
            repeat(PER_ROW - rowItems.size) { row.addView(View(ctx), cellParams(ctx)) }
            container.addView(row)
        }
    }

    private fun cellParams(ctx: Context) =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            val m = Rows.dp(ctx, 4f)
            setMargins(m, m, m, m)
        }

    private fun tile(ctx: Context, module: CampusModule, onClick: (CampusModule) -> Unit): View {
        val available = module.state == ModuleState.AVAILABLE

        val card = MaterialCardView(ctx).apply {
            radius = Rows.dp(ctx, 14f).toFloat()
            strokeWidth = Rows.dp(ctx, 1f)
            strokeColor = ContextCompat.getColor(ctx, R.color.card_stroke)
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            alpha = if (available) 1f else 0.6f
            setOnClickListener { onClick(module) }
        }

        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                Rows.dp(ctx, 4f), Rows.dp(ctx, 14f),
                Rows.dp(ctx, 4f), Rows.dp(ctx, 14f)
            )
        }
        box.addView(TextView(ctx).apply {
            text = module.name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                if (available) ContextCompat.getColor(ctx, R.color.brand)
                else themeColor(ctx, android.R.attr.textColorSecondary)
            )
            gravity = Gravity.CENTER
        })
        box.addView(TextView(ctx).apply {
            text = stateLabel(module)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(themeColor(ctx, android.R.attr.textColorSecondary))
            gravity = Gravity.CENTER
            setPadding(0, Rows.dp(ctx, 3f), 0, 0)
        })

        card.addView(box)
        return card
    }

    private fun stateLabel(m: CampusModule): String = when (m.state) {
        ModuleState.AVAILABLE -> m.subtitle
        ModuleState.NEEDS_INTRANET -> "需内网"
        ModuleState.NEEDS_THIRDPARTY -> "需系统地址"
        ModuleState.NOT_READY -> "未就绪"
    }

    private fun themeColor(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(ctx, tv.resourceId) else tv.data
    }
}
