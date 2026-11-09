package com.carpediemly.codebook.data

import com.carpediemly.codebook.crypto.Crypto
import com.carpediemly.codebook.crypto.VaultFormat
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // 测试用较低的迭代次数，保持单测秒级完成；生产默认值是 600_000。
    private val testIterations = 110_000

    private lateinit var vaultFile: File

    private fun newRepository(): VaultRepository {
        vaultFile = File(tempFolder.newFolder(), "vault.cb")
        return VaultRepository(FileVaultStorage(vaultFile), defaultIterations = testIterations)
    }

    @Test
    fun `创建保险库后文件存在且不含明文`() {
        val repo = newRepository()
        val result = repo.create("master-password".toCharArray())
        assertTrue(result.isSuccess)
        assertTrue(vaultFile.exists())

        val raw = vaultFile.readBytes().toString(Charsets.UTF_8)
        assertTrue(raw.contains("codebook.vault"))
        assertFalse("保险库文件不能包含主密码", raw.contains("master-password"))
        assertTrue(repo.isUnlocked())
    }

    @Test
    fun `错误密码无法解锁`() {
        val repo = newRepository()
        repo.create("master-password".toCharArray())
        repo.lock()

        assertEquals(UnlockResult.WrongPassword, repo.unlock("wrong-password".toCharArray()))
    }

    @Test
    fun `正确密码解锁后条目一致`() {
        val repo = newRepository()
        repo.create("master-password".toCharArray())

        val entry = Entry(
            name = "招商银行",
            username = "6225880123456789",
            password = "Bank#2026",
            passwordType = PasswordType.PAYMENT,
            notes = "柜台预留手机号 138****0000",
        )
        val saved = repo.saveEntries(listOf(entry)).getOrThrow()
        assertEquals(1, saved.size)
        repo.lock()

        val result = repo.unlock("master-password".toCharArray())
        assertTrue(result is UnlockResult.Success)
        val entries = (result as UnlockResult.Success).entries
        assertEquals(1, entries.size)
        assertEquals("招商银行", entries[0].name)
        assertEquals("6225880123456789", entries[0].username)
        assertEquals("Bank#2026", entries[0].password)
        assertEquals(PasswordType.PAYMENT, entries[0].passwordType)
    }

    @Test
    fun `保险库文件内容被篡改时提示损坏`() {
        val repo = newRepository()
        repo.create("master-password".toCharArray())
        repo.saveEntries(listOf(Entry(name = "GitHub", username = "alice", password = "p@ss")))
        repo.lock()

        val envelope = VaultFormat.decode(vaultFile.readBytes())
        val cipherBytes = Crypto.decodeBase64(envelope.payload.ciphertext)
        cipherBytes[0] = (cipherBytes[0].toInt() xor 0x01).toByte()
        val tampered = envelope.copy(
            payload = envelope.payload.copy(ciphertext = Crypto.encodeBase64(cipherBytes)),
        )
        vaultFile.writeBytes(VaultFormat.encode(tampered))

        assertEquals(UnlockResult.Corrupt, repo.unlock("master-password".toCharArray()))
    }

    @Test
    fun `调低迭代次数的降级攻击无法解锁`() {
        val repo = newRepository()
        repo.create("master-password".toCharArray())
        repo.lock()

        val envelope = VaultFormat.decode(vaultFile.readBytes())
        val downgraded = envelope.copy(kdf = envelope.kdf.copy(iterations = testIterations - 1000))
        vaultFile.writeBytes(VaultFormat.encode(downgraded))

        // KDF 参数被写进 keyWrap 的 AAD，改动后认证失败
        assertEquals(UnlockResult.WrongPassword, repo.unlock("master-password".toCharArray()))
    }

    @Test
    fun `修改主密码后旧密码失效新密码可用`() {
        val repo = newRepository()
        repo.create("master-password".toCharArray())
        repo.saveEntries(listOf(Entry(name = "微信", username = "alice", password = "wx-2026")))

        val changed = repo.changePassword(
            "master-password".toCharArray(),
            "new-master-password".toCharArray(),
        )
        assertEquals(ChangePasswordResult.Success, changed)
        repo.lock()

        assertEquals(UnlockResult.WrongPassword, repo.unlock("master-password".toCharArray()))

        val reopened = repo.unlock("new-master-password".toCharArray())
        assertTrue(reopened is UnlockResult.Success)
        assertEquals("微信", (reopened as UnlockResult.Success).entries[0].name)
    }

    @Test
    fun `锁定后不能继续写数据`() {
        val repo = newRepository()
        repo.create("master-password".toCharArray())
        repo.lock()

        assertNull(repo.saveEntries(listOf(Entry(name = "x"))).getOrNull())
        assertFalse(repo.isUnlocked())
    }

    @Test
    fun `重复创建保险库会被拒绝`() {
        val repo = newRepository()
        repo.create("master-password".toCharArray())
        val second = repo.create("another-password".toCharArray())
        assertTrue(second.isFailure)
    }

    @Test
    fun `过短的主密码被拒绝`() {
        val repo = newRepository()
        assertTrue(repo.create("123".toCharArray()).isFailure)
        assertFalse(vaultFile.exists())
    }

    @Test
    fun `文件损坏时不会崩溃`() {
        val repo = newRepository()
        repo.create("master-password".toCharArray())
        repo.lock()
        vaultFile.writeBytes("not a vault".toByteArray())

        assertEquals(UnlockResult.UnsupportedFormat, repo.unlock("master-password".toCharArray()))
    }

    @Test
    fun `备份文件保留上一次内容`() {
        val repo = newRepository()
        repo.create("master-password".toCharArray())
        repo.saveEntries(listOf(Entry(name = "条目一")))
        repo.lock()

        val backup = File(vaultFile.parentFile, "${vaultFile.name}.bak")
        assertTrue("写入新版本时应留下备份", backup.exists())
        assertNotNull(VaultFormat.decode(backup.readBytes()))
    }
}
