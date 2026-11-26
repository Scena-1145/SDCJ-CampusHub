package com.scena.campushub.reminder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 国产 ROM 的后台限制引导。
 *
 * 这些 ROM 会掐掉 App 的 AlarmManager 闹钟（哪怕用了
 * setExactAndAllowWhileIdle），需要用户手动开「自启动 / 后台运行 / 不省电优化」。
 * 原生 Android（Pixel、三星等）没有这个问题。
 */
object OemHelper {

    enum class Oem(val label: String) {
        XIAOMI("小米 / 红米"),
        HUAWEI("华为 / 荣耀"),
        OPPO("OPPO / 一加 / realme"),
        VIVO("vivo / iQOO"),
        SAMSUNG("三星 One UI"),
        MOTOROLA("摩托罗拉 MyUI"),
        OTHER("其他")
    }

    fun current(): Oem {
        val brand = (Build.BRAND ?: "").lowercase()
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val s = "$brand $manufacturer"
        return when {
            s.contains("xiaomi") || s.contains("redmi") || s.contains("poco") -> Oem.XIAOMI
            s.contains("huawei") || s.contains("honor") -> Oem.HUAWEI
            s.contains("oppo") || s.contains("oneplus") || s.contains("realme") -> Oem.OPPO
            s.contains("vivo") || s.contains("iqoo") -> Oem.VIVO
            s.contains("samsung") -> Oem.SAMSUNG
            s.contains("motorola") || s.contains("moto") || s.contains("lenovo") -> Oem.MOTOROLA
            else -> Oem.OTHER
        }
    }

    /**
     * 是否属于「会掐闹钟」的厂商。
     *
     * 注意：三星必须算进来 —— One UI 的「设备维护 → 电池 → 应用电源管理」
     * 里进了「始终休眠的应用」列表后，AlarmManager 的闹钟完全不触发。
     * 摩托罗拉 MyUI 也有类似的电池优化限制。
     */
    fun needsGuide(oem: Oem = current()): Boolean = when (oem) {
        Oem.XIAOMI, Oem.HUAWEI, Oem.OPPO, Oem.VIVO, Oem.SAMSUNG, Oem.MOTOROLA -> true
        Oem.OTHER -> false
    }

    /** 各厂商的设置路径说明。 */
    fun guideText(oem: Oem = current(), sdk: Int = Build.VERSION.SDK_INT): String = when (oem) {
        Oem.XIAOMI ->
            "小米/红米需要两步：\n" +
                "1. 设置 → 应用设置 → 应用管理 → 校园枢纽 → 省电策略 → 选「无限制」\n" +
                "2. 设置 → 应用设置 → 应用管理 → 校园枢纽 → 自启动 → 打开"
        Oem.HUAWEI ->
            "华为/荣耀需要一步：\n" +
                "设置 → 应用 → 应用启动管理 → 找到「校园枢纽」→ 关闭「自动管理」，然后手动把三项全开（自启动 / 关联启动 / 后台活动）"
        Oem.OPPO ->
            "OPPO/一加/realme 需要两步：\n" +
                "1. 设置 → 电池 → 应用耗电管理 → 校园枢纽 → 允许后台运行\n" +
                "2. 设置 → 应用管理 → 校园枢纽 → 自启动 → 打开"
        Oem.VIVO ->
            "vivo/iQOO 需要两步：\n" +
                "1. 设置 → 电池 → 后台高耗电 → 校园枢纽 → 打开\n" +
                "2. i管家 → 应用管理 → 自启动 → 校园枢纽 → 打开"
        Oem.SAMSUNG ->
            if (sdk <= 28) {
                // One UI 1.0 / Android 9
                "三星 One UI（Android 9）有专门的应用休眠机制，不设置的话闹钟完全不触发：\n\n" +
                    "1. 设置 → 设备维护（Device care）→ 电池 → 应用电源管理\n" +
                    "2. 确认「校园枢纽」不在「始终休眠的应用」列表里\n" +
                    "   （如果在，点菜单 → 移出休眠）\n" +
                    "3. 把它加入「未监视的应用」\n\n" +
                    "另外：设置 → 应用 → 校园枢纽 → 电池 → 关闭「限制后台活动」"
            } else {
                "三星 One UI 需要两步：\n" +
                    "1. 设置 → 电池和设备维护 → 电池 → 后台使用限制\n" +
                    "2. 把「校园枢纽」加入「永不休眠的应用」，并关掉「将未使用的应用置于休眠状态」"
            }
        Oem.MOTOROLA ->
            "摩托罗拉 MyUI 需要两步：\n" +
                "1. 设置 → 电池 → 电池优化（或「应用电池管理」）→ 校园枢纽 → 选「不优化」\n" +
                "2. 设置 → 应用 → 校园枢纽 → 电池 → 选择「无限制」\n\n" +
                "MyUI 还有「自启动管理」，如果找不到，用系统的 设置 → 应用和通知 → 特殊应用权限 查看。"
        Oem.OTHER ->
            "如果闹钟不响，请到系统设置里把「校园枢纽」的\n" +
                "自启动、后台运行、省电优化（设为不优化）都打开。"
    }

    /** 尝试打开厂商的「自启动 / 后台管理」页面；失败返回 false。 */
    fun tryOpenSettings(ctx: Context, oem: Oem = current()): Boolean {
        val candidates: List<ComponentName> = when (oem) {
            Oem.XIAOMI -> listOf(
                ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            )
            Oem.HUAWEI -> listOf(
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            )
            Oem.OPPO -> listOf(
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")
            )
            Oem.VIVO -> listOf(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            )
            // 三星：设备维护 / 电池 / 应用电源管理
            Oem.SAMSUNG -> listOf(
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.ram.RamActivity")
            )
            // 摩托罗拉：电池优化 / 应用信息
            Oem.MOTOROLA -> listOf(
                ComponentName("com.motorola.motocare", "com.motorola.motocare.internal.settings.BatteryActivity"),
                ComponentName("com.motorola.batterymanager", "com.motorola.batterymanager.ui.BatteryActivity")
            )
            else -> emptyList()
        }

        for (c in candidates) {
            try {
                ctx.startActivity(
                    Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return true
            } catch (e: Exception) {
                // 试下一个
            }
        }

        // 兜底：应用详情页
        return try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 是否已被系统排除在电池优化之外（原生 Android 的等效开关）。 */
    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return try {
            pm.isIgnoringBatteryOptimizations(ctx.packageName)
        } catch (e: Throwable) {
            true
        }
    }

    /**
     * 应用待机桶（API 28+）。RESTRICTED/RARE 桶会显著延迟甚至吞掉闹钟。
     * 返回中文描述；无法查询时返回空串。
     */
    fun standbyBucket(ctx: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return ""
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return ""
            val bucket = usm.appStandbyBucket
            when (bucket) {
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active（活跃，不限）"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set（正常）"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent（偶尔受限）"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE -> "rare（⚠️ 闹钟会被大幅延迟）"
                android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted（❌ 闹钟基本不触发）"
                else -> "未知($bucket)"
            }
        } catch (e: Throwable) {
            ""
        }
    }

    fun requestIgnoreBatteryOptimizations(ctx: Context): Boolean = try {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    } catch (e: Exception) {
        false
    }

    fun hasShownGuide(ctx: Context): Boolean =
        ctx.getSharedPreferences("campushub_reminder", Context.MODE_PRIVATE)
            .getBoolean("guide_shown", false)

    fun markGuideShown(ctx: Context) {
        ctx.getSharedPreferences("campushub_reminder", Context.MODE_PRIVATE)
            .edit().putBoolean("guide_shown", true).apply()
    }
}
