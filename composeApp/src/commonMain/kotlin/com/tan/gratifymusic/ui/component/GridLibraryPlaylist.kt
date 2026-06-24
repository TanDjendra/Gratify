package com.tan.gratifymusic.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tan.domain.data.entities.AlbumEntity
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.PlaylistEntity
import com.tan.domain.data.entities.PodcastsEntity
import com.tan.domain.data.model.searchResult.playlists.PlaylistsResult
import com.tan.domain.data.type.ChartItem
import com.tan.domain.data.type.PlaylistType
import com.tan.domain.utils.LocalResource
import com.tan.logger.Logger
import com.tan.gratifymusic.extension.angledGradientBackground
import com.tan.gratifymusic.extension.isScrollingUp
import com.tan.gratifymusic.ui.navigation.destination.list.AlbumDestination
import com.tan.gratifymusic.ui.navigation.destination.list.LocalPlaylistDestination
import com.tan.gratifymusic.ui.navigation.destination.list.PlaylistDestination
import com.tan.gratifymusic.ui.navigation.destination.list.PodcastDestination
import com.tan.gratifymusic.ui.theme.seed
import com.tan.gratifymusic.ui.theme.typo
import com.tan.gratifymusic.ui.theme.white
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import gratifymusic.composeapp.generated.resources.Res
import gratifymusic.composeapp.generated.resources.create
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal inline fun <reified T> GridLibraryPlaylist(
    navController: NavController,
    contentPadding: PaddingValues,
    data: LocalResource<List<T>>,
    titleText: StringResource,
    iconRes: DrawableResource,
    gradientColors: List<Color>,
    emptyDescText: StringResource,
    emptyRecText: StringResource,
    noinline onScrolling: (onTop: Boolean) -> Unit = { _ -> },
    noinline createNewPlaylist: (() -> Unit)? = null,
    noinline onReload: () -> Unit,
) {
    Logger.w("GridLibraryPlaylist", "Generic Type: ${T::class.simpleName}")
    val state = rememberLazyGridState()
    val isScrollingUp by state.isScrollingUp()

    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }
            .collect {
                if (it <= 1) {
                    onScrolling.invoke(true)
                } else {
                    onScrolling.invoke(isScrollingUp)
                }
            }
    }
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        modifier = Modifier.fillMaxSize(),
        state = pullToRefreshState,
        onRefresh = onReload,
        isRefreshing = data is LocalResource.Loading,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = data is LocalResource.Loading,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(
                            top = contentPadding.calculateTopPadding(),
                        ),
                containerColor = PullToRefreshDefaults.indicatorContainerColor,
                color = PullToRefreshDefaults.indicatorColor,
                maxDistance = PullToRefreshDefaults.PositionalThreshold,
            )
        },
    ) {
        Crossfade(targetState = data) { data ->
            val list = (data as? LocalResource.Success)?.data ?: emptyList()
            if ((data is LocalResource.Success && list.isNotEmpty()) || createNewPlaylist != null) {
                LazyVerticalGrid(
                    columns = GridCells.FixedSize(size = 132.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    contentPadding = contentPadding,
                    state = state,
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(titleText),
                            style = typo().titleLarge,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }
                    if (createNewPlaylist != null) {
                        item {
                            Box(
                                modifier =
                                    Modifier.clickable {
                                        createNewPlaylist()
                                    },
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .padding(10.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(132.dp)
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .angledGradientBackground(
                                                colors =
                                                    listOf(
                                                        seed,
                                                        white.copy(alpha = 0.8f),
                                                    ),
                                                degrees = 45f,
                                            ),
                                        Alignment.Center,
                                    ) {
                                        Icon(
                                            modifier = Modifier.size(84.dp),
                                            imageVector = Icons.Rounded.Add,
                                            tint = white,
                                            contentDescription = null,
                                        )
                                    }
                                    Text(
                                        text = stringResource(Res.string.create),
                                        style = typo().titleSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        modifier =
                                            Modifier
                                                .width(132.dp)
                                                .wrapContentHeight(align = Alignment.CenterVertically)
                                                .padding(top = 8.dp)
                                                .basicMarquee(
                                                    iterations = Int.MAX_VALUE,
                                                    animationMode = MarqueeAnimationMode.Immediately,
                                                ).focusable(),
                                    )
                                }
                            }
                        }
                    }
                    items(list) { item ->
                        if (item !is PlaylistType) {
                            return@items
                        }
                        HomeItemContentPlaylist(
                            onClick = {
                                when (item) {
                                    is ChartItem -> {
                                        navController.navigate(
                                            PlaylistDestination(
                                                playlistId = item.ytPlaylistId,
                                                isYourYouTubePlaylist = false,
                                            ),
                                        )
                                    }

                                    is LocalPlaylistEntity -> {
                                        navController.navigate(
                                            LocalPlaylistDestination(
                                                item.id,
                                            ),
                                        )
                                    }

                                    is PlaylistsResult -> {
                                        navController.navigate(
                                            PlaylistDestination(
                                                item.browseId,
                                                isYourYouTubePlaylist = true,
                                            ),
                                        )
                                    }

                                    is AlbumEntity -> {
                                        navController.navigate(
                                            AlbumDestination(
                                                item.browseId,
                                            ),
                                        )
                                    }

                                    is PlaylistEntity -> {
                                        navController.navigate(
                                            PlaylistDestination(
                                                item.id,
                                            ),
                                        )
                                    }

                                    is PodcastsEntity -> {
                                        navController.navigate(
                                            PodcastDestination(
                                                podcastId = item.podcastId,
                                            ),
                                        )
                                    }
                                }
                            },
                            data = item,
                            thumbSize = 132.dp,
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val uriHandler = LocalUriHandler.current
                        GratifyMusicChartButton(
                            modifier =
                                Modifier.wrapContentWidth().padding(
                                    vertical = 16.dp,
                                ),
                            onClick = {
                                uriHandler.openUri("https://chart.gratifymusic.org")
                            },
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EndOfPage()
                    }
                }
            } else if (data is LocalResource.Loading) {
                CenterLoadingBox(
                    Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding
                ) {
                    item {
                        Text(
                            text = stringResource(titleText),
                            style = typo().titleLarge,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )
                    }
                    item {
                        LibraryEmptyRecommendationBox(
                            iconRes = iconRes,
                            gradientColors = gradientColors,
                            emptyDescText = emptyDescText,
                            emptyRecText = emptyRecText
                        )
                    }
                }
            }
        }
    }
}