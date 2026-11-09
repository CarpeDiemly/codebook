package com.carpediemly.codebook.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carpediemly.codebook.data.Entry
import com.carpediemly.codebook.data.FileVaultStorage
import com.carpediemly.codebook.data.UnlockResult
import com.carpediemly.codebook.data.VaultRepository
import com.carpediemly.codebook.data.VaultState
import com.carpediemly.codebook.security.SecureClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 唯一的状态持有者：保险库状态 + 数据操作 + 剪贴板 + 自动锁定计时。
 *
 * 明文条目只存在于 [VaultState.Unlocked] 里；一旦锁定，内存中的 DEK 被清零、
 * 条目对象失去引用，剪贴板里的敏感内容也会被清掉。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository(FileVaultStorage.forApp(application))
    private val clipboard = SecureClipboard(application)

    private val _vaultState = MutableStateFlow(repository.state())
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private var backgroundedAt: Long? = null

    suspend fun createVault(password: CharArray): Result<Unit> = withContext(Dispatchers.Default) {
        _busy.value = true
        try {
            repository.create(password).also { refreshState() }
        } finally {
            _busy.value = false
        }
    }

    suspend fun unlock(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        _busy.value = true
        try {
            repository.unlock(password).also { refreshState() }
        } finally {
            _busy.value = false
        }
    }

    suspend fun saveEntries(entries: List<Entry>): Result<List<Entry>> = withContext(Dispatchers.Default) {
        _busy.value = true
        try {
            repository.saveEntries(entries).also { refreshState() }
        } finally {
            _busy.value = false
        }
    }

    /** 新增或更新一条记录；按 id 判断。 */
    suspend fun upsertEntry(entry: Entry): Result<List<Entry>> {
        val current = currentEntries() ?: return Result.failure(IllegalStateException("保险库已锁定"))
        val exists = current.any { it.id == entry.id }
        val next = if (exists) {
            current.map { existing -> if (existing.id == entry.id) entry.touch() else existing }
        } else {
            current + entry
        }
        return saveEntries(next)
    }

    suspend fun deleteEntry(id: String): Result<List<Entry>> {
        val current = currentEntries() ?: return Result.failure(IllegalStateException("保险库已锁定"))
        return saveEntries(current.filterNot { it.id == id })
    }

    fun copyToClipboard(label: String, value: String) {
        clipboard.copy(label, value)
    }

    fun lockVault() {
        clipboard.clearIfOwned()
        repository.lock()
        refreshState()
    }

    /** 应用进入后台时记录时间。 */
    fun onAppBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    /** 回到前台：离开超过 [AUTO_LOCK_MILLIS] 就锁定。 */
    fun onAppForegrounded() {
        val start = backgroundedAt ?: return
        backgroundedAt = null
        if (repository.isUnlocked() && SystemClock.elapsedRealtime() - start >= AUTO_LOCK_MILLIS) {
            viewModelScope.launch { lockVault() }
        }
    }

    override fun onCleared() {
        clipboard.dispose()
        repository.lock()
    }

    private fun currentEntries(): List<Entry>? =
        (repository.state() as? VaultState.Unlocked)?.entries

    private fun refreshState() {
        _vaultState.value = repository.state()
    }

    companion object {
        /** 切到后台超过该时长即自动锁定。 */
        const val AUTO_LOCK_MILLIS = 60_000L
    }
}
