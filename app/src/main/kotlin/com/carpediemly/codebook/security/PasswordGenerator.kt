package com.carpediemly.codebook.security

import java.security.SecureRandom
import java.util.Collections

/**
 * 密码生成器。
 *
 * 默认排除易混淆字符（l/1/I、O/0），长度限制 8–64；
 * 生成结果保证包含所选的每一类字符，避免"看起来有符号其实没有"。
 */
object PasswordGenerator {

    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#\$%^&*-_=+?"

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 64
    const val DEFAULT_LENGTH = 16

    fun generate(
        length: Int = DEFAULT_LENGTH,
        useSymbols: Boolean = true,
        random: SecureRandom = SecureRandom(),
    ): String {
        val size = length.coerceIn(MIN_LENGTH, MAX_LENGTH)
        val pools = buildList {
            add(LOWER)
            add(UPPER)
            add(DIGITS)
            if (useSymbols) add(SYMBOLS)
        }
        val all = pools.joinToString("").toCharArray()

        val chars = ArrayList<Char>(size)
        pools.forEach { pool -> chars.add(pool[random.nextInt(pool.length)]) }
        while (chars.size < size) {
            chars.add(all[random.nextInt(all.size)])
        }
        Collections.shuffle(chars, random)
        return chars.joinToString("")
    }
}
