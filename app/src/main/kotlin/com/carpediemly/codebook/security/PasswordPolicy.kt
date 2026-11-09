package com.carpediemly.codebook.security

import kotlin.math.ln

/**
 * 主密码与条目密码的强度评估。
 *
 * 只做粗粒度的熵估算（字符集大小 × 长度），用于给用户提示，
 * 不作为安全边界：真正的保护来自 PBKDF2 迭代次数与 AEAD。
 */
object PasswordPolicy {

    /** 主密码最低长度：低于此值不允许创建保险库。 */
    const val MIN_LENGTH = 8

    /** 推荐长度。 */
    const val RECOMMENDED_LENGTH = 12

    fun isAcceptableForVault(password: CharArray): Boolean = password.size >= MIN_LENGTH

    /** 估算熵（比特）。 */
    fun entropyBits(password: String): Double {
        if (password.isEmpty()) return 0.0
        var pool = 0
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in '0'..'9' }) pool += 10
        if (password.any { !it.isLetterOrDigit() && !it.isWhitespace() }) pool += 33
        if (pool == 0) pool = 1

        var bits = password.length * (ln(pool.toDouble()) / ln(2.0))
        // 重复字符占比过高时折减，避免 "aaaaaaaaaaaa" 被算成强密码。
        val uniqueRatio = password.toSet().size.toDouble() / password.length
        if (uniqueRatio < 0.5) {
            bits *= 0.6
        }
        return bits
    }

    /** 0=太弱 1=弱 2=一般 3=强 4=很强。 */
    fun score(password: String): Int = when {
        password.length < MIN_LENGTH -> 0
        else -> when {
            entropyBits(password) < 35 -> 1
            entropyBits(password) < 60 -> 2
            entropyBits(password) < 90 -> 3
            else -> 4
        }
    }
}
