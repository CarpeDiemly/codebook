package com.carpediemly.codebook.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.carpediemly.codebook.R
import com.carpediemly.codebook.security.PasswordPolicy
import com.carpediemly.codebook.ui.components.ErrorText
import com.carpediemly.codebook.ui.components.PasswordStrengthBar
import com.carpediemly.codebook.ui.components.strengthText
import kotlinx.coroutines.launch

/**
 * 首次启动：设置主密码。
 *
 * 密码不放进 rememberSaveable —— 避免 Activity 重建时明文进入系统保存的实例状态。
 */
@Composable
fun SetupScreen(onCreate: suspend (CharArray) -> Result<Unit>) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    val score = remember(password) { PasswordPolicy.score(password) }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.setup_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorRes = null
                },
                label = { Text(stringResource(R.string.setup_master_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                supportingText = {
                    Text(
                        if (password.isEmpty()) {
                            stringResource(R.string.setup_password_rule)
                        } else {
                            stringResource(R.string.setup_strength, strengthText(score))
                        },
                    )
                },
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            )

            if (password.isNotEmpty()) {
                PasswordStrengthBar(score = score, modifier = Modifier.padding(top = 4.dp))
            }

            OutlinedTextField(
                value = confirm,
                onValueChange = {
                    confirm = it
                    errorRes = null
                },
                label = { Text(stringResource(R.string.setup_confirm_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(R.string.setup_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }

            errorRes?.let { ErrorText(text = stringResource(it)) }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    errorRes = null
                    when {
                        password.length < PasswordPolicy.MIN_LENGTH ->
                            errorRes = R.string.setup_error_too_short

                        password != confirm -> errorRes = R.string.setup_error_mismatch

                        else -> scope.launch {
                            submitting = true
                            val chars = password.toCharArray()
                            try {
                                val result = onCreate(chars)
                                if (result.isFailure) errorRes = R.string.setup_error_generic
                            } finally {
                                chars.fill('\u0000')
                                submitting = false
                            }
                        }
                    }
                },
                enabled = !submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.setup_create))
                }
            }
        }
    }
}
