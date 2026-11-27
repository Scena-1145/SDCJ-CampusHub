package com.scena.campushub

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.scena.campushub.data.CampusApi
import com.scena.campushub.data.CampusCatalog
import com.scena.campushub.data.CampusModule
import com.scena.campushub.data.CampusNet
import com.scena.campushub.data.Course
import com.scena.campushub.data.CxCourse
import com.scena.campushub.data.Dashboard
import com.scena.campushub.data.DeepLinkProbe
import com.scena.campushub.data.JrcDeepLinks
import com.scena.campushub.data.LysoftClient
import com.scena.campushub.data.Store
import com.scena.campushub.databinding.ActivityMainBinding
import com.scena.campushub.databinding.DialogLoginBinding
import com.scena.campushub.databinding.ViewCampusBinding
import com.scena.campushub.databinding.ViewChaoxingBinding
import com.scena.campushub.databinding.ViewDashboardBinding
import com.scena.campushub.databinding.ViewJrcampusBinding
import com.scena.campushub.notify.NotifyHelper
import com.scena.campushub.notify.NotifyStore
import com.scena.campushub.push.Notifications
import com.scena.campushub.push.PushConfig
import com.scena.campushub.push.PushManager
import com.scena.campushub.push.PushState
import com.scena.campushub.reminder.OemHelper
import com.scena.campushub.reminder.ReminderScheduler
import com.scena.campushub.reminder.ReminderStore
import com.scena.campushub.reminder.ScheduleRefreshJob
import com.scena.campushub.ui.Bg
import com.scena.campushub.ui.DashboardRenderer
import com.scena.campushub.ui.Dialogs
import com.scena.campushub.ui.ModuleGrid
import com.scena.campushub.ui.Rows
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var dash: ViewDashboardBinding
    private lateinit var campus: ViewCampusBinding
    private lateinit var cx: ViewChaoxingBinding
    private lateinit var jrc: ViewJrcampusBinding
    private lateinit var cxProbe: WebView

    private lateinit var store: Store
    private lateinit var api: CampusApi
    private lateinit var renderer: DashboardRenderer

    private var session: LysoftClient.Session? = null
    private var chaoxingSummary: String? = null
    private var cxCourses: List<CxCourse> = emptyList()
    private var cxWebMode = true
    private var probeRunning = false

    private var pendingRequest: Pair<PermissionRequest, List<String>>? = null
    private var pendingGeo: Pair<String?, GeolocationPermissions.Callback>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dash = ViewDashboardBinding.inflate(layoutInflater, binding.content, false)
        campus = ViewCampusBinding.inflate(layoutInflater, binding.content, false)
        cx = ViewChaoxingBinding.inflate(layoutInflater, binding.content, false)
        jrc = ViewJrcampusBinding.inflate(layoutInflater, binding.content, false)
        binding.content.addView(dash.root)
        binding.content.addView(campus.root)
        binding.content.addView(cx.root)
        binding.content.addView(jrc.root)

        store = Store(this)
        api = CampusApi(this)
        renderer = DashboardRenderer(dash)

        bindHeader()
        bindActions()
        setupWeb()
        setupWebShell()
        hideProbeWebView()
        setupJrcampus()
        ModuleGrid.render(campus.llModules, CampusCatalog.modules) { onModuleClick(it) }
        checkCampusNet()

        binding.bottomNav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        show(R.id.nav_dashboard)
        initPush()

        if (store.isLoggedIn()) {
            session = store.loadSession()
            renderer.render(Dashboard())
            renderCampus(Dashboard())
            refreshAll()
        } else {
            renderer.render(Dashboard())
            renderCampus(Dashboard())
            showLoginDialog()
        }
    }

    // ------------------------------------------------------------ 推送

    private fun initPush() {
        Notifications.ensureChannel(this)
        Notifications.ensurePermission(this)

        if (!PushConfig.anyReady) {
            val deviceChannel = when {
                PushManager.hasHms(this) -> "HMS"
                PushManager.hasGms(this) -> "GMS"
                else -> "无"
            }
            dash.tvPushStatus.text =
                "远程推送：未配置（设备有 $deviceChannel）。国内网络下 FCM 基本不通，课表提醒用下面的本地提醒即可。"
            return
        }

        PushManager.register(this) { state ->
            dash.tvPushStatus.text = when (state) {
                is PushState.Registered -> "远程推送：${state.channel} 已注册 · ${state.token.take(12)}…"
                is PushState.Failed -> "远程推送：失败 · ${state.reason}（不影响本地课表提醒）"
                is PushState.NotConfigured -> "远程推送：未启用 · ${state.reason}"
            }
        }
    }

    // ------------------------------------------------------------ 头部与动作

    private fun bindHeader() {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        dash.tvGreeting.text = when (hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..18 -> "下午好"
            else -> "晚上好"
        }
    }

    private fun bindActions() {
        dash.btnRefresh.setOnClickListener { refreshAll() }
        dash.swipe.setOnRefreshListener { refreshAll() }
        dash.btnCxOpen.setOnClickListener {
            binding.bottomNav.selectedItemId = R.id.nav_chaoxing
            probeChaoxingCourses()
        }
        campus.btnNetCheck.setOnClickListener { checkCampusNet() }

        campus.btnOpenApp.setOnClickListener { openCampusApp() }
        campus.btnCampusRefresh.setOnClickListener { refreshAll() }
        campus.btnCampusLogin.setOnClickListener {
            if (store.isLoggedIn()) Dialogs.profile(this, session) { logout() } else showLoginDialog()
        }
        campus.tvCampusUser.setOnClickListener {
            if (!store.isLoggedIn()) showLoginDialog() else Dialogs.profile(this, session) { logout() }
        }

        dash.swReminder.isChecked = ReminderStore.isEnabled(this)
        dash.swReminder.setOnCheckedChangeListener { _, checked ->
            ReminderStore.setEnabled(this, checked)
            if (checked) {
                ReminderScheduler.reschedule(this)
                // 若课表缓存为空（例如启动时网络失败），补一次刷新，否则只会注册到汇总闹钟
                if (ReminderStore.cachedCourses(this).isEmpty() && store.isLoggedIn()) {
                    dash.tvReminderHint.text = "正在获取课表…"
                    refreshAll()
                }
                if (ReminderScheduler.needsExactPermission(this)) {
                    Dialogs.info(
                        this, "需要「闹钟和提醒」权限",
                        "Android 12 以上，精确到分钟的提醒需要单独授权。\n\n" +
                            "请到 设置 → 应用 → 校园枢纽 → 闹钟和提醒，允许后提醒才会准时。\n\n" +
                            "不授权也能用，只是可能延迟几分钟。"
                    )
                }
                maybeShowOemGuide(auto = true)
                updateReminderHint()
            } else {
                ReminderScheduler.cancelAll(this)
                ScheduleRefreshJob.cancel(this)
                updateReminderHint()
            }
        }
        // 提示行可点击 → 随时查看/重开后台权限设置
        dash.tvReminderHint.setOnClickListener { showOemGuide() }

        dash.btnReminderTest.setOnClickListener {
            if (ReminderScheduler.scheduleTest(this, 5)) {
                Dialogs.info(
                    this, "已安排测试提醒",
                    "5 秒后应弹出通知。\n\n" +
                        "· 如果有通知 → 闹钟链路正常，问题在别处（可能是课表没缓存）\n" +
                        "· 如果什么都没有 → 系统的后台限制或权限拦住了，点「自检」看详情"
                )
            } else {
                Dialogs.info(this, "安排失败", "无法注册测试闹钟，点「自检」查看错误记录。")
            }
        }

        dash.btnReminderDiag.setOnClickListener { showDiagnostics() }

        dash.btnNotifyAuth.setOnClickListener {
            if (NotifyHelper.isEnabled(this)) {
                renderNotify()
            } else {
                val dlg = MaterialAlertDialogBuilder(this)
                    .setTitle("开启通知聚合")
                    .setMessage(
                        "需要「通知使用权」才能读取今日校园、学习通、校园平台的通知并汇总到这里。\n\n" +
                            "· 只读取本机已显示的通知标题和内容\n" +
                            "· 不逆向对方 App、不需要账号密码、不联网上报\n" +
                            "· 随时可以在系统设置里关闭\n\n" +
                            "在接下来的页面找到「校园枢纽 · 通知聚合」并打开。"
                    )
                    .setPositiveButton("去设置") { _, _ ->
                        if (!NotifyHelper.openSettings(this)) {
                            Dialogs.info(this, "打不开设置", "请到 设置 → 通知 → 通知使用权 手动开启。")
                        }
                    }
                    .setNegativeButton("取消", null)
                    .create()
                dlg.show()
            }
        }

        updateReminderHint()
        renderNotify()
    }

    /** 渲染通知聚合卡片。 */
    private fun renderNotify() {
        val enabled = NotifyHelper.isEnabled(this)
        val items = NotifyStore.list(this)

        dash.btnNotifyAuth.text = if (enabled && items.isNotEmpty()) "清空" else "授权"

        if (!enabled) {
            dash.tvNotifyHint.text = "未授权。开启后可在这里看到三个 App 的通知。"
            dash.llNotify.removeAllViews()
            return
        }

        dash.tvNotifyHint.text =
            if (items.isEmpty()) "已授权，暂无通知。有新的会出现在这里。"
            else "已授权 · 最近 ${items.size} 条"

        val ctx = this
        dash.llNotify.removeAllViews()
        items.take(6).forEach { n ->
            dash.llNotify.addView(Rows.notifyRow(ctx, n.appLabel, n.title, n.text, n.timeText()))
        }

        if (items.isNotEmpty()) {
            // 已授权且有数据时，按钮变成清空
            dash.btnNotifyAuth.setOnClickListener {
                NotifyStore.clear(this)
                renderNotify()
            }
        }
    }

    /** 自检面板：把影响闹钟的每一项都列出来。 */
    private fun showDiagnostics() {
        val oem = OemHelper.current()
        val exact = ReminderScheduler.canExact(this)
        val battery = OemHelper.isIgnoringBatteryOptimizations(this)
        val enabled = ReminderStore.isEnabled(this)
        val courses = ReminderStore.cachedCourses(this)
        val cacheDate = ReminderStore.cachedDate(this)
        val today = ReminderStore.todayKeyPublic()
        val planned = ReminderStore.planned(this)
        val plannedAt = ReminderStore.plannedAt(this)
        val canPost = Notifications.canPost(this)
        val errors = ReminderStore.errors(this)

        val sb = StringBuilder()
        sb.appendLine("开关：").appendLine(if (enabled) "  ✅ 已开启" else "  ❌ 未开启")
        sb.appendLine()
        sb.appendLine("通知权限：").appendLine(if (canPost) "  ✅ 允许" else "  ❌ 被拒绝（去 设置→通知 打开）")
        sb.appendLine()
        sb.appendLine("精确闹钟：").appendLine(
            if (exact) "  ✅ 允许（setExactAndAllowWhileIdle）"
            else "  ⚠️ 未授权（降级为不精确，可能延迟）"
        )
        sb.appendLine()
        sb.appendLine("电池优化：").appendLine(
            if (battery) "  ✅ 已排除（不会被限制）" else "  ⚠️ 未排除（可能被系统限制）"
        )
        sb.appendLine()
        val bucket = OemHelper.standbyBucket(this)
        if (bucket.isNotBlank()) {
            sb.appendLine("应用待机桶：").appendLine("  $bucket")
            sb.appendLine()
        }
        sb.appendLine("课表缓存：")
        sb.appendLine("  日期 ${cacheDate.ifBlank { "（空）" }}")
        sb.appendLine("  今天 ${today}  " + if (cacheDate == today) "✅ 匹配" else "❌ 不匹配（缓存已过期）")
        sb.appendLine("  课程数 ${courses.size}")
        sb.appendLine()
        sb.appendLine("已注册闹钟（本地记账，共 ${planned.size} 条）：")
        if (planned.isEmpty()) {
            sb.appendLine("  ❌ 空 —— 这就是提醒不响的原因")
        } else {
            planned.forEach { sb.appendLine("  · ${it.time}  ${it.label}") }
        }
        if (plannedAt > 0) {
            val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                .format(java.util.Date(plannedAt))
            sb.appendLine("  （最后注册时间 $t）")
        }
        sb.appendLine()
        sb.appendLine("清晨自动刷课表：")
        sb.appendLine(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) "  ✅ 已排定（需联网，赶在 07:00 汇总之前）"
            else "  ❌ Android 6 不支持定时后台任务，需每天打开一次 App"
        )
        sb.appendLine()
        sb.appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("厂商类型：${oem.label}")
        if (OemHelper.needsGuide(oem)) {
            sb.appendLine("⚠️ 该厂商需要后台白名单，否则闹钟可能被掐")
        }
        if (Build.VERSION.SDK_INT >= 34 && planned.size > 1) {
            sb.appendLine()
            sb.appendLine("提示：Android 14+ 若长期不打开本应用，")
            sb.appendLine("系统可能冻结它导致闹钟不触发。偶尔打开一次即可。")
        }
        if (errors.isNotBlank()) {
            sb.appendLine()
            sb.appendLine("错误记录：")
            sb.appendLine(errors)
        }

        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle("提醒自检")
            .setMessage(sb.toString())
            .setPositiveButton("关闭", null)

        if (!exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dlg.setNeutralButton("授权精确闹钟") { _, _ ->
                if (!ReminderScheduler.openExactAlarmSettings(this)) {
                    Dialogs.info(this, "打不开", "请到 设置 → 应用 → 特殊权限 → 闹钟和提醒 里允许。")
                }
            }
        } else if (!battery && OemHelper.needsGuide(oem)) {
            dlg.setNeutralButton("后台设置") { _, _ -> showOemGuide() }
        } else if (planned.isEmpty() && enabled) {
            dlg.setNeutralButton("重新注册") { _, _ ->
                ReminderScheduler.reschedule(this)
                updateReminderHint()
                Dialogs.info(this, "已重新注册", "再看一次自检确认。若仍为空，请刷新课表（课表缓存可能是空的）。")
            }
        }
        dlg.show()
    }

    /** 开关打开时自动弹一次；用户看过就不再烦。 */
    private fun maybeShowOemGuide(auto: Boolean) {
        val oem = OemHelper.current()
        if (!OemHelper.needsGuide(oem)) return
        if (auto && OemHelper.hasShownGuide(this)) return
        OemHelper.markGuideShown(this)
        showOemGuide()
    }

    private fun showOemGuide() {
        val oem = OemHelper.current()
        val title = if (OemHelper.needsGuide(oem)) {
            "让闹钟一定能响（${oem.label}）"
        } else {
            "后台运行设置（${oem.label}）"
        }
        val dlg = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(OemHelper.guideText(oem))
            .setPositiveButton("去设置") { _, _ ->
                if (!OemHelper.tryOpenSettings(this, oem)) {
                    Dialogs.info(this, "打不开设置页", "请手动按上面的路径操作。")
                }
            }
            .setNegativeButton("知道了", null)
            .create()
        if (!OemHelper.isIgnoringBatteryOptimizations(this)) {
            dlg.setButton(AlertDialog.BUTTON_NEUTRAL, "关闭电池优化") { _, _ ->
                if (!OemHelper.requestIgnoreBatteryOptimizations(this)) {
                    Dialogs.info(this, "无法自动跳转", "请到 设置 → 电池 → 电池优化 里把「校园枢纽」设为「不优化」。")
                }
            }
        }
        dlg.show()
    }

    private fun updateReminderHint() {
        val enabled = ReminderStore.isEnabled(this)
        val courses = ReminderStore.cachedCourses(this)
        val planned = ReminderStore.planned(this)
        val oem = OemHelper.current()
        val base = when {
            !enabled -> "本地提醒，不需要网络和推送服务"
            courses.isEmpty() -> "已开启 · 今天没有课，明天 7:00 汇总"
            planned.size > 1 -> "已开启 · 今天 ${courses.size} 门课，已注册 ${planned.size} 个提醒"
            else -> "已开启 · 今天 ${courses.size} 门课，仅汇总（课程已开始或已过）"
        }
        val tip = if (OemHelper.needsGuide(oem)) " · 点击设置后台权限" else ""
        dash.tvReminderHint.text = base + tip
    }

    private fun openCampusApp() {
        val intent = packageManager.getLaunchIntentForPackage(Config.CAMPUS_PKG)
        if (intent == null) {
            Toast.makeText(this, "未安装校园平台 App", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "无法启动校园平台 App", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------ 功能宫格

    private fun onModuleClick(m: CampusModule) {
        when {
            m.id == CampusCatalog.ID_SCHEDULE ->
                binding.bottomNav.selectedItemId = R.id.nav_dashboard

            m.id == CampusCatalog.ID_PROFILE -> {
                if (session == null) showLoginDialog() else Dialogs.profile(this, session) { logout() }
            }

            m.id == CampusCatalog.ID_NEWS -> loadNews()

            m.openable -> openWeb(m.url!!, m.name)

            else -> Dialogs.featureUnavailable(this, m)
        }
    }

    // ------------------------------------------------------------ 校内系统网页

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebShell() {
        val wv = binding.webOverlayView
        val s: WebSettings = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.userAgentString = s.userAgentString + " CampusHub/1.0"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.setBackgroundColor(Color.WHITE)

        @Suppress("DEPRECATION")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressWeb.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressWeb.visibility = View.GONE
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val uri = Uri.parse(url ?: return false)
                if (uri.scheme == "http" || uri.scheme == "https") return false
                return startExternal(uri)
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressWeb.progress = newProgress
                binding.progressWeb.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        binding.btnWebBack.setOnClickListener { webBack() }
        binding.btnWebClose.setOnClickListener { closeWeb() }
    }

    /** 支付宝 / 微信 / 邮件等外部协议交给系统处理。 */
    private fun startExternal(uri: Uri): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun isWebOpen(): Boolean = binding.webOverlay.visibility == View.VISIBLE

    private fun openWeb(url: String, title: String) {
        binding.tvWebTitle.text = title
        binding.webOverlay.visibility = View.VISIBLE
        binding.webOverlayView.loadUrl(url)
    }

    private fun webBack() {
        val wv = binding.webOverlayView
        if (wv.canGoBack()) wv.goBack() else closeWeb()
    }

    private fun closeWeb() {
        binding.webOverlayView.stopLoading()
        binding.progressWeb.visibility = View.GONE
        binding.webOverlay.visibility = View.GONE
    }

    // ------------------------------------------------------------ 校园网

    private fun checkCampusNet() {
        campus.tvNetState.text = "正在检测校园网…"
        Bg.run({ CampusNet.probe() }, { r ->
            if (!alive()) return@run
            campus.tvNetState.text = when (r.getOrDefault(CampusNet.State.OUTSIDE)) {
                CampusNet.State.CAMPUS -> "校园网已连接 · 校内系统直连"
                else -> "当前不在校园网 · 用 WebVPN 访问内网资源"
            }
        })
    }

    private fun loadNews() {
        val s = session
        if (s == null) {
            showLoginDialog()
            return
        }
        Bg.run({ api.newsList(s) }, { r ->
            if (!alive()) return@run
            r.onSuccess { items ->
                Dialogs.news(this, items, if (items.isEmpty()) "服务端未返回数据（该校通知接口需要 yyid 参数）。" else null)
            }.onFailure { t ->
                Dialogs.news(this, emptyList(), "读取失败：${t.message ?: "网络错误"}")
            }
        })
    }

    // ------------------------------------------------------------ 登录

    private fun showLoginDialog() {
        val v = DialogLoginBinding.inflate(layoutInflater)
        v.etUser.setText(store.userId)
        val dlg: AlertDialog = MaterialAlertDialogBuilder(this)
            .setView(v.root)
            .setPositiveButton("登录", null)
            .setNegativeButton("稍后", null)
            .create()

        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val u = v.etUser.text?.toString()?.trim().orEmpty()
                val p = v.etPass.text?.toString().orEmpty()
                if (u.isBlank()) {
                    v.tilUser.error = "请输入学号"; return@setOnClickListener
                }
                if (p.isBlank()) {
                    v.tilPass.error = "请输入密码"; return@setOnClickListener
                }
                v.tilUser.error = null
                v.tilPass.error = null
                doLogin(u, p, dlg)
            }
        }
        dlg.show()
    }

    private fun doLogin(user: String, pass: String, dlg: AlertDialog) {
        val btn = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
        btn.isEnabled = false
        btn.text = "登录中…"
        Bg.run({ api.login(user, pass) }, { r ->
            if (!alive()) return@run
            btn.isEnabled = true
            btn.text = "登录"
            r.onSuccess { s ->
                session = s
                store.userId = user
                store.password = pass
                store.saveSession(s)
                dlg.dismiss()
                refreshAll()
            }.onFailure { t ->
                Toast.makeText(this, "登录失败：" + (t.message ?: "网络错误"), Toast.LENGTH_LONG).show()
            }
        })
    }

    /** 退出登录：连密码和学籍资料一起清掉，并停掉靠课表缓存驱动的提醒。 */
    private fun logout() {
        session = null
        store.clearSession()
        ReminderStore.clearCourses(this)
        ReminderScheduler.reschedule(this)
        renderer.render(Dashboard())
        renderCampus(Dashboard())
        updateReminderHint()
        showLoginDialog()
    }

    // ------------------------------------------------------------ 数据刷新

    /** 后台请求最长可拖到 35s，回填前先确认 Activity 还在，否则弹窗会抛 BadTokenException。 */
    private fun alive(): Boolean = !isFinishing && !isDestroyed

    private fun refreshAll() {
        dash.swipe.isRefreshing = true
        Bg.run({ loadDashboard() }, { r ->
            if (!alive()) return@run
            dash.swipe.isRefreshing = false
            r.onSuccess { d ->
                renderer.render(d)
                renderCampus(d)
                // 缓存今日课表并重排提醒（提醒在 App 未运行时也要能工作）
                d.courses?.let {
                    ReminderStore.cacheCourses(this, it)
                    ReminderScheduler.reschedule(this)
                    updateReminderHint()
                }
            }.onFailure { t ->
                renderer.render(Dashboard(error = t.message ?: "刷新失败"))
                campus.tvCampusStatus.visibility = View.VISIBLE
                campus.tvCampusStatus.text = "刷新失败：" + (t.message ?: "网络错误")
                // 密码已改 / 会话被服务端作废时，给出重新登录入口，而不是让用户对着旧数据发呆
                if (t is LysoftClient.ApiException && LysoftClient.isSessionDead(t.code)) {
                    session = null
                    showLoginDialog()
                }
            }
        })
    }

    private fun loadDashboard(): Dashboard {
        val current = session ?: throw LysoftClient.ApiException("尚未登录")
        return try {
            fetch(current)
        } catch (e: LysoftClient.ApiException) {
            // 只有会话类错误才值得重走握手 + 登录；0001 之类是接口本身不可用，重登治不了，
            // 白跑一轮握手还可能触发服务端的风控计数。
            if (!LysoftClient.isSessionDead(e.code) || !store.canAutoRefresh()) throw e
            val ns = api.login(store.userId, store.password)
            store.saveSession(ns)
            session = ns
            fetch(ns)
        }
    }

    private fun fetch(s: LysoftClient.Session): Dashboard {
        val courses = api.todayCourses(s)
        val counts = try {
            api.tabCounts(s)
        } catch (e: Exception) {
            emptyMap()
        }
        return Dashboard(
            userName = s.displayName,
            className = store.className.ifBlank { null },
            courses = courses,
            unread = counts["tdtzgg"],
            todoCount = counts["tdlcdb"],
            chaoxing = chaoxingSummary
        )
    }

    private fun renderCampus(d: Dashboard) {
        val s = session
        val loggedIn = s != null || store.isLoggedIn()
        campus.tvCampusUser.text = s?.displayName?.takeIf { it.isNotBlank() }
            ?: store.userId.ifBlank { "未登录" }
        campus.tvCampusMeta.text = listOfNotNull(
            store.className.takeIf { it.isNotBlank() },
            s?.professionName?.takeIf { it.isNotBlank() },
            if (loggedIn) "已登录" else null
        ).joinToString(" · ")
        campus.tvCampusSchool.text = s?.schoolName.orEmpty()
        campus.btnCampusLogin.text = if (loggedIn) "个人资料" else "登录"

        val ctx = this
        campus.llCampusCourses.removeAllViews()
        campus.tvCampusStatus.visibility = View.GONE
        val courses: List<Course> = d.courses ?: emptyList()
        if (courses.isEmpty()) {
            campus.llCampusCourses.addView(Rows.hint(ctx, "今天没有课程安排"))
        } else {
            campus.llCampusCourses.addView(Rows.hint(ctx, "今天共 ${courses.size} 门课"))
            courses.forEach { campus.llCampusCourses.addView(Rows.course(ctx, it, detail = true)) }
        }
    }

    // ------------------------------------------------------------ 学习通

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWeb() {
        val wv = cx.webView
        val s: WebSettings = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.cacheMode = WebSettings.LOAD_DEFAULT
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.setGeolocationEnabled(true)
        s.userAgentString = s.userAgentString + " CampusHub/1.0"
        CookieManager.getInstance().setAcceptCookie(true)
        // 扫码登录的二维码 iframe 挂在第三方域，关掉会导致登录页一直转圈，故保留；
        // 相机 / 麦克风 / 定位这类传感器能力改由 Config.isTrustedSensorOrigin 收口。
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.setBackgroundColor(Color.WHITE)

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                cx.progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                cx.progress.visibility = View.GONE
                if (url?.contains("chaoxing") == true) probeChaoxingCourses()
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                cx.progress.progress = newProgress
                cx.progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            /** 网页请求相机 / 麦克风时放行，避免静默失败。 */
            @SuppressLint("WebViewApiAvailability")
            override fun onPermissionRequest(request: PermissionRequest?) {
                request ?: return
                if (!Config.isTrustedSensorOrigin(request.origin?.toString())) {
                    request.deny()
                    return
                }
                val wanted = request.resources.filter {
                    it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                }
                if (wanted.isEmpty()) {
                    request.deny()
                    return
                }
                val missing = wanted.filter {
                    val perm = if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                        Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
                    ContextCompat.checkSelfPermission(this@MainActivity, perm) !=
                        PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) {
                    request.grant(wanted.toTypedArray())
                    return
                }
                // 先申请运行时权限，授权后再回填
                pendingRequest = request to wanted
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    missing.map {
                        if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                            Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO
                    }.toTypedArray(),
                    REQ_WEB_MEDIA
                )
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback ?: return
                if (!Config.isTrustedSensorOrigin(origin)) {
                    callback.invoke(origin, false, false)
                    return
                }
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false)
                    return
                }
                // 系统运行时权限没到手时，网页定位只会静默失败，必须先申请再回填
                pendingGeo = origin to callback
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    REQ_WEB_GEO
                )
            }
        }
        cx.btnCxToggle.setOnClickListener { setCxWebMode(!cxWebMode) }
        cx.btnCxApp.setOnClickListener { openChaoxingApp() }

        // 学习通 Tab 直接进网页版，课程列表退化为第二视图
        setCxWebMode(true)
    }

    /** 跳原版学习通，签到（拍照/定位）必须走这条。 */
    private fun openChaoxingApp() {
        val intent = packageManager.getLaunchIntentForPackage(Config.CHAOXING_PKG)
        if (intent == null) {
            Dialogs.info(
                this, "未安装学习通",
                "拍照签到、定位签到需要原版学习通 App 的原生能力，网页端做不到。\n\n请先安装学习通。"
            )
            return
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Dialogs.info(this, "无法启动", "启动学习通失败：${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val allGranted = grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            REQ_WEB_MEDIA -> {
                val pending = pendingRequest
                pendingRequest = null
                if (pending == null) return
                val (request, wanted) = pending
                if (allGranted) request.grant(wanted.toTypedArray()) else request.deny()
            }

            REQ_WEB_GEO -> {
                val pending = pendingGeo
                pendingGeo = null
                pending?.let { (origin, callback) -> callback.invoke(origin, allGranted, false) }
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 用隐藏 WebView 打开课程列表接口，直接读返回的 JSON 文本。
     * 这是"读你自己已登录会话里本来就能看到的页面"，不涉及复刻超星的加密与风控协议。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun hideProbeWebView() {
        cxProbe = WebView(this)
        cxProbe.settings.javaScriptEnabled = true
        cxProbe.settings.domStorageEnabled = true
        cxProbe.settings.userAgentString = cx.webView.settings.userAgentString
        cxProbe.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript("document.body.innerText") { raw -> onChaoxingJson(raw) }
            }
        }
        cxProbe.layoutParams = android.widget.FrameLayout.LayoutParams(1, 1)
        cxProbe.alpha = 0f
        binding.content.addView(cxProbe)
    }

    private fun probeChaoxingCourses() {
        if (!::cxProbe.isInitialized || probeRunning) return
        if (CookieManager.getInstance().getCookie(Config.CHAOXING_HOME) == null) return
        probeRunning = true
        cxProbe.loadUrl("https://mooc1-api.chaoxing.com/mycourse/backclazzdata?view=json")
    }

    private fun onChaoxingJson(raw: String?) {
        probeRunning = false
        val courses = parseChaoxingCourses(raw)
        cxCourses = courses
        renderChaoxing(courses)
    }

    /** 未登录时超星也会写入 JSESSIONID 等 cookie，只有 _uid 才是真实的登录标记。 */
    private fun isChaoxingLoggedIn(): Boolean {
        val cookie = CookieManager.getInstance().getCookie(Config.CHAOXING_HOME) ?: return false
        return cookie.contains("_uid=") || cookie.contains("UID=")
    }

    private fun renderChaoxing(courses: List<CxCourse>) {
        val loggedIn = courses.isNotEmpty() || isChaoxingLoggedIn()
        cx.tvCxState.text = if (loggedIn) "已登录" else "未登录"
        cx.tvCxSub.text = when {
            courses.isNotEmpty() -> "共 ${courses.size} 门课程"
            loggedIn -> "已登录，但未解析到课程列表，可在网页版里查看"
            else -> "在网页版里登录超星账号后显示课程"
        }

        chaoxingSummary = when {
            courses.isNotEmpty() -> "已登录 · 共 ${courses.size} 门课程"
            loggedIn -> "已登录"
            else -> null
        }
        dash.tvCxSummary.text = chaoxingSummary ?: "在「学习通」页的网页版登录后显示课程"

        val ctx = this
        cx.llCxCourses.removeAllViews()
        if (courses.isEmpty()) {
            cx.llCxCourses.addView(Rows.hint(ctx, if (loggedIn) "未能读取课程列表" else "在网页版登录后这里会显示你的课程"))
        } else {
            courses.forEach { c ->
                cx.llCxCourses.addView(Rows.cxCourse(ctx, c) { openCourse(it) })
            }
        }
    }

    private fun openCourse(c: CxCourse) {
        val url = if (c.courseId.isNotBlank() && c.clazzId.isNotBlank()) {
            "https://mooc1.chaoxing.com/visit/stucoursemiddle?courseid=${c.courseId}&clazzid=${c.clazzId}&vc=1&cpi=0"
        } else {
            Config.CHAOXING_HOME + "/"
        }
        setCxWebMode(true)
        cx.webView.loadUrl(url)
    }

    private fun setCxWebMode(on: Boolean) {
        cxWebMode = on
        cx.scrollNative.visibility = if (on) View.GONE else View.VISIBLE
        cx.webView.visibility = if (on) View.VISIBLE else View.GONE
        cx.btnCxToggle.text = if (on) "查看课程列表" else "打开网页版"
        if (on && cx.webView.url.isNullOrBlank()) {
            cx.webView.loadUrl(Config.CHAOXING_HOME + "/")
        }
    }

    /** 解析 channelList[].content.course.data[].course.data[] 结构，容错处理。 */
    private fun parseChaoxingCourses(raw: String?): List<CxCourse> {
        if (raw.isNullOrBlank() || raw == "null") return emptyList()
        val text = try {
            JSONObject("{\"v\":$raw}").getString("v")
        } catch (e: Exception) {
            raw
        }
        val idx = text.indexOf('{')
        if (idx < 0) return emptyList()

        return try {
            val root = JSONObject(text.substring(idx))
            val channels = root.optJSONArray("channelList") ?: return emptyList()
            val out = LinkedHashMap<String, CxCourse>()
            for (i in 0 until channels.length()) {
                val content = channels.optJSONObject(i)?.optJSONObject("content") ?: continue
                val classArr = content.optJSONObject("course")?.optJSONArray("data") ?: continue
                for (j in 0 until classArr.length()) {
                    val cls = classArr.optJSONObject(j) ?: continue
                    val clazzId = cls.optString("id")
                    val inner = cls.optJSONObject("course")?.optJSONArray("data") ?: JSONArray()
                    for (k in 0 until inner.length()) {
                        val o = inner.optJSONObject(k) ?: continue
                        val name = o.optString("name")
                        if (name.isBlank()) continue
                        val id = o.optString("id")
                        out["$id|$clazzId|$name"] = CxCourse(
                            name = name,
                            courseId = id,
                            clazzId = clazzId,
                            teacher = o.optString("teacherfactor")
                        )
                    }
                }
            }
            out.values.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ------------------------------------------------------------ 今日校园

    private fun setupJrcampus() {
        val installed = isInstalled(JrcDeepLinks.PKG)
        val version = if (installed) appVersion(JrcDeepLinks.PKG) else ""
        jrc.tvJrcState.text = if (installed) "今日校园 已安装" else "今日校园 未安装"
        jrc.tvJrcSub.text = when {
            !installed -> "打卡、请假、签到需要原版 App，请先安装。"
            version.isNotBlank() -> "版本 $version"
            else -> "已安装"
        }

        jrc.btnJrcOpen.setOnClickListener { launchPackage(JrcDeepLinks.PKG, "今日校园") }

        // 深链候选按钮
        val ctx = this
        jrc.llDeepLinks.removeAllViews()
        JrcDeepLinks.probes.forEach { probe ->
            jrc.llDeepLinks.addView(Rows.probeRow(ctx, probe.label, probe.uri) {
                testDeepLink(probe)
            })
        }
    }

    private fun testDeepLink(probe: DeepLinkProbe) {
        if (!isInstalled(JrcDeepLinks.PKG)) {
            Dialogs.info(this, "未安装", "请先安装今日校园。")
            return
        }
        if (probe.uri.startsWith("package:")) {
            launchPackage(JrcDeepLinks.PKG, "今日校园")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(probe.uri)).apply {
                setPackage(JrcDeepLinks.PKG)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Dialogs.info(
                this, "已发起跳转",
                "「${probe.label}」已发出。\n\n如果今日校园被打开了，说明这条深链可用 —— 告诉我编号，我把它固化成快捷入口。\n如果没有反应或报错，换下一条试。"
            )
        } catch (e: ActivityNotFoundException) {
            Dialogs.info(this, "此深链不通", "「${probe.label}」\n${probe.uri}\n\n原因：${e.message}")
        } catch (e: Exception) {
            Dialogs.info(this, "跳转失败", "「${probe.label}」\n${e.message}")
        }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun appVersion(pkg: String): String = try {
        val info = packageManager.getPackageInfo(pkg, 0)
        info.versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun launchPackage(pkg: String, label: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Dialogs.info(
                this, "未安装 $label",
                "$label 的打卡、签到依赖原生 SDK（定位 / 相机 / 扫码），网页端无法替代。\n\n请先安装 $label。"
            )
            return
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Dialogs.info(this, "无法启动", "启动 $label 失败：${e.message}")
        }
    }

    // ------------------------------------------------------------ 导航

    private fun show(itemId: Int) {
        if (isWebOpen()) closeWeb()
        dash.root.visibility = if (itemId == R.id.nav_dashboard) View.VISIBLE else View.GONE
        campus.root.visibility = if (itemId == R.id.nav_campus) View.VISIBLE else View.GONE
        cx.root.visibility = if (itemId == R.id.nav_chaoxing) View.VISIBLE else View.GONE
        jrc.root.visibility = if (itemId == R.id.nav_jrcampus) View.VISIBLE else View.GONE
        if (itemId == R.id.nav_chaoxing) setCxWebMode(true)
    }

    override fun onBackPressed() {
        when {
            isWebOpen() -> webBack()
            cx.root.visibility == View.VISIBLE && cxWebMode && cx.webView.canGoBack() ->
                cx.webView.goBack()
            cx.root.visibility == View.VISIBLE -> binding.bottomNav.selectedItemId = R.id.nav_dashboard
            campus.root.visibility == View.VISIBLE -> binding.bottomNav.selectedItemId = R.id.nav_dashboard
            jrc.root.visibility == View.VISIBLE -> binding.bottomNav.selectedItemId = R.id.nav_dashboard
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::cxProbe.isInitialized) {
            binding.content.removeView(cxProbe)
            cxProbe.destroy()
        }
        if (::cx.isInitialized) {
            cx.webView.destroy()
        }
        if (::binding.isInitialized) {
            binding.webOverlay.removeView(binding.webOverlayView)
            binding.webOverlayView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val REQ_WEB_MEDIA = 2001
        private const val REQ_WEB_GEO = 2002
    }
}
