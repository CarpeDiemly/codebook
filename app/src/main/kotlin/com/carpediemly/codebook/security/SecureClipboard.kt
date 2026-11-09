package com.carpediemly.codebook.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 安全剪贴板：
 * 1. 标记为敏感内容（API 33+ 系统不会展示预览）；
 * 2. 复制后 [clearAfterMillis] 毫秒自动清空——但只清空"仍然是我们写进去的那份"，
 *    不会误删用户后来复制的东西；
 * 3. 锁定时立即清空。
 */
class SecureClipboard(
    context: Context,
    private val clearAfterMillis: Long = DEFAULT_CLEAR_AFTER_MS,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null
    private var ownedValue: String? = null

    fun copy(label: String, value: String) {
        val manager = appContext.getSystemService(ClipboardManager::class.java) ?: return
        val clip = ClipData.newPlainText(label, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        manager.setPrimaryClip(clip)
        ownedValue = value

        clearJob?.cancel()
        clearJob = scope.launch {
            delay(clearAfterMillis)
            clearIfStillOwned(manager, value)
        }
    }

    /** 锁库或退出时调用：如果剪贴板里还是我们放的敏感内容，立即清掉。 */
    fun clearIfOwned() {
        val value = ownedValue ?: return
        val manager = appContext.getSystemService(ClipboardManager::class.java) ?: return
        clearJob?.cancel()
        clearIfStillOwned(manager, value)
    }

    private fun clearIfStillOwned(manager: ClipboardManager, value: String) {
        val current = runCatching {
            manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        }.getOrNull()
        if (current == value) {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        if (ownedValue == value) ownedValue = null
    }

    /** 进程销毁前调用：清空剪贴板并结束后台清理任务。 */
    fun dispose() {
        clearIfOwned()
        scope.cancel()
    }

    companion object {
        const val DEFAULT_CLEAR_AFTER_MS = 30_000L
    }
}
