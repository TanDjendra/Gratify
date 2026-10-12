package com.tan.data.repository

import DatabaseDao
import com.tan.data.db.datasource.LocalDataSource
import com.tan.domain.data.entities.GoogleAccountEntity
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.manager.DataStoreManager
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlin.test.*

class PlaylistOwnerFlowTest {
    @Test fun unrelatedPreferencesDoNotResubscribeButAccountChangesDo(): Unit = runBlocking {
        val email = MutableSharedFlow<String?>(replay = 1)
        email.emit("A")
        val google = MutableStateFlow<GoogleAccountEntity?>(null)
        val subscriptions = mutableListOf<String>()
        val manager = Proxy.newProxyInstance(DataStoreManager::class.java.classLoader,
            arrayOf(DataStoreManager::class.java)) { _, method, _ ->
            when (method.name) { "getString" -> email; else -> error(method.name) }
        } as DataStoreManager
        val dao = Proxy.newProxyInstance(DatabaseDao::class.java.classLoader,
            arrayOf(DatabaseDao::class.java)) { _, method, args ->
            when (method.name) {
                "getUsedGoogleAccountFlow" -> google
                "getAllLocalPlaylistsFlow" -> flow<List<LocalPlaylistEntity>> {
                    val owner = args!![0] as String
                    subscriptions += owner
                    emit(listOf(LocalPlaylistEntity(id = 1, title = owner, ownerEmail = owner)))
                    awaitCancellation()
                }
                else -> error(method.name)
            }
        } as DatabaseDao
        val output = Channel<List<LocalPlaylistEntity>>(Channel.UNLIMITED)
        val job = launch { LocalDataSource(dao, manager).getAllLocalPlaylistsFlow().collect { output.send(it) } }
        try {
            withTimeout(5_000) {
                assertEquals("A", output.receive().single().ownerEmail)
                repeat(20) { email.emit("A") } // getString maps every preferences update to the same account.
                email.emit("B")
                while (output.receive().single().ownerEmail != "B") { }
                assertEquals(listOf("A", "B"), subscriptions)
            }
        } finally { job.cancelAndJoin(); output.close() }
    }
}
