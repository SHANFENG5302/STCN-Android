package cn.smart.teach

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var lastBackPressedTime: Long = 0

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



        // 硬件加速与网页加载
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                swipeRefreshLayout.isRefreshing = false // 停止刷新动画
            }
        }
        webView.loadUrl("https://forum.smart-teach.cn/")

        // 下拉刷新监听
        swipeRefreshLayout.setOnRefreshListener { webView.reload() }

        // 系统栏内边距适配（复用 swipeRefreshLayout 变量）
        ViewCompat.setOnApplyWindowInsetsListener(swipeRefreshLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // 双击返回退出逻辑
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressedTime < 2000) {
                finish()
            } else {
                lastBackPressedTime = currentTime
                Toast.makeText(this, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
        }
    }
}