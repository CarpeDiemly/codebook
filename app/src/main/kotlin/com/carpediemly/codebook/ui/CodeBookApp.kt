package com.carpediemly.codebook.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carpediemly.codebook.data.VaultState
import com.carpediemly.codebook.ui.screens.SetupScreen
import com.carpediemly.codebook.ui.screens.UnlockScreen
import com.carpediemly.codebook.ui.screens.VaultListScreen

/**
 * 应用根：一个状态对应一个页面，没有多余的导航栈可回退到明文界面。
 */
@Composable
fun CodeBookApp(viewModel: AppViewModel = viewModel()) {
    val state by viewModel.vaultState.collectAsStateWithLifecycle()

    when (val current = state) {
        VaultState.Empty -> SetupScreen(onCreate = viewModel::createVault)
        VaultState.Locked -> UnlockScreen(onUnlock = viewModel::unlock)
        is VaultState.Unlocked -> VaultListScreen(
            entries = current.entries,
            onLock = viewModel::lockVault,
        )
    }
}
