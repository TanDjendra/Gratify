package com.tan.gratify.ui.screen.home

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.domain.data.entities.NotificationEntity
import com.tan.gratify.extension.formatTimeAgo
import com.tan.gratify.ui.component.CenterLoadingBox
import com.tan.gratify.ui.component.EndOfPage
import com.tan.gratify.ui.component.RippleIconButton
import com.tan.gratify.ui.navigation.destination.list.AlbumDestination
import com.tan.gratify.ui.navigation.destination.list.ArtistDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.NotificationViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.album
import gratify.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import gratify.composeapp.generated.resources.baseline_arrow_outward_24
import gratify.composeapp.generated.resources.holder
import gratify.composeapp.generated.resources.mono
import gratify.composeapp.generated.resources.new_release
import gratify.composeapp.generated.resources.no_notification
import gratify.composeapp.generated.resources.notification
import gratify.composeapp.generated.resources.singles

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    navController: NavController,
    viewModel: NotificationViewModel = koinViewModel(),
) {
    val listNotification by viewModel.listNotification.collectAsStateWithLifecycle()
    Column {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(Res.string.notification),
                    style = typo().titleMedium,
                )
            },
            navigationIcon = {
                RippleIconButton(resId = Res.drawable.baseline_arrow_back_ios_new_24) {
                    navController.navigateUp()
                }
            },
        )
        Crossfade(targetState = listNotification) {
            if (it == null) {
                Box(
                    Modifier.fillMaxSize(),
                ) {
                    CenterLoadingBox(modifier = Modifier.align(Alignment.Center))
                }
            } else if (it.isNotEmpty()) {
                LazyColumn(modifier = Modifier.padding(15.dp)) {
                    items(it) { notification ->
                        SwipeableNotificationItem(
                            notification = notification,
                            navController = navController,
                            onFollowBack = { followerId ->
                                viewModel.followBack(followerId)
                            },
                            onDelete = {
                                viewModel.deleteNotification(notification.id)
                            }
                        )
                    }
                    item {
                        EndOfPage()
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxSize(),
                ) {
                    Text(
                        text = stringResource(Res.string.no_notification),
                        style = typo().titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableNotificationItem(
    notification: NotificationEntity,
    navController: NavController,
    onFollowBack: (String) -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(5.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFE57373)), // Soft red
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = Color.White,
                    modifier = Modifier.padding(end = 20.dp)
                )
            }
        },
        content = {
            NotificationItem(
                notification = notification,
                navController = navController,
                onFollowBack = onFollowBack
            )
        }
    )
}

@Composable
fun NotificationItem(
    notification: NotificationEntity,
    navController: NavController,
    onFollowBack: (String) -> Unit = {}
) {
    if (notification.channelId == "developer") {
        DeveloperNotificationItem(notification = notification)
    } else if (notification.channelId == "follower_activity") {
        FollowNotificationItem(notification = notification, onFollowBack = onFollowBack, navController = navController)
    } else {
        Box(
            modifier =
                Modifier
                    .padding(5.dp)
                    .fillMaxWidth(),
        ) {
            Column {
                Row(
                    Modifier.clickable {
                        navController.navigate(
                            ArtistDestination(
                                channelId = notification.channelId,
                            ),
                        )
                    },
                ) {
                    val thumb = notification.thumbnail
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalPlatformContext.current)
                                .data(thumb)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .diskCacheKey(thumb)
                                .crossfade(true)
                                .build(),
                        placeholder = painterResource(Res.drawable.holder),
                        error = painterResource(Res.drawable.holder),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .align(Alignment.CenterVertically)
                                .size(50.dp)
                                .clip(
                                    CircleShape,
                                ),
                    )
                    Spacer(modifier = Modifier.padding(5.dp))
                    Column {
                        Text(text = stringResource(Res.string.new_release), style = typo().titleSmall)
                        Spacer(modifier = Modifier.padding(3.dp))
                        Text(text = notification.name, style = typo().headlineMedium)
                    }
                }
                LazyRow(
                    Modifier.padding(top = 15.dp),
                ) {
                    items(notification.single) { single ->
                        ItemAlbumNotification(
                            isAlbum = false,
                            browseId = single["browseId"] ?: "",
                            title = single["title"] ?: "",
                            thumbnail = single["thumbnails"],
                            navController,
                        )
                    }
                    items(notification.album) { album ->
                        ItemAlbumNotification(
                            isAlbum = true,
                            browseId = album["browseId"] ?: "",
                            title = album["title"] ?: "",
                            thumbnail = album["thumbnails"],
                            navController = navController,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = notification.time.formatTimeAgo(),
                style = typo().titleSmall,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 15.dp),
            )
        }
    }
}

