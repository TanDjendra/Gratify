package com.tan.data.repository

import android.content.Context
import org.koin.mp.KoinPlatform.getKoin

internal actual fun updatePackageName(): String = getKoin().get<Context>().packageName
