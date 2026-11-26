package com.scena.campushub.push

import com.scena.campushub.BuildConfig

/**
 * 推送配置，值来自 local.properties（构建时注入 BuildConfig）。
 * 全部留空 = 不启用推送，App 正常降级运行。
 */
object PushConfig {

    val fcmAppId: String = BuildConfig.PUSH_FCM_APP_ID
    val fcmApiKey: String = BuildConfig.PUSH_FCM_API_KEY
    val fcmProjectId: String = BuildConfig.PUSH_FCM_PROJECT_ID
    val fcmSenderId: String = BuildConfig.PUSH_FCM_SENDER_ID
    val hmsAppId: String = BuildConfig.PUSH_HMS_APP_ID

    val fcmReady: Boolean
        get() = fcmAppId.isNotBlank() && fcmApiKey.isNotBlank() &&
                fcmProjectId.isNotBlank() && fcmSenderId.isNotBlank()

    val hmsReady: Boolean
        get() = hmsAppId.isNotBlank()

    val anyReady: Boolean
        get() = fcmReady || hmsReady
}
