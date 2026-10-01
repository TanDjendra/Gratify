package com.tan.gratify.ui.screen.login

import com.tan.gratify.ui.theme.GratifyColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.tan.gratify.expect.ui.photoPickerResult
import com.tan.gratify.ui.navigation.destination.MainDestination
import com.tan.gratify.ui.theme.typo
import com.tan.gratify.viewModel.CreateProfileViewModel
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import gratify.composeapp.generated.resources.Res
import gratify.composeapp.generated.resources.baseline_add_photo_alternate_24

private val GratifyAccent = GratifyColors.Accent
private val BackgroundBlack = GratifyColors.Background
private val DarkGray = GratifyColors.SurfaceRaised
private val LightGray = GratifyColors.Accent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProfileScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    hideBottomNavigation: () -> Unit = {},
    showBottomNavigation: () -> Unit = {},
    viewModel: CreateProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Photo picker
    val photoPicker = photoPickerResult { uri ->
        if (uri != null) {
            viewModel.updateAvatarPath(uri)
        }
    }

    LaunchedEffect(Unit) {
        hideBottomNavigation()
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            navController.navigate(MainDestination) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(BackgroundBlack),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Buat profil kamu",
                        style = typo().titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundBlack,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = BackgroundBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(32.dp))

            // Avatar Preview — clickable to pick photo
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(DarkGray)
                    .border(2.dp, GratifyAccent, CircleShape)
                    .clickable { photoPicker.launch() },
                contentAlignment = Alignment.Center
            ) {
                if (uiState.avatarPath.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(uiState.avatarPath)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = "Default Avatar",
                        modifier = Modifier.size(64.dp),
                        tint = GratifyColors.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ketuk untuk pilih foto",
                style = typo().bodySmall,
                color = GratifyColors.TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Display Name Input
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = { viewModel.updateDisplayName(it) },
                label = { Text("Nama Tampilan", color = GratifyColors.TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LightGray,
                    unfocusedBorderColor = DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = GratifyAccent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.saveProfile() }
                )
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ini adalah nama yang akan ditampilkan di profil kamu.",
                style = typo().bodySmall.copy(color = GratifyColors.TextSecondary)
            )

            Spacer(Modifier.height(48.dp))

            // Save Button
            Button(
                onClick = { viewModel.saveProfile() },
                enabled = uiState.displayName.isNotEmpty() && !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GratifyAccent,
                    disabledContainerColor = DarkGray,
                    contentColor = Color.Black,
                    disabledContentColor = GratifyColors.TextSecondary
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = if (uiState.isSaving) "Menyimpan..." else "Simpan & lanjutkan",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
