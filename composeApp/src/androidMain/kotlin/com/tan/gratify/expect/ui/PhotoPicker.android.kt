package com.tan.gratify.expect.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun photoPickerResult(shape: PhotoCropShape, onResultUri: (String?) -> Unit): PhotoPickerLauncher {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResultUri)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var active by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selected = uri?.toString()
        if (uri == null) { active = false; callback(null) }
    }
    selected?.let { value -> key(value) {
        PhotoCropDialog(context, Uri.parse(value), shape, onCancel = {
            selected = null
            active = false
            callback(null)
        }, onSaved = {
            selected = null
            active = false
            callback(it)
        })
    } }
    return remember(launcher) { object : PhotoPickerLauncher {
        override val isActive: Boolean get() = active
        override fun launch() {
            if (active) return
            active = true
            launcher.launch(arrayOf("image/*"))
        }
    } }
}
