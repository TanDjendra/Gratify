package com.tan.gratify.utils

import com.tan.gratify.BuildKonfig

object VersionManager {
    private var versionName: String? = null

    fun initialize() {
        if (versionName == null) {
            versionName =
                try {
                    BuildKonfig.versionName
                } catch (_: Exception) {
                    String()
                }
        }
    }

    fun getVersionName(): String = removeDevSuffix(versionName ?: String())

    fun isVersionLower(current: String, target: String): Boolean {
        val currentParts = parseVersion(current) ?: return false
        val targetParts = parseVersion(target) ?: return false
        val maxLength = maxOf(currentParts.size, targetParts.size)
        for (i in 0 until maxLength) {
            val currVal = currentParts.getOrNull(i) ?: 0
            val targetVal = targetParts.getOrNull(i) ?: 0
            if (currVal < targetVal) return true
            if (currVal > targetVal) return false
        }
        return false
    }

    private fun parseVersion(value: String): List<Int>? {
        val numericPart = value.removePrefix("v").substringBefore('-')
        if (numericPart.isEmpty()) return null
        val parts = numericPart.split('.').map { it.toIntOrNull() }
        if (parts.any { it == null }) return null
        return parts.filterNotNull()
    }

    private fun removeDevSuffix(versionName: String): String {
        return if (versionName.endsWith("-dev")) {
            versionName.replace("-dev", "")
        } else {
            versionName
        }
    }
}
