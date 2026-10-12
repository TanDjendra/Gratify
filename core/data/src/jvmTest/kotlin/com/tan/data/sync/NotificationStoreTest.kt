package com.tan.data.sync

import DatabaseDao
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.tan.data.db.Converters
import com.tan.data.db.MusicDatabase
import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.utils.newReleaseIds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import androidx.sqlite.execSQL
import kotlin.test.*

class NotificationStoreTest {
    private suspend fun fixture(block: suspend (DatabaseDao, AccountLibraryStore) -> Unit) {
        val dir = Files.createTempDirectory("gratify-notifications")
        val db = Room.databaseBuilder<MusicDatabase>(name = dir.resolve("test.db").toString())
            .addTypeConverter(Converters()).setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try { val store = AccountLibraryStore(db); store.activate("A", null); block(db.getDatabaseDao(), store) }
        finally { db.close(); dir.toFile().deleteRecursively() }
    }
    private fun announcement(title: String) = NotificationEntity(channelId = "developer", name = title,
        single = listOf(mapOf("title" to "body", "browseId" to "https://gratify.org/")))
    private fun follower(owner: String) = NotificationEntity(channelId = "follower_activity", name = "Nara",
        single = listOf(mapOf("title" to "followed", "browseId" to "Nara-id", "ownerId" to owner)))

    @Test fun observerSeesInsertsAndDeletesWithoutReopeningPage(): Unit = runBlocking {
        fixture { dao, _ ->
            val emitted = mutableListOf<List<NotificationEntity>>()
            val observer = launch { dao.observeNotifications().collect { emitted += it } }
            withTimeout(5000) { while (emitted.isEmpty()) delay(10) }
            dao.insertNotification(announcement("New"))
            withTimeout(5000) { while (emitted.last().isEmpty()) delay(10) }
            dao.deleteNotification(emitted.last().single().id)
            withTimeout(5000) { while (emitted.last().isNotEmpty()) delay(10) }
            observer.cancelAndJoin()
        }
    }
    @Test fun deletedAndPrunedCardsDoNotComeBackFromPolling(): Unit = runBlocking {
        fixture { dao, _ ->
            assertTrue(dao.insertNotificationOnce(announcement("deleted")))
            dao.deleteNotification(dao.getAllNotification().single().id)
            assertFalse(dao.insertNotificationOnce(announcement("deleted")))
            repeat(110) { dao.insertNotificationOnce(announcement("n-$it")) }
            assertEquals(100, dao.getAllNotification().size)
            assertFalse(dao.insertNotificationOnce(announcement("n-0")))
        }
    }
    @Test fun simultaneousSourcesClaimOneEventAndKeepDifferentLinks(): Unit = runBlocking {
        fixture { dao, _ ->
            val claims = coroutineScope { (1..16).map { async(Dispatchers.IO) { dao.insertNotificationOnce(announcement("same")) } }.awaitAll() }
            assertEquals(1, claims.count { it })
            assertTrue(dao.insertNotificationOnce(announcement("same").copy(single = listOf(mapOf("title" to "body", "browseId" to "https://gratify.org/other")))))
            assertEquals(2, dao.getAllNotification().size)
        }
    }
    @Test fun cachedAnnouncementCanAlertOnceAndSeparateLinksHaveTheirOwnDelivery(): Unit = runBlocking {
        fixture { dao, _ ->
            val card = announcement("Polling before push")
            assertTrue(dao.insertNotificationOnce(card))
            assertFalse(dao.insertNotificationOnce(card))
            val claims = coroutineScope { (1..12).map { async(Dispatchers.IO) { dao.claimDeveloperNotificationDelivery(card) } }.awaitAll() }
            assertEquals(1, claims.count { it })
            val another = card.copy(single = listOf(mapOf("title" to "body", "browseId" to "https://gratify.org/other")))
            dao.insertNotificationOnce(another); assertTrue(dao.claimDeveloperNotificationDelivery(another))
            assertFalse(dao.claimDeveloperNotificationDelivery(follower("A")))
        }
    }
    @Test fun deletedAnnouncementCannotProduceALatePushAlert(): Unit = runBlocking {
        fixture { dao, _ ->
            val card = announcement("Already dismissed")
            dao.insertNotificationOnce(card); dao.deleteNotification(dao.getAllNotification().single().id)
            assertFalse(dao.insertNotificationOnce(card)); assertFalse(dao.claimDeveloperNotificationDelivery(card))
            assertFalse(dao.claimDeveloperNotificationDelivery(announcement("Not cached")))
        }
    }
    @Test fun switchedAccountsRejectLateEventsAndRestoreDismissalReceipts(): Unit = runBlocking {
        fixture { dao, store ->
            assertTrue(dao.insertNotificationOnce(follower("A")))
            dao.deleteNotification(dao.getAllNotification().single().id)
            store.activate("B", "A")
            assertFalse(dao.insertNotificationOnce(follower("A")))
            assertTrue(dao.insertNotificationOnce(follower("B")))
            store.activate("A", "B")
            assertTrue(dao.getAllNotification().isEmpty())
            assertFalse(dao.insertNotificationOnce(follower("A")))
        }
    }
    @Test fun legacyCardsAreAdoptedWithoutDuplicateAlert(): Unit = runBlocking {
        fixture { dao, _ ->
            dao.insertNotificationRaw(follower("A").copy(single = listOf(mapOf("title" to "followed", "browseId" to "Nara-id"))))
            assertFalse(dao.insertNotificationOnce(follower("A")))
            assertEquals(1, dao.getAllNotification().size)
            dao.deleteNotification(dao.getAllNotification().single().id)
            assertFalse(dao.insertNotificationOnce(follower("A")))
        }
    }
    @Test fun emptyReleaseBaselineDetectsFirstReleaseButFirstScanIsSilent() {
        assertEquals(emptySet(), newReleaseIds(null, listOf("old")))
        assertEquals(setOf("first"), newReleaseIds(emptyList(), listOf("first")))
        assertEquals(setOf("new"), newReleaseIds(listOf("old", "removed"), listOf("old", "new", "new")))
    }
    @Test fun oldDatabaseMigratesWithoutLosingNotifications(): Unit = runBlocking {
        val directory = Files.createTempDirectory("gratify-notification-migration")
        val path = directory.resolve("old.db").toString()
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val schema = Json.parseToJsonElement(Files.readString(root.resolve("core/data/schemas/com.tan.data.db.MusicDatabase/27.json"))).jsonObject["database"]!!.jsonObject
        BundledSQLiteDriver().open(path).use { connection ->
            schema["entities"]!!.jsonArray.forEach { entity ->
                val table = entity.jsonObject
                connection.execSQL(table["createSql"]!!.jsonPrimitive.content.replace("\u0024{TABLE_NAME}", table["tableName"]!!.jsonPrimitive.content))
                table["indices"]?.jsonArray.orEmpty().forEach { index -> connection.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\u0024{TABLE_NAME}", table["tableName"]!!.jsonPrimitive.content)) }
            }
            schema["setupQueries"]!!.jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL("INSERT INTO notification(id,channelId,name,single,album,time) VALUES(7,'developer','Kept','[]','[]',0)")
            connection.execSQL("PRAGMA user_version=27")
        }
        val db = Room.databaseBuilder<MusicDatabase>(name=path).addTypeConverter(Converters())
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            assertEquals("Kept", db.getDatabaseDao().getAllNotification().single().name)
            assertTrue(db.getDatabaseDao().insertNotificationOnce(announcement("After migration")))
        } finally { db.close(); directory.toFile().deleteRecursively() }
    }
}
