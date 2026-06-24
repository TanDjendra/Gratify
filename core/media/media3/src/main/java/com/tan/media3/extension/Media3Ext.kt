package com.tan.media3.extension

import android.content.Context
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.tan.common.MEDIA_CUSTOM_COMMAND
import com.tan.common.MERGING_DATA_TYPE
import com.tan.domain.data.entities.SongEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.player.GenericCommandButton
import com.tan.domain.mediaservice.handler.RepeatState
import com.tan.domain.utils.connectArtists
import com.tan.domain.utils.toListName
import com.tan.media3.R

fun MediaItem?.toSongEntity(): SongEntity? =
    if (this != null) {
        SongEntity(
            videoId = this.mediaId,
            albumId = null,
            albumName = this.mediaMetadata.albumTitle.toString(),
            artistId = null,
            artistName = listOf(this.mediaMetadata.artist.toString()),
            duration = "",
            durationSeconds = 0,
            isAvailable = true,
            isExplicit = false,
            likeStatus = "INDIFFERENT",
            thumbnails = this.mediaMetadata.artworkUri.toString(),
            title = this.mediaMetadata.title.toString(),
            videoType = "",
            category = "",
            resultType = "",
            liked = false,
            totalPlayTime = 0,
            downloadState = 0,
        )
    } else {
        null
    }

@JvmName("MediaItemtoSongEntity")
@UnstableApi
fun SongEntity.toMediaItem(): MediaItem {
    val isSong = (this.thumbnails?.contains("w544") == true && this.thumbnails?.contains("h544") == true)
    return MediaItem
        .Builder()
        .setMediaId(this.videoId)
        .setUri(this.videoId)
        .setCustomCacheKey(this.videoId)
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(this.title)
                .setArtist(this.artistName?.connectArtists())
                .setArtworkUri(this.thumbnails?.toUri())
                .setAlbumTitle(this.albumName)
                .setDescription(
                    if (isSong) MERGING_DATA_TYPE.SONG else MERGING_DATA_TYPE.VIDEO,
                ).build(),
        ).build()
}

@JvmName("TracktoMediaItem")
@UnstableApi
fun Track.toMediaItem(): MediaItem {
    var thumbUrl =
        this.thumbnails?.last()?.url
            ?: "http://i.ytimg.com/vi/${this.videoId}/maxresdefault.jpg"
    if (thumbUrl.contains("w120")) {
        thumbUrl = Regex("([wh])120").replace(thumbUrl, "$1544")
    }
    val artistName: String = this.artists.toListName().connectArtists()
    val isSong =
        (
            this.thumbnails?.last()?.height != 0 &&
                this.thumbnails?.last()?.height == this.thumbnails?.last()?.width &&
                this.thumbnails?.last()?.height != null
        ) &&
            (!thumbUrl.contains("hq720") && !thumbUrl.contains("maxresdefault"))
    return MediaItem
        .Builder()
        .setMediaId(this.videoId)
        .setUri(this.videoId)
        .setCustomCacheKey(this.videoId)
        .setMediaMetadata(
            MediaMetadata
                .Builder()
                .setTitle(this.title)
                .setArtist(this.artists.toListName().connectArtists())
                .setArtworkUri(thumbUrl.toUri())
                .setAlbumTitle(this.album?.name)
                .setDescription(
                    if (isSong) MERGING_DATA_TYPE.SONG else MERGING_DATA_TYPE.VIDEO,
                ).build(),
        ).build()
}

@androidx.annotation.OptIn(UnstableApi::class)
fun List<Track>.toMediaItems(): List<MediaItem> {
    val listMediaItem = mutableListOf<MediaItem>()
    for (item in this) {
        listMediaItem.add(item.toMediaItem())
    }
    return listMediaItem
}

@UnstableApi
fun MediaItem.isSong(): Boolean = this.mediaMetadata.description?.contains(MERGING_DATA_TYPE.SONG) == true

@UnstableApi
fun MediaItem.isVideo(): Boolean = this.mediaMetadata.description?.contains(MERGING_DATA_TYPE.VIDEO) == true

fun GenericCommandButton.toCommandButton(context: Context): CommandButton =
    when (this) {
        is GenericCommandButton.Like -> {
            val liked = this.isLiked
            CommandButton
                .Builder(0)
                .setIconResId(
                    if (liked) {
                        R.drawable.baseline_favorite_24
                    } else {
                        R.drawable.baseline_favorite_border_24
                    },
                ).setDisplayName(
                    if (liked) {
                        context.getString(R.string.liked)
                    } else {
                        context.getString(
                            R.string.like,
                        )
                    },
                ).setSessionCommand(SessionCommand(MEDIA_CUSTOM_COMMAND.LIKE, Bundle()))
                .build()
        }
        GenericCommandButton.Radio -> {
            CommandButton
                .Builder(0)
                .setIconResId(R.drawable.baseline_sensors_24)
                .setDisplayName(context.getString(R.string.radio))
                .setSessionCommand(
                    SessionCommand(
                        MEDIA_CUSTOM_COMMAND.RADIO,
                        Bundle(),
                    ),
                ).build()
        }
        is GenericCommandButton.Repeat -> {
            val repeatMode = this.repeatState
            CommandButton
                .Builder(0)
                .setIconResId(
                    when (repeatMode) {
                        RepeatState.One -> R.drawable.baseline_repeat_one_24

                        RepeatState.All -> R.drawable.baseline_repeat_24_enable

                        else -> R.drawable.baseline_repeat_24
                    },
                ).setDisplayName(
                    when (repeatMode) {
                        RepeatState.One -> context.getString(R.string.repeat_one)

                        RepeatState.All -> context.getString(R.string.repeat_all)

                        else -> context.getString(R.string.repeat_off)
                    },
                ).setSessionCommand(
                    SessionCommand(
                        MEDIA_CUSTOM_COMMAND.REPEAT,
                        Bundle(),
                    ),
                ).build()
        }
        is GenericCommandButton.Shuffle -> {
            CommandButton
                .Builder(0)
                .setIconResId(
                    if (this.isShuffled) {
                        R.drawable.baseline_shuffle_24_enable
                    } else {
                        R.drawable.baseline_shuffle_24
                    },
                ).setDisplayName(context.getString(R.string.shuffle))
                .setSessionCommand(
                    SessionCommand(
                        MEDIA_CUSTOM_COMMAND.SHUFFLE,
                        Bundle(),
                    ),
                ).build()
        }
    }