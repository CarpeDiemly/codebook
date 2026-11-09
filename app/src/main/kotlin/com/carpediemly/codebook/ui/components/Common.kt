package com.carpediemly.codebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carpediemly.codebook.R
import com.carpediemly.codebook.data.PasswordType

/** 密码强度条：4 段，颜色随分数变化。 */
@Composable
fun PasswordStrengthBar(score: Int, modifier: Modifier = Modifier) {
    val safeScore = score.coerceIn(0, 4)
    val activeColor = when (safeScore) {
        0 -> MaterialTheme.colorScheme.error
        1 -> Color(0xFFE8871E)
        2 -> Color(0xFFC9A227)
        3 -> Color(0xFF3F9D52)
        else -> Color(0xFF1B5E20)
    }
    if (safeScore == 0 && score == 0) {
        // 空密码不显示强度条
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (index < safeScore.coerceAtLeast(1)) {
                            activeColor
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

/** 强度文案，供设置/编辑页复用。 */
@Composable
fun strengthText(score: Int): String = stringResource(
    when (score.coerceIn(0, 4)) {
        0 -> R.string.setup_strength_0
        1 -> R.string.setup_strength_1
        2 -> R.string.setup_strength_2
        3 -> R.string.setup_strength_3
        else -> R.string.setup_strength_4
    },
)

/** 密码类型的本地化名称（数据里存的是稳定 key）。 */
@Composable
fun passwordTypeLabel(type: String): String = when (type) {
    PasswordType.LOGIN -> stringResource(R.string.type_login)
    PasswordType.PAYMENT -> stringResource(R.string.type_payment)
    PasswordType.BANK_CARD -> stringResource(R.string.type_bankcard)
    PasswordType.PIN -> stringResource(R.string.type_pin)
    PasswordType.PATTERN -> stringResource(R.string.type_pattern)
    PasswordType.SECURITY_QUESTION -> stringResource(R.string.type_security)
    PasswordType.APP_LOCK -> stringResource(R.string.type_applock)
    else -> {
        val custom = PasswordType.customText(type)
        if (custom.isBlank()) stringResource(R.string.type_custom) else custom
    }
}

/** 统一的行内错误提示。 */
@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}