@Composable
fun DeveloperNotificationItem(
    notification: NotificationEntity,
) {
    val uriHandler = LocalUriHandler.current
    val body = notification.single.firstOrNull()?.get("title") ?: ""
    val url = notification.single.firstOrNull()?.get("browseId") ?: ""

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.mono),
                        contentDescription = "Developer Logo",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Pesan Pengembang",
                        style = typo().titleSmall,
                        color = Color(0xFFB7B6B6)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = notification.name,
                        style = typo().headlineMedium,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = notification.time.formatTimeAgo(),
                    style = typo().bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.End
                )
            }

            if (body.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = body,
                    style = typo().bodyMedium,
                    color = Color(0xFFE4E2E6),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (url.startsWith("http")) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            try {
                                uriHandler.openUri(url)
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                        .background(Color(0x19FFFFFF))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Kunjungi Tautan",
                        style = typo().titleSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(Res.drawable.baseline_arrow_outward_24),
                        contentDescription = "Buka Link",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ItemAlbumNotification(
    isAlbum: Boolean,
    browseId: String,
    title: String,
    thumbnail: String?,
    navController: NavController,
) {
    Box(
        modifier =
            Modifier
                .clickable {
                    navController.navigate(
                        AlbumDestination(
                            browseId = browseId,
                        ),
                    )
                },
    ) {
        Column(
            Modifier.padding(5.dp),
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(thumbnail)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(thumbnail)
                        .crossfade(true)
                        .build(),
                placeholder = painterResource(Res.drawable.holder),
                error = painterResource(Res.drawable.holder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(150.dp)
                        .clip(
                            RoundedCornerShape(10),
                        ),
            )
            Text(
                text = title,
                style = typo().titleSmall,
                color = Color.White,
                maxLines = 1,
                modifier =
                    Modifier
                        .width(150.dp)
                        .wrapContentHeight(align = Alignment.CenterVertically)
                        .padding(top = 10.dp)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ).focusable(),
            )
            Text(
                text = if (isAlbum) stringResource(Res.string.album) else stringResource(Res.string.singles),
                style = typo().bodySmall,
                maxLines = 1,
                modifier =
                    Modifier
                        .width(150.dp)
                        .wrapContentHeight(align = Alignment.CenterVertically)
                        .padding(top = 10.dp)
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                        ).focusable(),
            )
        }
    }
}

@Composable
fun FollowNotificationItem(
    notification: NotificationEntity,
    navController: NavController,
    onFollowBack: (String) -> Unit
) {
    val (isFollowed, setIsFollowed) = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val followerId = notification.single.firstOrNull()?.get("browseId") ?: ""
    val name = notification.name

    Box(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 5.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable {
                    if (followerId.isNotEmpty()) {
                        navController.navigate(com.tan.gratify.ui.navigation.destination.social.UserProfileDestination(userId = followerId))
                    }
                }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(notification.thumbnail)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(notification.thumbnail)
                        .crossfade(true)
                        .build(),
                    placeholder = painterResource(Res.drawable.holder),
                    error = painterResource(Res.drawable.holder),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$name mulai mengikuti Anda",
                        style = typo().titleSmall,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = notification.time.formatTimeAgo(),
                        style = typo().bodySmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isFollowed) {
                androidx.compose.material3.Button(
                    onClick = {
                        setIsFollowed(true)
                        onFollowBack(followerId)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ikuti Balik")
                }
            } else {
                androidx.compose.material3.OutlinedButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mengikuti")
                }
            }
        }
    }
}