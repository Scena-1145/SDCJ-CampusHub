# 只做代码收缩，不混淆符号名。
# 混淆本身能再省一点体积，但 Firebase / HMS 都靠类名反射初始化，
# 一旦削错只能等真机推送时才暴露，个人设备不方便反复试。
-dontobfuscate

# 推送与提醒的入口类整体保留（系统按类名实例化，将来挪出清单也不会断）
-keep class com.scena.campushub.push.** { *; }
-keep class * extends android.app.job.JobService
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.service.notification.NotificationListenerService
-keep class * extends com.google.firebase.messaging.FirebaseMessagingService
-keep class * extends com.huawei.hms.push.HmsMessageService

# HMS / Firebase 里引用了但本机 SDK 未必存在的类，缺了不该中断构建
-dontwarn com.huawei.**
-dontwarn com.google.firebase.**
-dontwarn org.apache.**
-dontwarn org.slf4j.**
