package com.tan.gratifymusic.viewModel

import androidx.lifecycle.viewModelScope
import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.extension.now
import com.tan.domain.repository.CommonRepository
import com.tan.gratifymusic.viewModel.base.BaseViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RemoteNotification(
    val title: String,
    val body: String,
    val url: String? = null,
    val time: String? = null
)

class NotificationViewModel(
    private val commonRepository: CommonRepository,
) : BaseViewModel() {
    private var _listNotification: MutableStateFlow<List<NotificationEntity>?> =
        MutableStateFlow(null)
    val listNotification: StateFlow<List<NotificationEntity>?> = _listNotification

    init {
        refreshNotifications()
        viewModelScope.launch {
            try {
                syncNotifications()
            } catch (e: Exception) {
                com.tan.logger.Logger.e("NotificationVM", "Failed to sync notifications: ${e.message}")
            }
        }
    }

    private fun refreshNotifications() {
        viewModelScope.launch {
            commonRepository.getAllNotifications().collect { notificationEntities ->
                _listNotification.value =
                    notificationEntities?.sortedByDescending {
                        it.time
                    }
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun syncNotifications() {
        val client = HttpClient()
        val responseText = try {
            client.get("https://tanweb.vercel.app/notifications.json").bodyAsText()
        } finally {
            client.close()
        }

        val remoteNotifications = try {
            json.decodeFromString<List<RemoteNotification>>(responseText)
        } catch (e: Exception) {
            com.tan.logger.Logger.e("NotificationVM", "Failed to parse remote notifications: ${e.message}")
            emptyList()
        }

        if (remoteNotifications.isEmpty()) return

        val localNotifications = commonRepository.getAllNotifications().firstOrNull() ?: emptyList()
        var insertedAny = false

        for (remote in remoteNotifications) {
            val isDuplicate = localNotifications.any { local ->
                local.name == remote.title &&
                        local.single.firstOrNull()?.get("title") == remote.body
            }

            if (!isDuplicate) {
                val parsedTime = try {
                    remote.time?.let { LocalDateTime.parse(it) } ?: now()
                } catch (e: Exception) {
                    now()
                }

                val entity = NotificationEntity(
                    channelId = "developer",
                    thumbnail = null,
                    name = remote.title,
                    single = listOf(
                        mapOf(
                            "title" to remote.body,
                            "browseId" to (remote.url ?: "")
                        )
                    ),
                    album = emptyList(),
                    time = parsedTime
                )

                commonRepository.insertNotification(entity)
                insertedAny = true
            }
        }

        if (insertedAny) {
            refreshNotifications()
        }
    }
}