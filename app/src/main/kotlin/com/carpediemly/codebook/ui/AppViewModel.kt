package com.carpediemly.codebook.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.carpediemly.codebook.data.Entry
import com.carpediemly.codebook.data.FileVaultStorage
import com.carpediemly.codebook.data.UnlockResult
import com.carpediemly.codebook.data.VaultRepository
import com.carpediemly.codebook.data.VaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 唯一的状态持有者：保险库状态 + 数据操作。
 *
 * 明文条目只存在于 [VaultState.Unlocked] 里；一旦调用 [lockVault]，
 * 内存中的 DEK 被清零、条目对象失去引用。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository(FileVaultStorage.forApp(application))

    private val _vaultState = MutableStateFlow(repository.state())
    val vaultState: StateFlow<VaultState> = _vaultState.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 首次创建保险库。密码在调用返回后由调用方清零。 */
    suspend fun createVault(password: CharArray): Result<Unit> = withContext(Dispatchers.Default) {
        _busy.value = true
        try {
            val result = repository.create(password)
            _vaultState.value = repository.state()
            result
        } finally {
            _busy.value = false
        }
    }

    /** 解锁。 */
    suspend fun unlock(password: CharArray): UnlockResult = withContext(Dispatchers.Default) {
        _busy.value = true
        try {
            val result = repository.unlock(password)
            _vaultState.value = repository.state()
            result
        } finally {
            _busy.value = false
        }
    }

    /** 保存全部条目。 */
    suspend fun saveEntries(entries: List<Entry>): Result<List<Entry>> = withContext(Dispatchers.Default) {
        _busy.value = true
        try {
            val result = repository.saveEntries(entries)
            _vaultState.value = repository.state()
            result
        } finally {
            _busy.value = false
        }
    }

    /** 立即锁定并清空内存明文。 */
    fun lockVault() {
        repository.lock()
        _vaultState.value = repository.state()
    }

    override fun onCleared() {
        repository.lock()
        super.onCleared()
    }

    /** 进程被系统回收前尽力锁定。 */
    fun lockOnBackground() {
        if (repository.isUnlocked()) {
            viewModelScope.launch { lockVault() }
        }
    }
}
