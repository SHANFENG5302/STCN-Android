package cn.smart.teach

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.activity.result.ActivityResultLauncher


class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var lastBackPressedTime: Long = 0 // 上次点击返回键的时间
    private var uploadMessageAboveL: ValueCallback<Array<Uri>>? = null // 用于处理文件上传的回调
    private val FILE_CHOOSER_RESULT_CODE = 10000 // 文件选择器请求码
    private var isFromDeepLink = false // 标记是否从深度链接启动
    private var shouldClearHistory = false // 标记是否需要清除历史记录

    @SuppressLint("MissingInflatedId", "SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 初始化核心控件（复用变量避免重复 findViewById）
        val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        webView = findViewById(R.id.webView)
        val webSettings = webView.settings

        // WebView 核心配置（合并相关设置）
        with(webSettings) {
            javaScriptEnabled = true // 启用JavaScript
            domStorageEnabled = true // DOM存储支持
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // 混合内容支持
            setSupportZoom(true) // 缩放支持
            builtInZoomControls = true
            displayZoomControls = false // 隐藏缩放按钮
            allowContentAccess = true // 内容访问权限
            allowFileAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT // 缓存策略
            loadsImagesAutomatically = true // 自动加载图片
        }

        // 硬件加速与刷新动画
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false // 停止刷新动画
                // 检查是否需要清除历史记录
                if (shouldClearHistory && url == "https://forum.smart-teach.cn/") {
                    webView.clearHistory() // 首页已加载，清除历史生效
                    shouldClearHistory = false // 重置标记
                }
            }
        }

        // 设置 WebChromeClient 处理文件上传
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                uploadMessageAboveL = filePathCallback
                openFileChooserActivity(fileChooserParams)
                return true
            }
        }

        // 处理链接
        handleDeepLink(intent)

        // 下拉刷新监听
        swipeRefreshLayout.setOnRefreshListener { webView.reload() }
        val density = resources.displayMetrics.density
        val targetHeightDp = 100
        val targetHeightPx = (targetHeightDp * density).toInt()
        swipeRefreshLayout.setProgressViewOffset(false, 0, targetHeightPx)

        // 系统栏内边距适配（复用 swipeRefreshLayout 变量）
        ViewCompat.setOnApplyWindowInsetsListener(swipeRefreshLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        onBackPressedDispatcher.addCallback(this) {
            handleBackPressed()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // 当应用已运行时收到新的链接
        handleDeepLink(intent)
    }

   // 处理链接
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data
        if (Intent.ACTION_VIEW == intent?.action && data != null) {
            val url = data.toString()
            // 在 WebView 中直接加载链接
            webView.loadUrl(url)
            isFromDeepLink = true // 标记是从链接启动的
        } else {
            // 没有链接，加载首页
            webView.loadUrl("https://forum.smart-teach.cn/")
            isFromDeepLink = false
        }
    }

    private fun openFileChooserActivity(fileChooserParams: WebChromeClient.FileChooserParams) {
        val intent = fileChooserParams.createIntent()
        try {
            startActivityForResult(Intent.createChooser(intent, "文件选择"), FILE_CHOOSER_RESULT_CODE)
        } catch (e: ActivityNotFoundException) {
            // 处理没有文件管理器的情况
            Toast.makeText(this, "未找到文件管理器应用", Toast.LENGTH_SHORT).show()
            uploadMessageAboveL?.onReceiveValue(null)
            uploadMessageAboveL = null
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (uploadMessageAboveL == null) return

            if (resultCode == Activity.RESULT_OK && data != null) {
                // 处理文件选择结果
                val results = processFileChooserResult(data)
                uploadMessageAboveL?.onReceiveValue(results)
            } else {
                // 用户取消选择或其他错误
                uploadMessageAboveL?.onReceiveValue(null)
            }
            uploadMessageAboveL = null
        }
    }

    private fun processFileChooserResult(data: Intent): Array<Uri>? {
        return when {
            data.clipData != null -> {
                // 多选文件
                val clipData = data.clipData!!
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            }
            data.data != null -> {
                // 单选文件
                arrayOf(data.data!!)
            }
            else -> null
        }
    }

    // 双击返回与退出逻辑
    // 重置到首页
    private fun resetToHomePage() {
        webView.loadUrl("https://forum.smart-teach.cn/")
        shouldClearHistory = true
        webView.clearHistory()
        isFromDeepLink = false
    }
    // 处理返回键
    private fun handleBackPressed() {
        // 优先 WebView 回退
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        // 若来自链接且当前无历史记录，则返回首页
        when {
            isFromDeepLink -> {
                resetToHomePage() // 返回首页
            }
            else -> {
                // 正常状态，双击退出
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressedTime < 2000) {
                    finish() // 双击退出
                } else {
                    lastBackPressedTime = currentTime
                    Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}