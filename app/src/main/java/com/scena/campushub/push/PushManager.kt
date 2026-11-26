package com.scena.campushub.push

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.huawei.hms.aaid.HmsInstanceId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

sealed class PushState {
    data class Registered(val channel: String, val token: String) : PushState()
    data class Failed(val reason: String) : PushState()
    data class NotConfigured(val reason: String) : PushState()
}

/**
 * 双通道推送：华为/荣耀设备走 HMS Push，其余走 FCM。
 *
 * 不依赖 google-services.json / agconnect-services.json，
 * 配置通过 BuildConfig 注入，因此未配置时也能正常编译运行。
 */
object PushManager {

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private fun hasPackage(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    fun hasHms(ctx: Context): Boolean =
        hasPackage(ctx, "com.huawei.hwid") || hasPackage(ctx, "com.huawei.hms")

    fun hasGms(ctx: Context): Boolean = hasPackage(ctx, "com.google.android.gms")

    /** 在后台线程完成注册，回调切回主线程。 */
    fun register(ctx: Context, cb: (PushState) -> Unit) {
        val app = ctx.applicationContext
        exec.execute {
            val state = try {
                doRegister(app)
            } catch (t: Throwable) {
                PushState.Failed(t.message ?: t.javaClass.simpleName)
            }
            main.post { cb(state) }
        }
    }

    private fun doRegister(ctx: Context): PushState {
        if (!PushConfig.anyReady) return PushState.NotConfigured("未填写推送配置")

        if (PushConfig.hmsReady && hasHms(ctx)) return registerHms(ctx)
        if (PushConfig.fcmReady && hasGms(ctx)) return registerFcm(ctx)

        return PushState.NotConfigured(
            if (hasHms(ctx) || hasGms(ctx)) "设备有推送服务，但对应配置未填写"
            else "设备缺少 HMS Core / GMS"
        )
    }

    private fun registerHms(ctx: Context): PushState {
        val token = HmsInstanceId.getInstance(ctx).getToken(PushConfig.hmsAppId, "HCM")
        if (token.isNullOrBlank()) return PushState.Failed("HMS 返回空 token")
        PushStore.saveToken(ctx, "HMS", token)
        return PushState.Registered("HMS", token)
    }

    private fun registerFcm(ctx: Context): PushState {
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId(PushConfig.fcmAppId)
                .setApiKey(PushConfig.fcmApiKey)
                .setProjectId(PushConfig.fcmProjectId)
                .setGcmSenderId(PushConfig.fcmSenderId)
                .build()
            FirebaseApp.initializeApp(ctx, options)
        }

        val latch = CountDownLatch(1)
        var token: String? = null
        var error: String? = null
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) token = task.result else error = task.exception?.message
            latch.countDown()
        }
        latch.await(20, TimeUnit.SECONDS)

        val tk = token
        return when {
            !tk.isNullOrBlank() -> {
                PushStore.saveToken(ctx, "FCM", tk)
                PushState.Registered("FCM", tk)
            }
            error != null -> PushState.Failed("FCM: $error")
            else -> PushState.Failed("FCM 获取 token 超时")
        }
    }
}
