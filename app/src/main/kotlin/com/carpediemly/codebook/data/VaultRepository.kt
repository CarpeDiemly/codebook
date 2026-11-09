package com.carpediemly.codebook.data

import com.carpediemly.codebook.crypto.AeadSpec
import com.carpediemly.codebook.crypto.Crypto
import com.carpediemly.codebook.crypto.DEFAULT_ITERATIONS
import com.carpediemly.codebook.crypto.KDF_ALGO
import com.carpediemly.codebook.crypto.CIPHER_ALGO
import com.carpediemly.codebook.crypto.KdfSpec
import com.carpediemly.codebook.crypto.MIN_ITERATIONS
import com.carpediemly.codebook.crypto.UnsupportedVaultFormatException
import com.carpediemly.codebook.crypto.VaultEnvelope
import com.carpediemly.codebook.crypto.VaultFormat
import com.carpediemly.codebook.security.PasswordPolicy
import java.time.Instant
import java.util.UUID
import javax.crypto.AEADBadTagException

/** 保险库对外状态。 */
sealed interface VaultState {
    /** 还没有创建保险库（首次启动）。 */
    data object Empty : VaultState

    /** 保险库存在但已锁定（进程里没有任何明文）。 */
    data object Locked : VaultState

    /** 已解锁：条目仅存在于内存。 */
    data class Unlocked(val entries: List<Entry>) : VaultState
}

sealed interface UnlockResult {
    data class Success(val entries: List<Entry>) : UnlockResult
    data object WrongPassword : UnlockResult
    data object Corrupt : UnlockResult
    data object NotInitialized : UnlockResult
    data object UnsupportedFormat : UnlockResult
}

sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data object WrongPassword : ChangePasswordResult
    data object NotUnlocked : ChangePasswordResult
    data class InvalidNewPassword(val reason: String) : ChangePasswordResult
}

/**
 * 保险库核心：密钥派生、信封加密、文件落盘。
 *
 * 设计要点（详见 docs/SECURITY.md）：
 * 1. 主密码只用于派生 KEK，从不落盘；
 * 2. 真正加密数据的是随机 DEK，DEK 被 KEK 包装后写进文件 —— 改密码时只需重新包装 DEK；
 * 3. 所有写操作都是"整文件重加密 + 原子替换"；
 * 4. 锁定时销毁内存中的 DEK，条目对象交给 GC。
 */
