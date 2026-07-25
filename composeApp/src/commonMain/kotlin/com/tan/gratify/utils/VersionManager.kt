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
        val cleanCurrent = current.removePrefix("v").split("-")[0]
        val cleanTarget = target.removePrefix("v").split("-")[0]
        
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val targetParts = cleanTarget.split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(currentParts.size, targetParts.size)
        for (i in 0 until maxLength) {
            val currVal = currentParts.getOrNull(i) ?: 0
            val targetVal = targetParts.getOrNull(i) ?: 0
            if (currVal < targetVal) return true
            if (currVal > targetVal) return false
        }
        return false
    }

    private fun removeDevSuffix(versionName: String): String {
        return if (versionName.endsWith("-dev")) {
            versionName.replace("-dev", "")
        } else {
            versionName
        }
    }
}