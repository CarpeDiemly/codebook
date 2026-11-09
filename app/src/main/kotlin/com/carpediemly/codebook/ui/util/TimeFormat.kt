package com.carpediemly.codebook.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 把 ISO-8601 时间串格式化成手机本地时间，解析失败时原样返回。 */
object TimeFormat {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun format(iso: String): String = runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrDefault(iso)
}
