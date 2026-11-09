package com.carpediemly.codebook

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.carpediemly.codebook.ui.AppViewModel
import com.carpediemly.codebook.ui.CodeBookApp
import com.carpediemly.codebook.ui.theme.CodeBookTheme

class MainActivity : AppCompatActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 阻止截屏、录屏与最近任务缩略图：密码类应用的最低要求。
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        enableEdgeToEdge()
        setContent {
            CodeBookTheme {
                CodeBookApp(appViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        appViewModel.onAppBackgrounded()
    }
}
