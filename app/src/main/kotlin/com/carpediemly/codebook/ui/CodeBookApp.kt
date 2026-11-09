package com.carpediemly.codebook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carpediemly.codebook.data.Entry
import com.carpediemly.codebook.data.VaultState
import com.carpediemly.codebook.ui.screens.EntryDetailScreen
import com.carpediemly.codebook.ui.screens.EntryEditScreen
import com.carpediemly.codebook.ui.screens.SetupScreen
import com.carpediemly.codebook.ui.screens.UnlockScreen
import com.carpediemly.codebook.ui.screens.VaultListScreen
import kotlinx.coroutines.launch

/** 解锁后的三个页面。不引入导航框架，状态机足够，也不会出现可回退到明文页的历史栈。 */
sealed interface VaultRoute {
    data object List : VaultRoute
    data class Detail(val id: String) : VaultRoute
    data class Edit(val id: String?) : VaultRoute
}

@Composable
fun CodeBookApp(viewModel: AppViewModel) {
    val state by viewModel.vaultState.collectAsStateWithLifecycle()

    when (val current = state) {
        VaultState.Empty -> SetupScreen(onCreate = viewModel::createVault)
        VaultState.Locked -> UnlockScreen(onUnlock = viewModel::unlock)
        is VaultState.Unlocked -> UnlockedRoot(entries = current.entries, viewModel = viewModel)
    }
}

@Composable
private fun UnlockedRoot(entries: List<Entry>, viewModel: AppViewModel) {
    var route by remember { mutableStateOf<VaultRoute>(VaultRoute.List) }
    val scope = rememberCoroutineScope()

    // 条目被删除（或数据变化）时，别停在已不存在的详情页。
    LaunchedEffect(entries) {
        val current = route
        val missing = when (current) {
            is VaultRoute.Detail -> entries.none { it.id == current.id }
            is VaultRoute.Edit -> current.id != null && entries.none { it.id == current.id }
            VaultRoute.List -> false
        }
        if (missing) route = VaultRoute.List
    }

    when (val current = route) {
        VaultRoute.List -> VaultListScreen(
            entries = entries,
            onOpen = { id -> route = VaultRoute.Detail(id) },
            onAdd = { route = VaultRoute.Edit(null) },
            onLock = viewModel::lockVault,
        )

        is VaultRoute.Detail -> {
            val entry = entries.firstOrNull { it.id == current.id }
            if (entry != null) {
                EntryDetailScreen(
                    entry = entry,
                    onBack = { route = VaultRoute.List },
                    onEdit = { route = VaultRoute.Edit(entry.id) },
                    onDelete = {
                        scope.launch {
                            viewModel.deleteEntry(entry.id)
                            route = VaultRoute.List
                        }
                    },
                    onCopy = viewModel::copyToClipboard,
                )
            }
        }

        is VaultRoute.Edit -> {
            val initial = current.id?.let { id -> entries.firstOrNull { it.id == id } }
            EntryEditScreen(
                initial = initial,
                onCancel = {
                    route = if (initial != null) VaultRoute.Detail(initial.id) else VaultRoute.List
                },
                onSave = { entry ->
                    val result = viewModel.upsertEntry(entry)
                    if (result.isSuccess) route = VaultRoute.List
                    result.map { }
                },
            )
        }
    }
}
