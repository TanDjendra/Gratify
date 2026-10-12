package com.tan.gratify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.data.model.social.*
import com.tan.gratify.ui.screen.friends.*
import com.tan.gratify.ui.theme.*
import com.tan.gratify.viewModel.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Production notes and inbox controls with sample profiles, without account/database writes. */
class FriendsSocialPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val fail = intent.getBooleanExtra("failure", false)
        setContent { AppTheme {
            var tab by remember { mutableStateOf(FriendsInboxTab.ACTIVITY) }
            var note by remember { mutableStateOf<String?>("Musik untuk perjalanan pulang") }
            var draft by remember { mutableStateOf("") }
            var editing by remember { mutableStateOf(false) }
            var saving by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            var failOnce by remember { mutableStateOf(fail) }
            var chat by remember { mutableStateOf(false) }
            var profile by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            val old = (System.currentTimeMillis()-72*60*60*1000L).toString()
            val friends=listOf(friend("nara","Nara Prameswari","Ruang tenang","Senja",0).let {
                it.copy(userProfile=it.userProfile.copy(note="Hari ini lagi suka lagu akustik.",noteUpdatedAt=old)) },
                friend("bima","Bima Putra","Langkah baru","Ruang Selatan",1),
                FriendActivityItem(UserProfile("citra","Citra Maharani",note="Sampai ketemu di konser nanti!"),false,"","","",""))
            val conversations=listOf(DirectConversation("chat","nara","Nara",accepted=true,requestFrom="self",lastBody="Dengarkan bagian reff-nya",unreadCount=2),
                DirectConversation("request","citra","Citra",accepted=false,requestFrom="citra",lastBody="Halo, boleh kenalan?",unreadCount=1))
            val social=FriendsSocialState(userId="self",conversations=conversations,activeConversation=if(chat) conversations.first() else null,
                messages=if(chat) listOf(DirectMessage("message","chat","nara","sample","Musik untuk perjalanan pulang", "2026-10-03T00:00:00Z")) else emptyList())
            Column(Modifier.fillMaxSize().background(GratifyColors.Background).navigationBarsPadding()) {
                FriendsInboxHeader("Alia",{},{chat=true},{})
                FriendsInboxContent(FriendsActivityState(currentUserId="self",myNote=note,myNoteUpdatedAt=old,friendsList=friends),social,
                    "Alia",null,tab,{tab=it},{},{},{draft=note.orEmpty(); failed=false; editing=true},{chat=true},{profile=it},{},{},{},{},
                    contentPadding=PaddingValues(bottom=24.dp))
            }
            if(editing) FriendsNoteEditor(draft,note,saving,failed,{draft=it; failed=false},onSave={
                saving=true
                scope.launch { delay(250); saving=false
                    if(failOnce) { failed=true; failOnce=false } else { note=draft.trim(); editing=false }
                }
            },onDelete={note=null; editing=false},onClose={editing=false})
            if(chat) androidx.compose.ui.window.Dialog(onDismissRequest={chat=false},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false)) {
                FriendsChatContent(social,{chat=false},{},{_,success->success()},{},{},{},{})
            }
            if(profile.isNotEmpty()) androidx.compose.material3.AlertDialog(onDismissRequest={profile=""},title={androidx.compose.material3.Text(profile)},
                confirmButton={androidx.compose.material3.TextButton(onClick={profile=""}) { androidx.compose.material3.Text("OK") }})
        } }
    }
    private fun friend(id: String,name: String,title: String,artist: String,index: Int): FriendActivityItem {
        val image="android.resource://$packageName/${if(index==0) R.drawable.home_sample_green else R.drawable.home_sample_coral}"
        val profile=UserProfile(id,name,showRecentArtists=true,nowPlayingVideoId="sample-$id",nowPlayingTitle=title,nowPlayingArtist=artist,lastActiveAt=System.currentTimeMillis().toString())
        return FriendActivityItem(profile,true,title,artist,"sample-$id","",artworkUrl=image)
    }
}
