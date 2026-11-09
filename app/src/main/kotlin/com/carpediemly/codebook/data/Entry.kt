package com.carpediemly.codebook.data

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * 一条密码记录。字段名与导出 JSON 完全一致，导入导出不需要额外映射层。
 *
 * 时间使用 ISO-8601 UTC 字符串（如 `2026-11-09T07:30:00Z`），
 * 既能人读，也能被其他语言/工具直接消费。
 */
@Serializable
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    /** 账号名 / 条目名称，例如"招商银行""GitHub"。 */
    val name: String = "",
    /** 账号（登录名、手机号、卡号）。 */
    val username: String = "",
    /** 密码本体。允许为空（有些条目只记账号）。 */
    val password: String = "",
    /** 密码类型，存稳定 key，见 [PasswordType]。 */
    val passwordType: String = PasswordType.LOGIN,
    val createdAt: String = nowIso(),
    val updatedAt: String = nowIso(),
    /** 备注。 */
    val notes: String = "",
) {
    val createdInstant: Instant
        get() = parseInstantOrEpoch(createdAt)

    val updatedInstant: Instant
        get() = parseInstantOrEpoch(updatedAt)

    /** 修改内容时刷新更新时间。 */
    fun touch(now: Instant = Instant.now()): Entry = copy(updatedAt = now.toString())

    companion object {
        fun nowIso(): String = Instant.now().toString()

        private fun parseInstantOrEpoch(value: String): Instant =
            runCatching { Instant.parse(value) }.getOrDefault(Instant.EPOCH)
    }
}

/** 校验结果，界面负责翻译成当前语言。 */
enum class EntryValidationError {
    EMPTY_NAME,
}

object EntryRules {
    const val MAX_NAME = 200
    const val MAX_USERNAME = 200
    const val MAX_PASSWORD = 500
    const val MAX_NOTES = 5000

    /**
     * 清洗外部输入：去首尾空白、按上限截断、规范化密码类型。
     * 导入文件时也走这里，避免脏数据污染保险库。
     */
    fun sanitize(entry: Entry): Entry = entry.copy(
        name = entry.name.trim().take(MAX_NAME),
        username = entry.username.trim().take(MAX_USERNAME),
        password = entry.password.take(MAX_PASSWORD),
        passwordType = PasswordType.normalize(entry.passwordType),
        notes = entry.notes.take(MAX_NOTES),
    )

    fun validate(entry: Entry): EntryValidationError? = when {
        entry.name.isBlank() -> EntryValidationError.EMPTY_NAME
        else -> null
    }
}
