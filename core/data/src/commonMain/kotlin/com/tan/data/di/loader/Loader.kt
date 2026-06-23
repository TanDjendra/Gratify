package com.tan.data.di.loader

import com.tan.data.di.databaseModule
import com.tan.data.di.mediaHandlerModule
import com.tan.data.di.repositoryModule
import org.koin.core.context.loadKoinModules

fun loadAllModules() {
    loadKoinModules(
        listOf(
            databaseModule,
            repositoryModule,
        ),
    )
    loadKoinModules(mediaHandlerModule)
    loadMediaService()
}

expect fun loadMediaService()