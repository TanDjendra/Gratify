package com.tan.data.sync

import DatabaseDao
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tan.data.db.Converters
import com.tan.data.db.MusicDatabase
import com.tan.domain.data.entities.SearchHistory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Files
import kotlin.test.*

class SearchHistoryStoreTest {
    private suspend fun fixture(block: suspend (DatabaseDao, AccountLibraryStore) -> Unit) {
        val dir = Files.createTempDirectory("gratify-search")
        val db = Room.databaseBuilder<MusicDatabase>(name = dir.resolve("test.db").toString())
            .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try { val store = AccountLibraryStore(db); store.activate("A", null); block(db.getDatabaseDao(), store) }
        finally { db.close(); dir.toFile().deleteRecursively() }
    }
    @Test fun observerUpdatesAfterInsertIndividualDeleteAndClear(): Unit = runBlocking {
        fixture { dao, _ ->
            val seen = MutableStateFlow<List<SearchHistory>?>(null)
            val job = launch { dao.observeSearchHistory().collect { seen.value = it } }
            suspend fun await(query: List<String>) = withTimeout(3000) { seen.filter { it?.map { row -> row.query } == query }.first() }
            await(emptyList()); dao.insertOwnedSearchHistory("A", SearchHistory("Nara")); await(listOf("Nara"))
            dao.insertOwnedSearchHistory("A", SearchHistory("Ari")); await(listOf("Ari", "Nara"))
            dao.deleteOwnedSearchHistory("A", "Nara"); await(listOf("Ari"))
            dao.deleteOwnedSearchHistory("A", null); await(emptyList()); job.cancelAndJoin()
        }
    }
    @Test fun repeatingAQueryMovesItToTheTopAndCapsHistoryAtThirty(): Unit = runBlocking {
        fixture { dao, _ ->
            repeat(35) { dao.insertOwnedSearchHistory("A", SearchHistory("query-$it")) }
            assertEquals(30, dao.getSearchHistory().size); assertEquals("query-34", dao.getSearchHistory().first().query)
            dao.insertOwnedSearchHistory("A", SearchHistory("query-10"))
            assertEquals("query-10", dao.getSearchHistory().first().query); assertEquals(30, dao.getSearchHistory().size)
        }
    }
    @Test fun wrongOwnerCannotInsertDeleteOrClear(): Unit = runBlocking {
        fixture { dao, store ->
            dao.insertOwnedSearchHistory("A", SearchHistory("A song")); store.activate("B", "A")
            dao.insertOwnedSearchHistory("B", SearchHistory("B song"))
            assertFailsWith<IllegalStateException> { dao.insertOwnedSearchHistory("A", SearchHistory("late")) }
            assertFailsWith<IllegalStateException> { dao.deleteOwnedSearchHistory("A", "B song") }
            assertFailsWith<IllegalStateException> { dao.deleteOwnedSearchHistory("A", null) }
            assertEquals(listOf("B song"), dao.getSearchHistory().map { it.query })
        }
    }
    @Test fun accountSwitchRestoresHistoryAndItsRecencyOrder(): Unit = runBlocking {
        fixture { dao, store ->
            listOf("z", "a", "middle").forEach { dao.insertOwnedSearchHistory("A", SearchHistory(it)) }
            store.activate("B", "A"); assertTrue(dao.getSearchHistory().isEmpty())
            dao.insertOwnedSearchHistory("B", SearchHistory("private B")); store.activate("A", "B")
            assertEquals(listOf("middle", "a", "z"), dao.getSearchHistory().map { it.query })
            store.activate("B", "A"); assertEquals(listOf("private B"), dao.getSearchHistory().map { it.query })
        }
    }
}
