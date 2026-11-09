package com.carpediemly.codebook.crypto

import javax.crypto.AEADBadTagException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CryptoTest {

    @Test
    fun `PBKDF2-HMAC-SHA256 符合标准测试向量`() {
        val derived = Crypto.deriveKey("password".toCharArray(), "salt".toByteArray(), 1)
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            derived.toHex(),
        )
    }

    @Test
    fun `相同输入派生相同密钥，不同盐派生不同密钥`() {
        val a = Crypto.deriveKey("hunter2".toCharArray(), ByteArray(16) { 1 }, 10)
        val b = Crypto.deriveKey("hunter2".toCharArray(), ByteArray(16) { 1 }, 10)
        val c = Crypto.deriveKey("hunter2".toCharArray(), ByteArray(16) { 2 }, 10)
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(c))
    }

    @Test
    fun `AES-GCM 加解密往返`() {
        val key = Crypto.randomBytes(Crypto.KEY_BYTES)
        val plaintext = "账号: alice 密码: S3cr3t!".toByteArray()
        val aad = "test:aad".toByteArray()

        val result = Crypto.encrypt(key, plaintext, aad)
        assertNotEquals(plaintext.toHex(), result.ciphertext.toHex())

        val decrypted = Crypto.decrypt(key, result.nonce, result.ciphertext, aad)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun `密文被篡改时解密失败`() {
        val key = Crypto.randomBytes(Crypto.KEY_BYTES)
        val result = Crypto.encrypt(key, "secret".toByteArray(), "aad".toByteArray())
        result.ciphertext[0] = (result.ciphertext[0].toInt() xor 0x01).toByte()

        try {
            Crypto.decrypt(key, result.nonce, result.ciphertext, "aad".toByteArray())
            throw AssertionError("篡改后的密文不应解密成功")
        } catch (_: AEADBadTagException) {
            // 预期行为
        }
    }

    @Test
    fun `AAD 不匹配时解密失败`() {
        val key = Crypto.randomBytes(Crypto.KEY_BYTES)
        val result = Crypto.encrypt(key, "secret".toByteArray(), "aad-1".toByteArray())

        try {
            Crypto.decrypt(key, result.nonce, result.ciphertext, "aad-2".toByteArray())
            throw AssertionError("AAD 不匹配不应解密成功")
        } catch (_: AEADBadTagException) {
            // 预期行为
        }
    }

    @Test
    fun `密钥错误时解密失败`() {
        val key = Crypto.randomBytes(Crypto.KEY_BYTES)
        val wrong = Crypto.randomBytes(Crypto.KEY_BYTES)
        val result = Crypto.encrypt(key, "secret".toByteArray(), "aad".toByteArray())

        try {
            Crypto.decrypt(wrong, result.nonce, result.ciphertext, "aad".toByteArray())
            throw AssertionError("错误密钥不应解密成功")
        } catch (_: AEADBadTagException) {
            // 预期行为
        }
    }

    @Test
    fun `每次加密使用不同的 nonce`() {
        val key = Crypto.randomBytes(Crypto.KEY_BYTES)
        val a = Crypto.encrypt(key, "x".toByteArray(), "aad".toByteArray())
        val b = Crypto.encrypt(key, "x".toByteArray(), "aad".toByteArray())
        assertFalse(a.nonce.contentEquals(b.nonce))
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
    }

    @Test
    fun `Base64 编解码往返`() {
        val bytes = Crypto.randomBytes(32)
        assertArrayEquals(bytes, Crypto.decodeBase64(Crypto.encodeBase64(bytes)))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
