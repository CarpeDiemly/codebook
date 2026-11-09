package com.carpediemly.codebook.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 保险库文件的读写抽象。抽出接口是为了让核心逻辑能在 JVM 单元测试里
 * 用临时目录跑，不需要安卓设备。
 */
interface VaultStorage {
    fun exists(): Boolean
    fun read(): ByteArray?

    /** 原子写入：要么写成，要么保持旧文件不变。 */
    fun write(bytes: ByteArray)
}

/**
 * 应用私有目录中的单文件存储：
 *
 * - 位置：`files/vault.cb`（不申请任何存储权限，其他应用读不到）
 * - 写入：先写 `.tmp` 并 fsync，再原子替换；替换前把旧文件复制成 `.bak` 兜底
 * - 明文永不落盘：写进来的字节本身已经是密文信封
 */
class FileVaultStorage(private val file: File) : VaultStorage {

    override fun exists(): Boolean = file.exists()

    override fun read(): ByteArray? = if (file.exists()) file.readBytes() else null

    override fun write(bytes: ByteArray) {
        val dir = file.parentFile ?: error("保险库路径缺少父目录")
        if (!dir.exists()) dir.mkdirs()

        val tmp = File(dir, "${file.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            if (file.exists()) {
                val backup = File(dir, "${file.name}.bak")
                if (backup.exists()) backup.delete()
                file.copyTo(backup, overwrite = true)
            }
            moveAtomically(tmp, file)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun moveAtomically(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val VAULT_FILE_NAME = "vault.cb"

        fun forApp(context: Context): FileVaultStorage =
            FileVaultStorage(File(context.filesDir, VAULT_FILE_NAME))
    }
}
