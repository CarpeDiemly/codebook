package com.carpediemly.codebook.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.carpediemly.codebook.R
import com.carpediemly.codebook.data.Entry
import com.carpediemly.codebook.data.PasswordType
import com.carpediemly.codebook.security.PasswordGenerator
import com.carpediemly.codebook.ui.components.passwordTypeLabel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 新增 / 编辑条目。
 *
 * 密码字段默认打码，可临时显示；可以一键生成强密码。
 * 有未保存修改时返回会二次确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    initial: Entry?,
    onCancel: () -> Unit,
    onSave: suspend (Entry) -> Result<Unit>,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var passwordType by remember { mutableStateOf(initial?.passwordType ?: PasswordType.LOGIN) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    var revealed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var showTypePicker by remember { mutableStateOf(false) }
    var showGenerator by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val dirty = remember(name, username, password, passwordType, notes) {
        if (initial == null) {
            name.isNotBlank() || username.isNotBlank() || password.isNotBlank() ||
                notes.isNotBlank() || passwordType != PasswordType.LOGIN
        } else {
            name != initial.name || username != initial.username ||
                password != initial.password || passwordType != initial.passwordType ||
                notes != initial.notes
        }
    }

    fun attemptClose() {
        if (saving) return
        if (dirty) showDiscard = true else onCancel()
    }

    fun save() {
        if (saving) return
        if (name.isBlank()) {
            errorRes = R.string.entry_error_name
            return
        }
        errorRes = null
        scope.launch {
            saving = true
            val base = initial ?: Entry()
            val entry = base.copy(
                name = name,
                username = username,
                password = password,
                passwordType = passwordType,
                notes = notes,
            )
            val result = onSave(entry)
            saving = false
            if (result.isFailure) errorRes = R.string.setup_error_generic
        }
    }

    BackHandler { attemptClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (initial == null) R.string.entry_new else R.string.entry_edit),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { attemptClose() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = !saving) {
                        Text(stringResource(R.string.entry_save))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorRes = null
                },
                label = { Text(stringResource(R.string.label_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.label_username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.label_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showTypePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = passwordTypeLabel(passwordType),
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾")
                }
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.label_password)) },
                singleLine = true,
                visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { revealed = !revealed }) {
                    Text(stringResource(if (revealed) R.string.common_hide else R.string.common_show))
                }
                TextButton(onClick = { showGenerator = true }) {
                    Text(stringResource(R.string.action_generate))
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.label_notes)) },
                placeholder = { Text(stringResource(R.string.entry_notes_hint)) },
                minLines = 3,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxWidth(),
            )

            errorRes?.let { res ->
                Text(
                    text = stringResource(res),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showTypePicker) {
        TypePickerDialog(
            current = passwordType,
            onDismiss = { showTypePicker = false },
            onConfirm = {
                passwordType = it
                showTypePicker = false
            },
        )
    }

    if (showGenerator) {
        PasswordGeneratorDialog(
            onDismiss = { showGenerator = false },
            onGenerate = {
                password = it
                revealed = true
                showGenerator = false
            },
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.entry_unsaved_title)) },
            text = { Text(stringResource(R.string.entry_unsaved_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        onCancel()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            },
        )
    }
}

@Composable
private fun TypePickerDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val startCustom = PasswordType.isCustom(current)
    var selected by remember { mutableStateOf(if (startCustom) PasswordType.CUSTOM else current) }
    var customText by remember { mutableStateOf(PasswordType.customText(current)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.type_pick_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                PasswordType.presets.forEach { key ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = key }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == key, onClick = { selected = key })
                        Text(passwordTypeLabel(key))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selected = PasswordType.CUSTOM }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selected == PasswordType.CUSTOM,
                        onClick = { selected = PasswordType.CUSTOM },
                    )
                    Text(stringResource(R.string.type_custom))
                }
                if (selected == PasswordType.CUSTOM) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it },
                        label = { Text(stringResource(R.string.type_custom_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        if (selected == PasswordType.CUSTOM) {
                            PasswordType.custom(customText)
                        } else {
                            selected
                        },
                    )
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

@Composable
private fun PasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit,
) {
    var length by remember { mutableStateOf(PasswordGenerator.DEFAULT_LENGTH) }
    var useSymbols by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.generator_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.generator_length, length))
                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.roundToInt() },
                    valueRange = 12f..32f,
                    steps = 19,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useSymbols = !useSymbols },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = useSymbols, onCheckedChange = { useSymbols = it })
                    Text(stringResource(R.string.generator_symbols))
                }
                Text(
                    text = stringResource(R.string.generator_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGenerate(PasswordGenerator.generate(length, useSymbols)) }) {
                Text(stringResource(R.string.action_generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
