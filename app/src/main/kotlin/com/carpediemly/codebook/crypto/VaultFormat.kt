package com.carpediemly.codebook.crypto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 保险库文件格式标识。改动格式必须同时增加版本号，并写迁移逻辑。 */
const val FORMAT_VAULT = "codebook.vault"
const val VERSION_VAULT = 1

/** 写入文件头的算法标识，用于未来升级（例如换 Argon2id）。 */
const val KDF_ALGO = "PBKDF2-HMAC-SHA256"
const val CIPHER_ALGO = "AES-256-GCM"

/** 默认 KDF 迭代次数（OWASP 2023 对 PBKDF2-HMAC-SHA256 的建议量级）。 */
const val DEFAULT_ITERATIONS = 600_000

/** 允许的最低迭代次数，低于它的文件视为可疑/损坏，拒绝解锁。 */
const val MIN_ITERATIONS = 100_000

@Serializable
data class KdfSpec(
    val algo: String = KDF_ALGO,
    val iterations: Int = DEFAULT_ITERATIONS,
    /** Base64(16 字节随机盐)。 */
    val salt: String,
)

@Serializable
data class AeadSpec(
    val algo: String = CIPHER_ALGO,
    /** Base64(12 字节 nonce)。 */
    val nonce: String,
    /** Base64(密文 + 16 字节认证标签)。 */
    val ciphertext: String,
    /** 参与认证的附加数据，仅用于审计/自查，解密时必须按规则重建而不是信任文件里的值。 */
    val aad: String,
)

/**
 * 保险库文件的 JSON 信封：只包含 KDF 参数、被包装的 DEK 与加密后的 payload，
 * 没有任何明文条目。主密码永不落盘。
 */
@Serializable
data class VaultEnvelope(
    val format: String = FORMAT_VAULT,
    val version: Int = VERSION_VAULT,
    val kdf: KdfSpec,
    val keyWrap: AeadSpec,
    val payload: AeadSpec,
)

object VaultFormat {

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * payload 的 AAD：固定 ASCII 字符串，Kotlin 与浏览器 JS 都能一字不差复现，
     * 防止密文被跨版本/跨用途复用。
     */
    fun aadPayload(): ByteArray = "codebook.vault:1:payload".toByteArray(Charsets.UTF_8)

    /**
     * keyWrap 的 AAD 绑定 KDF 参数，防止攻击者偷偷调低迭代次数再离线爆破。
     */
    fun aadKeyWrap(iterations: Int, saltBase64: String): ByteArray =
        "codebook.vault:1:keywrap:$iterations:$saltBase64".toByteArray(Charsets.UTF_8)

    fun encode(envelope: VaultEnvelope): ByteArray =
        json.encodeToString(VaultEnvelope.serializer(), envelope).toByteArray(Charsets.UTF_8)

    /** 解析信封；格式/版本不支持时抛出 [UnsupportedVaultFormatException]。 */
    fun decode(bytes: ByteArray): VaultEnvelope {
        val envelope = try {
            json.decodeFromString(VaultEnvelope.serializer(), bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw UnsupportedVaultFormatException("保险库文件不是有效的 JSON 信封", e)
        }
        if (envelope.format != FORMAT_VAULT) {
            throw UnsupportedVaultFormatException("未知的保险库格式：${envelope.format}")
        }
        if (envelope.version != VERSION_VAULT) {
            throw UnsupportedVaultFormatException("不支持的保险库版本：${envelope.version}")
        }
        return envelope
    }
}

class UnsupportedVaultFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
