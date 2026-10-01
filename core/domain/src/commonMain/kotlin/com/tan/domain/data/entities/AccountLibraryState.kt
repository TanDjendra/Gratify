package com.tan.domain.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "account_library_state")
data class AccountLibraryState(@PrimaryKey val id: Int = 1, val ownerId: String)

/** Private, local archive. It must never be uploaded as another account's data. */
@Entity(tableName = "account_library_snapshot")
data class AccountLibrarySnapshot(@PrimaryKey val ownerId: String, val contents: String)

@Entity(tableName = "library_removal", primaryKeys = ["ownerId", "tableName", "itemId"])
data class LibraryRemoval(val ownerId: String, val tableName: String, val itemId: String, val revision: String,
    @ColumnInfo(defaultValue = "0") val enabled: Int = 0)
