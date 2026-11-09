package com.carpediemly.codebook.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {

    @Test
    fun `空密码熵为零且分数最低`() {
        assertEquals(0.0, PasswordPolicy.entropyBits(""), 0.0001)
        assertEquals(0, PasswordPolicy.score(""))
    }

    @Test
    fun `低于最低长度的密码不被接受`() {
        assertFalse(PasswordPolicy.isAcceptableForVault("1234567".toCharArray()))
        assertTrue(PasswordPolicy.isAcceptableForVault("12345678".toCharArray()))
    }

    @Test
    fun `长随机密码分数更高`() {
        val weak = PasswordPolicy.score("password")
        val strong = PasswordPolicy.score("vT7!qZ2#mR9\$kL4@xW1")
        assertTrue("期望强密码分数更高：weak=$weak strong=$strong", strong > weak)
    }

    @Test
    fun `大量重复字符会被折减`() {
        val repeated = PasswordPolicy.entropyBits("aaaaaaaaaaaa")
        val varied = PasswordPolicy.entropyBits("abcdefghijkl")
        assertTrue("重复字符的熵应更低", repeated < varied)
    }
}
