package com.tan.gratify.utils

import com.tan.domain.data.model.update.UpdateData
import com.tan.domain.data.model.update.UpdateDelta

fun selectDelta(update: UpdateData, versionCode: Long, sourceSize: Long, sourceHash: String, split: Boolean): UpdateDelta? {
    val targetCode = update.versionCode ?: return null
    val targetSize = update.sizeBytes ?: return null
    if (split || targetCode <= versionCode) return null
    return update.deltas.filter {
        it.format == "gratify-delta-v1" && it.fromVersionCode == versionCode && it.fromSizeBytes == sourceSize &&
            it.fromSha256.equals(sourceHash, true) && it.sizeBytes in 1..(targetSize * 7 / 10)
    }.minByOrNull { it.sizeBytes }
}
