package com.tan.gratify

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tan.domain.manager.DataStoreManager
import com.tan.gratify.ui.component.DeveloperSupportSheet
import com.tan.gratify.ui.component.TrackApplicationOpenings
import com.tan.gratify.ui.theme.AppTheme
import com.tan.gratify.viewModel.support.SupportPromptController
import com.tan.gratify.viewModel.support.SupportPromptStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.json.JSONObject

/** Debug-only sample backdrop; production prompt/controller, isolated installation history. */
class SupportPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("SupportPreview", "Created support preview")
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val seed = if (savedInstanceState == null) intent.getStringExtra("seed") else null
        val failLink = intent.getBooleanExtra("fail_link", false)
        val preview = ViewModelProvider(this)[SupportPreviewState::class.java]
        setContent { AppTheme {
            val store = remember { GlobalContext.get().get<DataStoreManager>() }
            val key = "developer_support_preview_v1"
            val controller = preview.controller
            var ready by remember { mutableStateOf(preview.initialized) }
            val status by controller.status.collectAsState()
            val scope = rememberCoroutineScope()
            LaunchedEffect(Unit) {
                if (preview.initialized) return@LaunchedEffect
                when (seed) {
                    "fresh" -> store.putString(key, "{}")
                    "twenty" -> store.putString(key, "{\"openings\":20,\"lastShownOpening\":1}")
                    "nineteen" -> store.putString(key, "{\"openings\":19,\"lastShownOpening\":1}")
                    "advance" -> {
                        val current = JSONObject(store.getString(key).first() ?: "{}")
                        current.put("openings", current.optLong("openings") + 20)
                        store.putString(key, current.toString())
                    }
                }
                ready = true
                preview.initialized = true
            }
            if (ready) TrackApplicationOpenings(controller)
            LaunchedEffect(status) {
                if (status == SupportPromptStatus.Pending) { delay(700); controller.present() }
            }
            Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D)).systemBarsPadding()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("Gratify", color = Color(0xFF1ED760), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Untukmu", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Putar lagi", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        for (color in listOf(Color(0xFF615A78), Color(0xFF69784A))) {
                            Box(Modifier.weight(1f).aspectRatio(1f).background(color, RoundedCornerShape(8.dp)))
                        }
                    }
                    Text("Pilihan hari ini", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Text("Home · Search · Library", Modifier.align(Alignment.BottomCenter).padding(24.dp), color = Color.LightGray)
                if (status == SupportPromptStatus.Visible) DeveloperSupportSheet(
                    onDismiss = { optedOut -> scope.launch { controller.dismiss(optedOut) } },
                    onOpenLink = { url ->
                        if (failLink) error("Debug link handler unavailable")
                        Log.i("SupportPreview", "Selected URL: $url")
                    },
                )
            }
        } }
    }
}

class SupportPreviewState : ViewModel() {
    private val store = GlobalContext.get().get<DataStoreManager>()
    val controller = SupportPromptController(
        { store.getString("developer_support_preview_v1").first() },
        { store.putString("developer_support_preview_v1", it) },
    )
    var initialized = false
}
