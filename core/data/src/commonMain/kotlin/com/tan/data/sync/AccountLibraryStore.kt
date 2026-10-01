package com.tan.data.sync

import androidx.room.PooledConnection
import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.tan.data.db.MusicDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
internal data class LibraryCell(val type: Int, val value: String? = null)
@Serializable
internal data class LibraryTable(val name: String, val columns: List<String>, val rows: List<List<LibraryCell>>)

/** Archive, clear, restore and ownership change commit together, including on network failure. */
internal class AccountLibraryStore(private val database: MusicDatabase) {
    suspend fun removeAccount(owner: String) {
        database.useWriterConnection { connection -> connection.immediateTransaction {
            val active = usePrepared("SELECT ownerId FROM account_library_state WHERE id=1") { if (it.step()) it.getText(0) else null }
            if (active == owner) {
                execSQL("PRAGMA defer_foreign_keys = ON")
                readTables(this).forEach { execSQL("DELETE FROM ${identifier(it.name)}") }
                execSQL("UPDATE account_library_state SET ownerId='' WHERE id=1")
            }
            for (table in listOf("account_library_snapshot", "library_removal")) usePrepared("DELETE FROM $table WHERE ownerId=?") {
                it.bindText(1, owner); it.step()
            }
        } }
        database.invalidationTracker.refreshAsync()
    }

    /** VACUUM creates a consistent copy without closing the active database or copying a live WAL. */
    suspend fun exportActiveAccount(destination: String) {
        database.useWriterConnection { connection ->
            connection.usePrepared("VACUUM INTO ?") { statement ->
                statement.bindText(1, destination)
                statement.step()
            }
        }
        BundledSQLiteDriver().open(destination).use { copy ->
            copy.execSQL("DELETE FROM account_library_snapshot")
            copy.execSQL("DELETE FROM library_removal WHERE ownerId NOT IN (SELECT ownerId FROM account_library_state WHERE id = 1)")
        }
    }

    suspend fun activate(ownerId: String, legacyOwner: String?) {
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val storedOwner = usePrepared("SELECT ownerId FROM account_library_state WHERE id = 1") {
                    if (it.step()) it.getText(0) else null
                }
                val previous = storedOwner ?: legacyOwner
                if (storedOwner == null) {
                    val seedOwner = previous ?: ownerId
                    if (seedOwner.isNotBlank()) for ((table, id, flag, kind) in listOf(
                        listOf("song", "videoId", "liked", "user_liked_songs"),
                        listOf("artist", "channelId", "followed", "user_followed_artists"),
                        listOf("album", "browseId", "liked", "user_saved_albums"),
                    )) usePrepared("INSERT OR IGNORE INTO library_removal(ownerId,tableName,itemId,revision,enabled) SELECT ?, ?, ${identifier(id)}, 'initial', 1 FROM ${identifier(table)} WHERE ${identifier(flag)}=1") {
                        it.bindText(1, seedOwner); it.bindText(2, kind); it.step()
                    }
                }
                if (previous != null && previous != ownerId) {
                    val tables = readTables(this)
                    if (previous.isNotEmpty()) {
                        val snapshot = Json.encodeToString(tables)
                        usePrepared("INSERT OR REPLACE INTO account_library_snapshot(ownerId, contents) VALUES (?, ?)") {
                            it.bindText(1, previous); it.bindText(2, snapshot); it.step()
                        }
                    }
                    val saved = usePrepared("SELECT contents FROM account_library_snapshot WHERE ownerId = ?") {
                        it.bindText(1, ownerId)
                        if (it.step()) it.getText(0) else null
                    }
                    execSQL("PRAGMA defer_foreign_keys = ON")
                    // Child/parent rows are replaced in the same deferred-FK transaction.
                    tables.forEach { execSQL("DELETE FROM ${identifier(it.name)}") }
                    if (saved != null) restoreTables(this, Json.decodeFromString(saved), tables)
                }
                usePrepared("INSERT OR REPLACE INTO account_library_state(id, ownerId) VALUES (1, ?)") {
                    it.bindText(1, ownerId); it.step()
                }
            }
        }
        database.invalidationTracker.refreshAsync()
    }

    private suspend fun readTables(connection: PooledConnection): List<LibraryTable> {
        val names = connection.usePrepared("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name") {
            buildList {
                while (it.step()) {
                    val name = it.getText(0)
                    if (!name.startsWith("sqlite_") && !name.startsWith("room_") &&
                        name !in setOf("android_metadata", "account_library_snapshot", "account_library_state", "library_removal")) add(name)
                }
            }
        }
        return names.map { name ->
            connection.usePrepared("SELECT * FROM ${identifier(name)}") { statement ->
                val columns = (0 until statement.getColumnCount()).map { statement.getColumnName(it) }
                val rows = buildList {
                    while (statement.step()) add(columns.indices.map { index ->
                        val type = statement.getColumnType(index)
                        LibraryCell(type, when (type) {
                            1 -> statement.getLong(index).toString()
                            2 -> statement.getDouble(index).toString()
                            3 -> statement.getText(index)
                            4 -> encodeBlob(statement.getBlob(index))
                            5 -> null
                            else -> error("Unsupported SQLite cell type")
                        })
                    })
                }
                LibraryTable(name, columns, rows)
            }
        }
    }

    private suspend fun restoreTables(connection: PooledConnection, saved: List<LibraryTable>, schema: List<LibraryTable>) {
        val current = schema.associateBy { it.name }
        for (table in saved) {
            val allowed = requireNotNull(current[table.name]) { "Unknown archived table" }
            require(table.columns.distinct().size == table.columns.size && table.columns.all { it in allowed.columns })
            for (row in table.rows) {
                require(row.size == table.columns.size)
                val sql = "INSERT INTO ${identifier(table.name)} (${table.columns.joinToString { identifier(it) }}) VALUES (${row.joinToString { "?" }})"
                connection.usePrepared(sql) { statement ->
                    row.forEachIndexed { index, cell ->
                        when (cell.type) {
                            1 -> statement.bindLong(index + 1, requireNotNull(cell.value).toLong())
                            2 -> statement.bindDouble(index + 1, requireNotNull(cell.value).toDouble())
                            3 -> statement.bindText(index + 1, requireNotNull(cell.value))
                            4 -> statement.bindBlob(index + 1, decodeBlob(requireNotNull(cell.value)))
                            5 -> statement.bindNull(index + 1)
                            else -> error("Unsupported archived cell type")
                        }
                    }
                    statement.step()
                }
            }
        }
    }

    private fun identifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    @OptIn(ExperimentalEncodingApi::class)
    private fun encodeBlob(value: ByteArray) = Base64.encode(value)
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeBlob(value: String) = Base64.decode(value)
}