class VaultRepository(
    private val storage: VaultStorage,
    private val defaultIterations: Int = DEFAULT_ITERATIONS,
) {

    private var dek: ByteArray? = null
    private var envelope: VaultEnvelope? = null
    private var payload: VaultPayload? = null

    fun hasVault(): Boolean = storage.exists()

    /** 所有读写都在同一把锁里，避免并发保存互相覆盖。 */
    @Synchronized
    fun state(): VaultState = when {
        payload != null -> VaultState.Unlocked(payload!!.entries)
        storage.exists() -> VaultState.Locked
        else -> VaultState.Empty
    }

    /** 当前是否已解锁。 */
    fun isUnlocked(): Boolean = payload != null

    /** 首次创建保险库。 */
    @Synchronized
    fun create(password: CharArray, now: Instant = Instant.now()): Result<Unit> = runCatching {
        require(!storage.exists()) { "保险库已存在" }
        require(PasswordPolicy.isAcceptableForVault(password)) {
            "主密码至少 ${PasswordPolicy.MIN_LENGTH} 位"
        }

        val newDek = Crypto.randomBytes(Crypto.KEY_BYTES)
        val salt = Crypto.randomBytes(Crypto.SALT_BYTES)
        val newPayload = VaultPayload(
            vaultId = UUID.randomUUID().toString(),
            createdAt = now.toString(),
        )
        try {
            val kek = Crypto.deriveKey(password, salt, defaultIterations)
            try {
                val newEnvelope = buildNewEnvelope(
                    dek = newDek,
                    kek = kek,
                    salt = salt,
                    iterations = defaultIterations,
                    payload = newPayload,
                )
                storage.write(VaultFormat.encode(newEnvelope))
                dek = newDek.copyOf()
                envelope = newEnvelope
                payload = newPayload
            } finally {
                Crypto.wipe(kek)
            }
        } catch (e: Exception) {
            Crypto.wipe(newDek)
            throw e
        }
    }

    /** 用主密码解锁。 */
    @Synchronized
    fun unlock(password: CharArray): UnlockResult {
        if (!storage.exists()) return UnlockResult.NotInitialized

        val parsed = try {
            VaultFormat.decode(storage.read() ?: return UnlockResult.Corrupt)
        } catch (_: UnsupportedVaultFormatException) {
            return UnlockResult.UnsupportedFormat
        }

        if (parsed.kdf.algo != KDF_ALGO || parsed.keyWrap.algo != CIPHER_ALGO || parsed.payload.algo != CIPHER_ALGO) {
            return UnlockResult.UnsupportedFormat
        }
        if (parsed.kdf.iterations < MIN_ITERATIONS) return UnlockResult.Corrupt

        val salt = runCatching { Crypto.decodeBase64(parsed.kdf.salt) }.getOrNull()
            ?: return UnlockResult.Corrupt
        val keyWrapNonce = runCatching { Crypto.decodeBase64(parsed.keyWrap.nonce) }.getOrNull()
            ?: return UnlockResult.Corrupt
        val keyWrapCipher = runCatching { Crypto.decodeBase64(parsed.keyWrap.ciphertext) }.getOrNull()
            ?: return UnlockResult.Corrupt

        val kek = Crypto.deriveKey(password, salt, parsed.kdf.iterations)
        val unwrapped = try {
            Crypto.decrypt(
                key = kek,
                nonce = keyWrapNonce,
                ciphertext = keyWrapCipher,
                aad = VaultFormat.aadKeyWrap(parsed.kdf.iterations, parsed.kdf.salt),
            )
        } catch (_: AEADBadTagException) {
            return UnlockResult.WrongPassword
        } catch (_: Exception) {
            return UnlockResult.Corrupt
        } finally {
            Crypto.wipe(kek)
        }

        val decryptedPayload = try {
            decryptPayload(parsed, unwrapped)
        } catch (_: Exception) {
            Crypto.wipe(unwrapped)
            return UnlockResult.Corrupt
        }

        dek = unwrapped
        envelope = parsed
        payload = decryptedPayload

        // 旧文件的 KDF 参数低于当前默认值时，趁机升级（只重新包装 DEK，不重写全部数据）。
        if (parsed.kdf.iterations < defaultIterations) {
            runCatching { rewrapWithNewSalt(password) }
        }

        return UnlockResult.Success(decryptedPayload.entries)
    }

    /** 保存全部条目（整体重加密）。返回清洗后的条目列表。 */
    @Synchronized
    fun saveEntries(entries: List<Entry>): Result<List<Entry>> = runCatching {
        val currentDek = dek ?: error("保险库已锁定")
        val currentEnvelope = envelope ?: error("保险库状态异常")
        val currentPayload = payload ?: error("保险库状态异常")

        val sanitized = sanitizeAll(entries)
        val newPayload = currentPayload.copy(entries = sanitized)
        val newPayloadSpec = encryptPayload(currentDek, newPayload)
        val newEnvelope = currentEnvelope.copy(payload = newPayloadSpec)

        storage.write(VaultFormat.encode(newEnvelope))
        envelope = newEnvelope
        payload = newPayload
        sanitized
    }

    /** 修改主密码。要求当前已解锁。 */
    @Synchronized
    fun changePassword(oldPassword: CharArray, newPassword: CharArray): ChangePasswordResult {
        if (payload == null) return ChangePasswordResult.NotUnlocked
        if (!PasswordPolicy.isAcceptableForVault(newPassword)) {
            return ChangePasswordResult.InvalidNewPassword(
                "新密码至少 ${PasswordPolicy.MIN_LENGTH} 位",
            )
        }

        val currentEnvelope = envelope ?: return ChangePasswordResult.NotUnlocked
        val currentDek = dek ?: return ChangePasswordResult.NotUnlocked
        val oldSalt = runCatching { Crypto.decodeBase64(currentEnvelope.kdf.salt) }.getOrNull()
            ?: return ChangePasswordResult.NotUnlocked

        // 先验证旧密码：用旧 KEK 解一次 keyWrap。
        val oldKek = Crypto.deriveKey(oldPassword, oldSalt, currentEnvelope.kdf.iterations)
        try {
            Crypto.decrypt(
                key = oldKek,
                nonce = Crypto.decodeBase64(currentEnvelope.keyWrap.nonce),
                ciphertext = Crypto.decodeBase64(currentEnvelope.keyWrap.ciphertext),
                aad = VaultFormat.aadKeyWrap(currentEnvelope.kdf.iterations, currentEnvelope.kdf.salt),
            )
        } catch (_: Exception) {
            return ChangePasswordResult.WrongPassword
        } finally {
            Crypto.wipe(oldKek)
        }

        return runCatching {
            val newSalt = Crypto.randomBytes(Crypto.SALT_BYTES)
            val newKek = Crypto.deriveKey(newPassword, newSalt, defaultIterations)
            try {
                val keyWrap = Crypto.encrypt(
                    key = newKek,
                    plaintext = currentDek,
                    aad = VaultFormat.aadKeyWrap(defaultIterations, Crypto.encodeBase64(newSalt)),
                )
                val newEnvelope = currentEnvelope.copy(
                    kdf = KdfSpec(iterations = defaultIterations, salt = Crypto.encodeBase64(newSalt)),
                    keyWrap = AeadSpec(
                        nonce = Crypto.encodeBase64(keyWrap.nonce),
                        ciphertext = Crypto.encodeBase64(keyWrap.ciphertext),
                        aad = String(VaultFormat.aadKeyWrap(defaultIterations, Crypto.encodeBase64(newSalt))),
                    ),
                )
                storage.write(VaultFormat.encode(newEnvelope))
                envelope = newEnvelope
                ChangePasswordResult.Success
            } finally {
                Crypto.wipe(newKek)
            }
        }.getOrElse { ChangePasswordResult.NotUnlocked }
    }

    /** 锁定：清零 DEK，丢弃明文条目。 */
    @Synchronized
    fun lock() {
        Crypto.wipe(dek)
        dek = null
        envelope = null
        payload = null
    }

    // ---- 内部实现 ----

    private fun buildNewEnvelope(
        dek: ByteArray,
        kek: ByteArray,
        salt: ByteArray,
        iterations: Int,
        payload: VaultPayload,
    ): VaultEnvelope {
        val saltBase64 = Crypto.encodeBase64(salt)
        val keyWrap = Crypto.encrypt(
            key = kek,
            plaintext = dek,
            aad = VaultFormat.aadKeyWrap(iterations, saltBase64),
        )
        return VaultEnvelope(
            kdf = KdfSpec(iterations = iterations, salt = saltBase64),
            keyWrap = AeadSpec(
                nonce = Crypto.encodeBase64(keyWrap.nonce),
                ciphertext = Crypto.encodeBase64(keyWrap.ciphertext),
                aad = String(VaultFormat.aadKeyWrap(iterations, saltBase64), Charsets.UTF_8),
            ),
            payload = encryptPayload(dek, payload),
        )
    }

    private fun encryptPayload(dek: ByteArray, payload: VaultPayload): AeadSpec {
        val plaintext = VaultFormat.json
            .encodeToString(VaultPayload.serializer(), payload)
            .toByteArray(Charsets.UTF_8)
        val result = try {
            Crypto.encrypt(dek, plaintext, VaultFormat.aadPayload())
        } finally {
            plaintext.fill(0)
        }
        return AeadSpec(
            nonce = Crypto.encodeBase64(result.nonce),
            ciphertext = Crypto.encodeBase64(result.ciphertext),
            aad = String(VaultFormat.aadPayload(), Charsets.UTF_8),
        )
    }

    private fun decryptPayload(parsed: VaultEnvelope, dekBytes: ByteArray): VaultPayload {
        val plaintext = Crypto.decrypt(
            key = dekBytes,
            nonce = Crypto.decodeBase64(parsed.payload.nonce),
            ciphertext = Crypto.decodeBase64(parsed.payload.ciphertext),
            aad = VaultFormat.aadPayload(),
        )
        return try {
            VaultFormat.json.decodeFromString(VaultPayload.serializer(), plaintext.toString(Charsets.UTF_8))
        } finally {
            plaintext.fill(0)
        }
    }

    /** 解锁后顺手升级 KDF 参数（换新盐 + 新迭代次数，重新包装同一个 DEK）。 */
    private fun rewrapWithNewSalt(password: CharArray) {
        val currentEnvelope = envelope ?: return
        val currentDek = dek ?: return
        val newSalt = Crypto.randomBytes(Crypto.SALT_BYTES)
        val newKek = Crypto.deriveKey(password, newSalt, defaultIterations)
        try {
            val saltBase64 = Crypto.encodeBase64(newSalt)
            val keyWrap = Crypto.encrypt(
                key = newKek,
                plaintext = currentDek,
                aad = VaultFormat.aadKeyWrap(defaultIterations, saltBase64),
            )
            val newEnvelope = currentEnvelope.copy(
                kdf = KdfSpec(iterations = defaultIterations, salt = saltBase64),
                keyWrap = AeadSpec(
                    nonce = Crypto.encodeBase64(keyWrap.nonce),
                    ciphertext = Crypto.encodeBase64(keyWrap.ciphertext),
                    aad = String(VaultFormat.aadKeyWrap(defaultIterations, saltBase64), Charsets.UTF_8),
                ),
            )
            storage.write(VaultFormat.encode(newEnvelope))
            envelope = newEnvelope
        } catch (_: Exception) {
            // 升级失败不影响本次解锁，下次再试。
        } finally {
            Crypto.wipe(newKek)
        }
    }

    private fun sanitizeAll(entries: List<Entry>): List<Entry> {
        val seen = HashSet<String>(entries.size)
        return entries.map { raw ->
            val cleaned = EntryRules.sanitize(raw)
            if (seen.add(cleaned.id)) cleaned else cleaned.copy(id = UUID.randomUUID().toString())
        }
    }
}
