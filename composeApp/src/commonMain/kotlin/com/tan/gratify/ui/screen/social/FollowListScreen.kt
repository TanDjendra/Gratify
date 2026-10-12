package com.tan.gratify.ui.screen.social

import com.tan.gratify.ui.theme.GratifyColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.tan.gratify.ui.component.UserAvatar
import com.tan.gratify.ui.component.CenterLoadingBox
import com.tan.gratify.ui.navigation.destination.social.UserProfileDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.FollowListViewModel
import androidx.compose.material.icons.rounded.QrCodeScanner
import com.tan.gratify.expect.ui.rememberQrScannerLauncher
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import com.tan.domain.utils.parseProfileUserId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth

@Composable
fun FollowListScreen(
    userId: String,
    initialTab: Int,
    navController: NavController,
    viewModel: FollowListViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    val supabase: SupabaseClient = koinInject()
    val sessionStatus by supabase.auth.sessionStatus.collectAsStateWithLifecycle()

    val scanLauncher = rememberQrScannerLauncher { scannedValue ->
        val scannedId = parseProfileUserId(scannedValue)
        if (scannedId != null) navController.navigate(UserProfileDestination(userId = scannedId))
        else viewModel.makeToast("QR ini bukan tautan profil Gratify.")
    }


    LaunchedEffect(userId, navBackStackEntry, sessionStatus) {
        viewModel.loadData(userId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Daftar Akun",
                style = typo().titleLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = scanLauncher) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = "Scan QR Code",
                    tint = Color.White
                )
            }
        }

        // Tabs
        val tabs = listOf("Pengikut", "Mengikuti")
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = typo().bodyLarge.copy(
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (selectedTabIndex == index) Color.White else GratifyColors.TextSecondary
                        )
                    }
                )
            }
        }

        Text("Menampilkan akun yang dapat kamu lihat.", style = typo().bodySmall,
            color = GratifyColors.TextSecondary, modifier = Modifier.padding(16.dp))
        val followers = selectedTabIndex == 0
        val list = if (followers) state.followers else state.following
        val loading = if (followers) state.followersLoading else state.followingLoading
        val error = if (followers) state.followersError else state.followingError
        val more = if (followers) state.moreFollowers else state.moreFollowing
        val retry = { if (followers) viewModel.retryFollowers() else viewModel.retryFollowing() }
        if (loading && list.isEmpty()) {
            CenterLoadingBox(Modifier.fillMaxSize())
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(list, key = { it.id }) { profile ->
                    Row(modifier = Modifier.fillMaxWidth()
                        .clickable { navController.navigate(UserProfileDestination(profile.id)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(imageUrl = profile.avatarUrl, name = profile.displayName, modifier = Modifier.size(50.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(profile.displayName ?: "Pengguna Gratify", style = typo().bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White, modifier = Modifier.weight(1f))
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (error != null) {
                            Text(error, color = GratifyColors.TextSecondary)
                            TextButton(onClick = retry, enabled = !loading) { Text("Coba lagi") }
                        } else if (list.isEmpty()) {
                            Text("Belum ada ${tabs[selectedTabIndex].lowercase()} yang dapat ditampilkan.", color = GratifyColors.TextSecondary)
                        } else if (loading) {
                            Text("Memuat akun…", color = GratifyColors.TextSecondary)
                        } else if (more) {
                            TextButton(onClick = { if (followers) viewModel.loadMoreFollowers() else viewModel.loadMoreFollowing() }) { Text("Lihat lainnya") }
                        }
                    }
                }
            }
        }
    }
}
