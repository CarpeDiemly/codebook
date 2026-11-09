package com.carpediemly.codebook.data

/**
 * 密码类型的稳定 key。
 *
 * 界面按当前语言映射显示文字，文件里永远存 key —— 这样中英切换、
 * 导出再导入都不会把用户数据搞乱。
 */
object PasswordType {
    const val LOGIN = "login"
    const val PAYMENT = "payment"
    const val BANK_CARD = "bankcard"
    const val PIN = "pin"
    const val PATTERN = "pattern"
    const val SECURITY_QUESTION = "security"
    const val APP_LOCK = "applock"
    const val CUSTOM = "custom"

    const val CUSTOM_PREFIX = "custom:"
    private const val MAX_CUSTOM_LENGTH = 50

    val presets: List<String> = listOf(
        LOGIN, PAYMENT, BANK_CARD, PIN, PATTERN, SECURITY_QUESTION, APP_LOCK,
    )

    /**
     * 规范化外部输入（导入文件、旧版本数据）：已知 key 原样保留，
     * 未知字符串归入自定义类型，保证数据不丢。
     */
    fun normalize(raw: String): String {
        val value = raw.trim()
        return when {
            value.isEmpty() -> LOGIN
            presets.contains(value) -> value
            value == CUSTOM -> CUSTOM_PREFIX
            value.startsWith(CUSTOM_PREFIX) -> CUSTOM_PREFIX + value.removePrefix(CUSTOM_PREFIX).take(MAX_CUSTOM_LENGTH)
            else -> CUSTOM_PREFIX + value.take(MAX_CUSTOM_LENGTH)
        }
    }

    fun isCustom(stored: String): Boolean = stored.startsWith(CUSTOM_PREFIX)

    /** 自定义类型的说明文字；非自定义类型返回空串。 */
    fun customText(stored: String): String =
        if (isCustom(stored)) stored.removePrefix(CUSTOM_PREFIX) else ""

    fun custom(stored: String, text: String): String {
        val trimmed = text.trim().take(MAX_CUSTOM_LENGTH)
        return if (trimmed.isEmpty()) CUSTOM_PREFIX else CUSTOM_PREFIX + trimmed
    }
}
