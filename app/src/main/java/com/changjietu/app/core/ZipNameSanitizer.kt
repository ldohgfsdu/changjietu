package com.changjietu.app.core

/**
 * 用户自定义 ZIP 文件名清洗（纯 JVM）。
 * 防止路径穿越 / 非法字符 / 空名；保证以 .zip 结尾。
 */
object ZipNameSanitizer {

    private const val MAX_LENGTH = 120

    fun sanitize(raw: String?, fallback: String): String {
        val cleaned = raw.orEmpty()
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f\\x7f]"), "_")
            .replace("..", "_")
            .replace(Regex("_+"), "_")
            .trim()
            .trimStart('.')
            .take(MAX_LENGTH)
            .trim()
            .takeIf { it.isNotBlank() }
            ?: fallback
        val base = if (cleaned.endsWith(".zip", ignoreCase = true)) cleaned else "$cleaned.zip"
        return base.take(MAX_LENGTH + 4)
    }
}
