package com.carpediemly.codebook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryTest {

    @Test
    fun `清洗会去空格并按上限截断`() {
        val dirty = Entry(
            name = "  GitHub  ",
            username = " alice ",
            password = "x".repeat(600),
            notes = "y".repeat(6000),
        )
        val clean = EntryRules.sanitize(dirty)
        assertEquals("GitHub", clean.name)
        assertEquals("alice", clean.username)
        assertEquals(EntryRules.MAX_PASSWORD, clean.password.length)
        assertEquals(EntryRules.MAX_NOTES, clean.notes.length)
    }

    @Test
    fun `名称为空的条目校验失败`() {
        assertEquals(EntryValidationError.EMPTY_NAME, EntryRules.validate(Entry(name = "   ")))
        assertEquals(null, EntryRules.validate(Entry(name = "微信")))
    }

    @Test
    fun `未知密码类型归入自定义`() {
        assertEquals("custom:邮箱密码", PasswordType.normalize("邮箱密码"))
        assertEquals(PasswordType.LOGIN, PasswordType.normalize(""))
        assertEquals(PasswordType.PAYMENT, PasswordType.normalize("payment"))
        assertTrue(PasswordType.isCustom(PasswordType.normalize("邮箱密码")))
        assertEquals("邮箱密码", PasswordType.customText(PasswordType.normalize("邮箱密码")))
        assertFalse(PasswordType.isCustom(PasswordType.PIN))
    }

    @Test
    fun `touch 会刷新更新时间`() {
        val entry = Entry(name = "a", createdAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z")
        val touched = entry.touch()
        assertTrue(touched.updatedInstant.isAfter(entry.updatedInstant))
        assertEquals(entry.createdAt, touched.createdAt)
    }
}
