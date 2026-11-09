package com.carpediemly.codebook.data

import kotlinx.serialization.Serializable

/**
 * 保险库解密后的内容。schema 是 payload 内部版本，用于字段演进。
 */
@Serializable
data class VaultPayload(
    val schema: Int = 1,
    val vaultId: String,
    val createdAt: String,
    val entries: List<Entry> = emptyList(),
)
