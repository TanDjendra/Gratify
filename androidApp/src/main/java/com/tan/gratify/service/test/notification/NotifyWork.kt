package com.tan.gratify.service.test.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tan.domain.data.entities.FollowedArtistSingleAndAlbum
import com.tan.domain.data.entities.NotificationEntity
import com.tan.domain.data.model.searchResult.albums.AlbumsResult
import com.tan.domain.repository.AlbumRepository
import com.tan.domain.repository.ArtistRepository
import com.tan.domain.repository.CommonRepository
import com.tan.domain.utils.newReleaseIds
import com.tan.gratify.viewModel.MoreAlbumsViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NotifyWork(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val albums: AlbumRepository by inject()
    private val artists: ArtistRepository by inject()
    private val notifications: CommonRepository by inject()

    override suspend fun doWork(): Result {
        var failed = false
        val owner = notifications.notificationAccountOwner()
        try {
            val artistList = withTimeout(15_000) { artists.getFollowedArtists().first() }
            val previous = withTimeout(15_000) { albums.getAllFollowedArtistSingleAndAlbums().first() }.orEmpty()
            NotificationHandler.createNotificationChannel(applicationContext)
            for (artist in artistList) {
                if (notifications.notificationAccountOwner() != owner) return Result.success()
                try {
                    val (albumItems, singleItems) = withTimeout(30_000) { coroutineScope {
                        val a = async { albums.getAlbumMore("MPAD${artist.channelId}", MoreAlbumsViewModel.ALBUM_PARAM).first() }
                        val s = async { albums.getAlbumMore("MPAD${artist.channelId}", MoreAlbumsViewModel.SINGLE_PARAM).first() }
                        // A failed scan must never erase either baseline.
                        requireNotNull(a.await()?.second) to requireNotNull(s.await()?.second)
                    } }
                    if (notifications.notificationAccountOwner() != owner) return Result.success()
                    val saved = previous.firstOrNull { it.channelId == artist.channelId }
                    val newAlbums = newReleaseIds(saved?.album?.mapNotNull { it["browseId"] }, albumItems.map { it.browseId })
                    val newSingles = newReleaseIds(saved?.single?.mapNotNull { it["browseId"] }, singleItems.map { it.browseId })
                    val model = NotificationModel(artist.name, artist.channelId,
                        singleItems.filter { it.browseId in newSingles }, albumItems.filter { it.browseId in newAlbums })
                    if (model.single.isNotEmpty() || model.album.isNotEmpty()) {
                        val entity = NotificationEntity(channelId = model.channelId, thumbnail = artist.thumbnails,
                            name = model.name, single = model.single.toMap(owner), album = model.album.toMap(owner))
                        if (notifications.insertNotificationOnce(entity)) NotificationHandler.createReminderNotification(applicationContext, model)
                    }
                    if (notifications.notificationAccountOwner() != owner) return Result.success()
                    albums.insertFollowedArtistSingleAndAlbum(FollowedArtistSingleAndAlbum(
                        channelId = artist.channelId, name = artist.name,
                        single = singleItems.toMap(), album = albumItems.toMap(),
                    ))
                } catch (_: TimeoutCancellationException) { failed = true }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
            }
        } catch (_: TimeoutCancellationException) { failed = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        return if (!failed) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

private fun List<AlbumsResult>.toMap(owner: String? = null): List<Map<String, String>> = map {
    buildMap {
        put("browseId", it.browseId); put("title", it.title); put("thumbnails", it.thumbnails.lastOrNull()?.url.orEmpty())
        owner?.let { id -> put("ownerId", id) }
    }
}

data class NotificationModel(val name: String, val channelId: String, val single: List<AlbumsResult>, val album: List<AlbumsResult>)
