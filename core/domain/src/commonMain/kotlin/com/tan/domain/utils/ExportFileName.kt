package com.tan.domain.utils

fun safeExportFileName(value: String): String {
    val cleaned = value.map { if (it.code < 32 || it in "/\\|?*<\":>") '_' else it }
        .joinToString("").trim().trimEnd('.', ' ').take(160).ifBlank { "Gratify_export" }
    val reserved = cleaned.substringBefore('.').uppercase() in
        (setOf("CON", "PRN", "AUX", "NUL") + (1..9).flatMap { listOf("COM$it", "LPT$it") })
    return if (reserved) "_$cleaned" else cleaned
}
