package com.carpediemly.codebook.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `生成长度符合要求`() {
        assertEquals(16, PasswordGenerator.generate(16).length)
        assertEquals(8, PasswordGenerator.generate(1).length) // 下限收敛到 8
        assertEquals(64, PasswordGenerator.generate(200).length) // 上限收敛到 64
    }

    @Test
    fun `包含数字、大小写字母和符号`() {
        repeat(20) {
            val pwd = PasswordGenerator.generate(20, useSymbols = true)
            assertTrue("缺少小写: $pwd", pwd.any { it in 'a'..'z' })
            assertTrue("缺少大写: $pwd", pwd.any { it in 'A'..'Z' })
            assertTrue("缺少数字: $pwd", pwd.any { it in '0'..'9' })
            assertTrue("缺少符号: $pwd", pwd.any { !it.isLetterOrDigit() })
        }
    }

    @Test
    fun `关闭符号时不会出现符号`() {
        repeat(20) {
            val pwd = PasswordGenerator.generate(24, useSymbols = false)
            assertTrue("不应包含符号: $pwd", pwd.all { it.isLetterOrDigit() })
        }
    }

    @Test
    fun `排除易混淆字符`() {
        repeat(50) {
            val pwd = PasswordGenerator.generate(32)
            assertFalse("不应包含 l/I/O/0/1: $pwd", pwd.any { it in "lIO01" })
        }
    }

    @Test
    fun `连续两次生成不相同`() {
        assertNotEquals(PasswordGenerator.generate(24), PasswordGenerator.generate(24))
    }
}
