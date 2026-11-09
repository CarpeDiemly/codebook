package com.carpediemly.codebook.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 密码学原语封装：只做三件事——密钥派生、AEAD 加解密、Base64。
 * 全部使用 JDK / Android 自带的 JCE 实现，不引入第三方加密库。
 */
object Crypto {

    /** AES-256 密钥长度（字节）。 */
    const val KEY_BYTES = 32

    /** PBKDF2 盐长度（字节）。 */
    const val SALT_BYTES = 16

    /** GCM nonce 长度（字节），96 位是 GCM 推荐值。 */
    const val NONCE_BYTES = 12

    /** GCM 认证标签长度（位）。 */
    private const val TAG_BITS = 128

    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"

    private val secureRandom = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also(secureRandom::nextBytes)

    /**
     * PBKDF2-HMAC-SHA256 派生 32 字节密钥。
     *
     * 注意：这里不限制迭代次数（便于用标准测试向量验证实现），
     * 生产参数的下限由 [VaultFormat.DEFAULT_ITERATIONS] 与仓库层校验保证。
     */
    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations >= 1) { "迭代次数必须为正数：$iterations" }
        require(salt.isNotEmpty()) { "盐不能为空" }
        val spec = PBEKeySpec(password, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** AES-256-GCM 加密。每次调用都应使用新的随机 nonce。 */
    fun encrypt(
        key: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
        nonce: ByteArray = randomBytes(NONCE_BYTES),
    ): AeadResult {
        require(key.size == KEY_BYTES) { "密钥长度必须是 $KEY_BYTES 字节" }
        require(nonce.size == NONCE_BYTES) { "nonce 长度必须是 $NONCE_BYTES 字节" }
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return AeadResult(nonce = nonce, ciphertext = cipher.doFinal(plaintext))
    }

    /**
     * AES-256-GCM 解密。
     *
     * 密码错误、密文被篡改、AAD 不匹配都会抛出 [javax.crypto.AEADBadTagException]，
     * 调用方据此区分"密码错误"与"数据损坏"，绝不能吞掉异常后继续使用明文。
     */
    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "密钥长度必须是 $KEY_BYTES 字节" }
        require(nonce.size == NONCE_BYTES) { "nonce 长度必须是 $NONCE_BYTES 字节" }
        val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }

    fun encodeBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decodeBase64(text: String): ByteArray = Base64.getDecoder().decode(text)

    /** 尽力清零内存中的密钥材料（JVM 无法保证副本被清除，属于纵深防御）。 */
    fun wipe(bytes: ByteArray?) {
        bytes?.fill(0)
    }

    /** 判断异常是否是"认证失败"（密码错误或数据被篡改）。 */
    fun isAuthFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is javax.crypto.AEADBadTagException) return true
            if (current is GeneralSecurityException && current.message?.contains("tag", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }
}

/** AEAD 加密结果。 */
class AeadResult(val nonce: ByteArray, val ciphertext: ByteArray)
