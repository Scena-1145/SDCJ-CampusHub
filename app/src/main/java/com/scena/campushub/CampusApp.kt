package com.scena.campushub

import android.app.Application
import android.os.Build
import com.google.android.material.color.DynamicColors

/**
 * Android 12+ 叠加 Material You 动态取色（跟随壁纸）。
 *
 * 之所以运行时叠加而不是用 Theme.Material3.DynamicColors.*：
 * 后者在 Material 1.12 里父主题是 Light，会让深色模式失效。
 * API 31 以下不调用，使用 res/values 里的 M2 静态主题。
 */
class CampusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                DynamicColors.applyToActivitiesIfAvailable(this)
            } catch (t: Throwable) {
                // 取色失败不影响使用，退回静态主题色
            }
        }
    }
}
