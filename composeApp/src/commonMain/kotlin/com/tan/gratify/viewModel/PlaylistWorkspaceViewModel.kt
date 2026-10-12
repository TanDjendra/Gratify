package com.tan.gratify.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tan.data.sync.AccountAccess
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.model.browse.album.Track
import com.tan.domain.data.model.searchResult.songs.SongsResult
import com.tan.domain.manager.DataStoreManager
import com.tan.domain.repository.LocalPlaylistRepository
import com.tan.domain.repository.SearchRepository
import com.tan.domain.repository.UserRepository
import com.tan.domain.repository.SocialRepository
import com.tan.domain.utils.toSongEntity
import com.tan.domain.data.entities.UserProfile
import com.tan.domain.utils.Resource
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable data class WorkspaceMember(@SerialName("user_id") val userId: String,
    val name: String? = null, val avatar: String? = null, val owner: Boolean = false)
@Serializable data class WorkspaceSong(val id: String, @SerialName("video_id") val videoId: String,
    val title: String, val artist: String = "", @SerialName("thumbnail_url") val thumbnail: String? = null,
    val duration: Int = 0, @SerialName("added_by") val addedBy: String? = null)
@Serializable data class PlaylistWorkspace(val id: String, @SerialName("owner_id") val ownerId: String,
    val kind: String, val title: String, val description: String = "",
    @SerialName("is_public") val isPublic: Boolean = false, val revision: Long = 0,
    @SerialName("generated_at") val generatedAt: String? = null, val member: Boolean = false,
    val items: List<WorkspaceSong> = emptyList(), val members: List<WorkspaceMember> = emptyList(),
    @SerialName("cover_path") val coverPath: String? = null, @kotlinx.serialization.Transient val coverUrl: String? = null,
    @SerialName("client_sync_id") val syncId:String?=null,
    @SerialName("source_shared_id") val sharedId:String?=null,
    @SerialName("legacy_thumbnail_url") val legacyCover:String?=null)
@Serializable data class PlaylistFolder(val id: String, val title: String, @SerialName("parent_id") val parentId: String? = null)
@Serializable data class FolderPlacement(val ref: String, @SerialName("folder_id") val folderId: String)
@Serializable data class WorkspaceLibrary(val playlists: List<PlaylistWorkspace> = emptyList(),
    val folders: List<PlaylistFolder> = emptyList(), val placements: List<FolderPlacement> = emptyList())
@Serializable data class WorkspaceInvitation(val id: String, val title: String, val kind: String,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("already_member") val alreadyMember: Boolean = false)

interface PlaylistWorkspaceGateway {
    fun currentOwner(): String?
    suspend fun call(action: String, args: JsonObject): JsonElement
    suspend fun coverUrl(path: String): String? = null
    suspend fun legacyCoverUrl(url:String):String? = url
    suspend fun uploadCover(owner: String, id: String, uri: String): String = error("Cover upload unavailable")
    suspend fun removeUnusedCover(path: String) { }
}
class SupabasePlaylistWorkspaceGateway(private val client: SupabaseClient): PlaylistWorkspaceGateway {
    override suspend fun legacyCoverUrl(url:String):String? {
        val path=url.substringAfter("/storage/v1/object/public/playlist-covers/","").substringBefore('?')
        if(path.isBlank())return url
        return coverRequests.withPermit {
            val owner=currentOwner()?:return@withPermit null
            val now=System.currentTimeMillis();val key="personal:$path"
            coverMutex.withLock {
                if(coverOwner!=owner){coverCache.clear();coverOwner=owner}
                coverCache[key]?.takeIf{now<it.first}?.let{return@withPermit it.second}
            }
            try {
                val signed=withTimeout(5_000){client.storage.from("playlist-covers").createSignedUrl(path,2.minutes)}
                if(currentOwner()!=owner)return@withPermit null
                coverMutex.withLock {
                    coverCache.entries.removeAll{now>=it.value.first}
                    while(coverCache.size>=128)coverCache.remove(coverCache.keys.first())
                    coverCache[key]=now+80_000 to signed
                }
                signed
            }catch(_:TimeoutCancellationException){null}
            catch(e:CancellationException){throw e}
            catch(_:Exception){null}
        }
    }
    private val coverMutex = Mutex()
    private val coverRequests = Semaphore(4)
    private val coverCache = linkedMapOf<String,Pair<Long,String>>()
    private var coverOwner: String? = null
    override fun currentOwner() = client.auth.currentUserOrNull()?.id
    override suspend fun call(action: String, args: JsonObject) = client.postgrest.rpc("gratify_workspace",
        buildJsonObject { put("p_action", action); put("p_args", args) }).decodeAs<JsonElement>()
    override suspend fun coverUrl(path: String): String? = coverRequests.withPermit {
        val owner=currentOwner() ?: return@withPermit null
        val now=System.currentTimeMillis()
        coverMutex.withLock {
            if(coverOwner!=owner){coverCache.clear();coverOwner=owner}
            coverCache[path]?.takeIf{now<it.first}?.let{return@withPermit it.second}
        }
        try {
            val url=withTimeout(5_000){client.storage.from("workspace-covers").createSignedUrl(path,2.minutes)}
            if(currentOwner()!=owner)return@withPermit null
            coverMutex.withLock {
                coverCache.entries.removeAll{now>=it.value.first}
                while(coverCache.size>=128)coverCache.remove(coverCache.keys.first())
                coverCache[path]=now+80_000 to url
            }
            url
        } catch(_:TimeoutCancellationException){null}
        catch(e:CancellationException){throw e}
        catch(_:Exception){null}
    }
    override suspend fun uploadCover(owner: String,id: String,uri: String): String {
        check(currentOwner()==owner){"Account changed"}
        val image=withContext(Dispatchers.IO){com.tan.gratify.utils.compressImage(uri)} ?: error("Cover image unreadable")
        require(image.size in 3..5*1024*1024 && image[0]==0xff.toByte() && image[1]==0xd8.toByte()){"Cover image invalid"}
        check(currentOwner()==owner){"Account changed"}
        val path="$owner/$id/${UUID.randomUUID()}.jpg"
        client.storage.from("workspace-covers").upload(path,image){contentType=ContentType.Image.JPEG;upsert=false}
        check(currentOwner()==owner){"Account changed"}
        return path
    }
    override suspend fun removeUnusedCover(path: String) {
        if(path.substringBefore('/')!=currentOwner())return
        withTimeout(5_000){client.storage.from("workspace-covers").delete(listOf(path))}
    }
}

data class PlaylistWorkspaceState(val owner: String? = null, val accountReady: Boolean = false,
    val library: WorkspaceLibrary = WorkspaceLibrary(), val detail: PlaylistWorkspace? = null,
    val invitation: WorkspaceInvitation? = null, val loading: Boolean = false, val busy: Boolean = false,
    val error: String? = null, val success: String? = null, val inviteLink: String? = null,
    val searchResults: List<SongsResult> = emptyList(), val searching: Boolean = false,
    val localPlaylists: List<LocalPlaylistEntity> = emptyList(), val people: List<UserProfile> = emptyList(), val peopleLoading: Boolean = false,
    val busyMessage: String = "Menyimpan perubahan…")

/** Account generations and lifecycle cancellation protect every read and mutation result. */
class PlaylistWorkspaceViewModel(private val gateway: PlaylistWorkspaceGateway,
    private val access: StateFlow<AccountAccess>, private val search: SearchRepository,
    private val playlists: LocalPlaylistRepository, private val preferences: DataStoreManager, private val users: UserRepository,
    private val social:SocialRepository?=null,
    private val experimentalFeaturesEnabled:Boolean=com.tan.domain.utils.PlaylistFeaturePolicy.experimentalEnabled): ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutable = MutableStateFlow(PlaylistWorkspaceState())
    val state = mutable.asStateFlow()
    private var generation = 0
    private var readJob: Job? = null
    private var mutationJob: Job? = null
    private var searchJob: Job? = null
    private var peopleJob: Job? = null
    private var polling: Job? = null
    private var activeId: String? = null
    private var activeToken: String? = null
    private var foreground = false
    private var readGeneration = 0
    private var lastReadError: String? = null
    private var invitePeer: String? = null
    private var inviteSendId: String? = null
    private data class UploadedCover(val owner:String,val id:String,val uri:String,val path:String)
    private var uploadedCover: UploadedCover? = null
    init {
        viewModelScope.launch {
            access.collectLatest { status ->
                val owner = (status as? AccountAccess.Ready)?.owner
                generation++; cancelWork(); lastReadError=null; invitePeer = null; inviteSendId = null; uploadedCover=null; mutable.value = PlaylistWorkspaceState(owner = owner,
                    accountReady = owner != null && owner == gateway.currentOwner())
                if (mutable.value.accountReady) {
                    refresh()
                    startPolling()
                    playlists.getAllLocalPlaylists().collect { list ->
                        if (same(owner)) mutable.update { it.copy(localPlaylists = list) }
                    }
                }
            }
        }
    }
    private fun same(owner: String?) = owner != null && owner == mutable.value.owner && owner == gateway.currentOwner() && mutable.value.accountReady
    private fun cancelWork() { readGeneration++; readJob?.cancel(); mutationJob?.cancel(); searchJob?.cancel(); peopleJob?.cancel(); polling?.cancel() }
    fun visible(id: String? = null, token: String? = null) { foreground = true; activeId = id; activeToken = token.takeIf{experimentalFeaturesEnabled}; refresh(); startPolling() }
    fun hidden() { foreground = false; polling?.cancel(); readJob?.cancel(); searchJob?.cancel(); peopleJob?.cancel() }
    private fun startPolling() {
        polling?.cancel()
        if (!foreground || !mutable.value.accountReady) return
        polling = viewModelScope.launch { while (isActive) { delay(15_000); if (!mutable.value.busy) refresh(quiet = true) } }
    }
    fun clearFeedback() { lastReadError=null; mutable.update { it.copy(error = null, success = null, inviteLink = null) } }
    fun refresh(quiet: Boolean = false) {
        val owner = mutable.value.owner ?: return
        if (!same(owner)) return
        val currentGeneration = generation; val read = ++readGeneration
        readJob?.cancel()
        if (!quiet) { lastReadError=null; mutable.update { it.copy(loading = true, error = null) } }
        readJob = viewModelScope.launch {
            try {
                val rawLibrary = if(experimentalFeaturesEnabled)json.decodeFromJsonElement<WorkspaceLibrary>(request("library")) else WorkspaceLibrary()
                check(same(owner)) { "Account changed" }
                if(experimentalFeaturesEnabled)playlists.reconcileCanonicalPlaylists(requireNotNull(owner),rawLibrary.playlists.filter{it.kind=="collaborative"}.map{it.id})
                val library=rawLibrary.copy(playlists=coroutineScope { rawLibrary.playlists.map { p -> async { displayCover(p) } }.awaitAll() })
                val token = activeToken; val id = activeId
                val invitation = token?.let { json.decodeFromJsonElement<WorkspaceInvitation>(request("preview", "token" to it)) }
                val detail = id?.let {
                    val first = json.decodeFromJsonElement<PlaylistWorkspace>(request("detail", "playlist_id" to it))
                    if(!experimentalFeaturesEnabled&&first.kind=="blend") {
                        if(same(owner)&&currentGeneration==generation&&read==readGeneration)
                            mutable.update{it.copy(detail=first,loading=false)}
                        return@launch
                    }
                    if (first.kind == "blend" && first.member) {
                        request("refresh_blend", "playlist_id" to it)
                        displayCover(json.decodeFromJsonElement<PlaylistWorkspace>(request("detail", "playlist_id" to it)))
                    } else displayCover(first)
                }
                if(detail?.kind=="collaborative"&&detail.member&&same(owner))
                    playlists.cacheCanonicalPlaylist(requireNotNull(owner),detail.id,detail.syncId,detail.title,
                        detail.coverPath?.let{"https://bnabldxsqpvkyqpjcsdv.supabase.co/storage/v1/object/public/workspace-covers/$it"}?:detail.legacyCover,
                        detail.items.map{it.playbackSong()},detail.sharedId.takeIf{detail.ownerId!=owner})
                if (same(owner) && currentGeneration == generation && read == readGeneration) {
                    val recoveredError=lastReadError
                    lastReadError=null
                    mutable.update { it.copy(library = library, detail = detail, invitation = invitation, loading = false,
                        error=if(it.error==recoveredError)null else it.error) }
                }
            } catch (e: TimeoutCancellationException) {
                if(same(owner)&&currentGeneration==generation&&read==readGeneration) {
                    lastReadError="Pembaruan terlalu lama. Periksa koneksi dan muat ulang."
                    mutable.update { it.copy(loading=false,error=lastReadError) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (same(owner) && currentGeneration == generation && read == readGeneration) {
                    lastReadError=workspaceFailure(e)
                    mutable.update { it.copy(loading = false, detail = null, invitation = null, error = lastReadError) }
                }
            }
        }
    }
    private suspend fun request(action: String, vararg args: Pair<String, String>): JsonElement =
        withTimeout(25_000) { gateway.call(action, buildJsonObject { args.forEach { (key,value) -> put(key,value) } }) }
    private suspend fun displayCover(p:PlaylistWorkspace):PlaylistWorkspace {
        val path=p.coverPath ?: return p.copy(coverUrl=p.legacyCover?.let{url->gateway.legacyCoverUrl(url)
            ?:mutable.value.detail?.takeIf{it.id==p.id&&it.legacyCover==url}?.coverUrl})
        val previous=mutable.value.detail?.takeIf{it.id==p.id&&it.coverPath==path}
            ?:mutable.value.library.playlists.find{it.id==p.id&&it.coverPath==path}
        return p.copy(coverUrl=gateway.coverUrl(path)?:previous?.coverUrl)
    }
    private fun mutate(action: String, args: JsonObject, prepare: suspend (JsonObject)->JsonObject = {it}, done: (JsonElement) -> Unit = {}) {
        if(!com.tan.domain.utils.PlaylistFeaturePolicy.allowsAction(action,args["kind"]?.jsonPrimitive?.contentOrNull,experimentalFeaturesEnabled)) {
            mutable.update{it.copy(error=com.tan.domain.utils.PlaylistFeaturePolicy.archivedMessage)};return
        }
        val owner = mutable.value.owner ?: return
        if (!same(owner) || mutable.value.busy) return
        val currentGeneration = generation
        readGeneration++; readJob?.cancel()
        lastReadError=null
        mutable.update { it.copy(busy = true, loading = false, error = null, success = null,busyMessage="Menyimpan perubahan…") }
        mutationJob = viewModelScope.launch {
            try {
                val result = withTimeout(45_000) { val prepared=prepare(args);check(same(owner)){"Account changed"};gateway.call(action, prepared) }
                if (same(owner) && currentGeneration == generation) {
                    mutable.update { it.copy(busy = false, success = "Perubahan berhasil disimpan.") }
                    done(result); refresh(quiet = true)
                }
            } catch (e: TimeoutCancellationException) {
                if(same(owner)&&currentGeneration==generation) mutable.update { it.copy(busy=false,error="Belum menerima konfirmasi server. Muat ulang sebelum mencoba lagi.") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (same(owner) && currentGeneration == generation)
                mutable.update { it.copy(busy = false, error = workspaceFailure(e)) } }
        }
    }
    fun create(kind: String, title: String, id: String, consent: Boolean, parentId: String? = null, automaticName:Boolean=false, done: (String) -> Unit) = mutate(
        if (kind == "folder") "folder_create" else "create", buildJsonObject {
            put("id", id); put("kind", kind); put("title", title.trim()); put("consent", consent)
            if(parentId!=null)put("parent_id",parentId)
            put("automatic_name",automaticName)
        }) { done(it.jsonObject.getValue("id").jsonPrimitive.content) }
    fun promoteLocal(localId:Long,done:(String)->Unit) {
        val owner=mutable.value.owner?:return
        mutate("promote",buildJsonObject {},prepare={
            val playlist=playlists.getAllLocalPlaylists().first().firstOrNull{it.id==localId}
                ?:error("Playlist unavailable")
            check(playlist.sourceSharedPlaylistId==null) { "Owner required" }
            val syncId=playlists.ensureOwnedPlaylistSyncId(localId,owner)
            val existing=request("resolve_personal","sync_id" to syncId).jsonObject["id"]?.jsonPrimitive?.contentOrNull
            if(existing==null) {
                mutable.update{it.copy(busyMessage="Menyinkronkan playlist sebelum mengundang…")}
                requireNotNull(social).syncUpPlaylist(localId,owner,isPublic=null).collect { it.getOrThrow() }
            }
            check(same(owner)) { "Account changed" }
            buildJsonObject{put("sync_id",syncId)}
        }) { result->
            activeId=result.jsonObject.getValue("id").jsonPrimitive.content
            done(requireNotNull(activeId))
        }
    }
    fun join(token: String, consent: Boolean, done: (String) -> Unit) = mutate("join", buildJsonObject {
        put("token", token); put("consent", consent)
    }) { activeToken = null; activeId = it.jsonObject.getValue("id").jsonPrimitive.content; done(activeId!!) }
    fun action(action: String, id: String, extras: JsonObject = JsonObject(emptyMap()), done: () -> Unit = {}) {
        val cover=mutable.value.detail?.takeIf{it.id==id&&it.ownerId==mutable.value.owner}?.coverPath
        mutate(action,buildJsonObject { put("playlist_id", id); extras.forEach { (key,value) -> put(key,value) } }) {
            done()
            if(action=="delete"&&cover!=null)viewModelScope.launch{
                try{gateway.removeUnusedCover(cover)}catch(e:CancellationException){throw e}catch(_:Exception){}
            }
        }
    }
    fun makeInvite(id: String, peer: String? = null) {
        if (mutable.value.busy) return
        invitePeer = null; inviteSendId = null
        mutable.update { it.copy(inviteLink = null) }
        mutate("invite", buildJsonObject {
        put("playlist_id", id); if (peer != null) put("target_id", peer)
    }) { result ->
        val token = result.jsonObject.getValue("token").jsonPrimitive.content
        invitePeer = peer; inviteSendId = UUID.randomUUID().toString()
        mutable.update { it.copy(inviteLink = "https://gratify.web.id/app/invite?token=$token", success = "Undangan siap. Berlaku 7 hari.") }
    }
    }
    fun sendInvite(peer: String) {
        if (invitePeer != peer) return
        val token = mutable.value.inviteLink?.substringAfter("?token=") ?: return
        val operation = inviteSendId ?: return
        mutate("send_invite", buildJsonObject { put("token", token); put("peer_id", peer); put("operation_id", operation) }) {
            mutable.update { it.copy(inviteLink = null, success = "Undangan terkirim di chat Gratify.") }
        }
    }
    fun folder(action: String, folderId: String?, ref: String? = null, title: String? = null, done: () -> Unit = {}) = mutate(action,
        buildJsonObject { put("folder_id", folderId?.let(::JsonPrimitive) ?: JsonNull); if (ref != null) put("ref", ref); if (title != null) put("title",title) }) { done() }
    fun moveFolder(id:String,parentId:String?,done:()->Unit={})=mutate("folder_move",buildJsonObject{
        put("folder_id",id);put("parent_id",parentId?.let(::JsonPrimitive)?:JsonNull)
    }){done()}
    fun saveDetails(id:String,title:String,description:String,public:Boolean,coverDraft:String?,resetCover:Boolean,done:()->Unit) {
        val p=mutable.value.detail?.takeIf{it.id==id&&it.ownerId==mutable.value.owner} ?: return
        val owner=p.ownerId
        var path=p.coverPath
        mutate("metadata",buildJsonObject{put("playlist_id",id);put("title",title.trim());put("description",description);put("is_public",public)},
            prepare={args->
                path=when {
                    resetCover->null
                    coverDraft!=null->{
                        mutable.update{it.copy(busyMessage="Mengunggah sampul…")}
                        val cached=uploadedCover?.takeIf{it.owner==owner&&it.id==id&&it.uri==coverDraft}
                        (cached?:UploadedCover(owner,id,coverDraft,gateway.uploadCover(owner,id,coverDraft)).also{uploadedCover=it}).path
                    }
                    else->p.coverPath
                }
                mutable.update{it.copy(busyMessage="Memperbarui playlist…")}
                buildJsonObject{args.forEach{(key,value)->put(key,value)};if(coverDraft!=null||resetCover)put("cover_path",path?.let(::JsonPrimitive)?:JsonNull)}
            }) {
                mutable.update{it.copy(detail=it.detail?.copy(title=title.trim(),description=description,isPublic=public,coverPath=path,coverUrl=if(resetCover)null else coverDraft?:p.coverUrl))}
                uploadedCover=null;done()
                if(p.coverPath!=null&&p.coverPath!=path)viewModelScope.launch{
                    if(same(owner))try{gateway.removeUnusedCover(p.coverPath)}catch(e:CancellationException){throw e}catch(_:Exception){}
                }
            }
    }
    fun add(id: String, track: Track) = action("add", id, buildJsonObject {
        put("operation_id",UUID.randomUUID().toString()); put("video_id",track.videoId); put("title",track.title)
        put("artist",track.artists.orEmpty().joinToString(", ") { it.name }.take(512)); put("duration",track.durationSeconds ?: 0)
        put("thumbnail_url",track.thumbnails?.lastOrNull()?.url.orEmpty())
    })
    fun remove(id: String, song: WorkspaceSong) = action("remove",id,buildJsonObject {
        put("operation_id",UUID.randomUUID().toString()); put("item_id",song.id)
    })
    fun reorder(id: String, ids: List<String>, revision: Long) = action("reorder",id,buildJsonObject {
        put("operation_id",UUID.randomUUID().toString()); put("ids",JsonArray(ids.map(::JsonPrimitive))); put("revision",revision)
    })
    fun search(query: String) {
        searchJob?.cancel(); mutable.update { it.copy(searchResults = emptyList(), searching = query.isNotBlank()) }
        if (query.isBlank() || !same(mutable.value.owner)) return
        val owner = mutable.value.owner; val currentGeneration = generation
        searchJob = viewModelScope.launch {
            delay(350)
            try { withTimeout(25_000) { search.getSearchDataSong(query).collect { result ->
                if (same(owner) && currentGeneration == generation) mutable.update { it.copy(searching = false,
                    searchResults = (result as? Resource.Success)?.data.orEmpty(),
                    error = if (result is Resource.Error) "Lagu belum dapat dimuat. Coba lagi." else it.error) }
            } } } catch (_: TimeoutCancellationException) { if(same(owner)&&generation==currentGeneration)mutable.update{it.copy(searching=false,error="Pencarian terlalu lama. Coba lagi.")} }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (same(owner)) mutable.update { it.copy(searching = false,error = "Lagu belum dapat dimuat. Coba lagi.") } }
        }
    }
    fun searchPeople(query: String) {
        peopleJob?.cancel(); mutable.update { it.copy(people=emptyList(),peopleLoading=query.trim().length>=2,inviteLink=null) }
        val owner=mutable.value.owner ?: return
        if(query.trim().length<2||!same(owner)) return
        val currentGeneration=generation
        peopleJob=viewModelScope.launch {
            try {
                delay(350)
                val found=withTimeout(25_000) { users.searchUsers(query.trim()).first() }
                if(same(owner)&&generation==currentGeneration) mutable.update { it.copy(people=found.filter{p->p.id!=owner}.take(20),peopleLoading=false) }
            } catch(_:TimeoutCancellationException){if(same(owner)&&generation==currentGeneration)mutable.update{it.copy(peopleLoading=false,error="Pencarian pengguna terlalu lama. Coba lagi.")}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){if(same(owner)&&generation==currentGeneration)mutable.update{it.copy(peopleLoading=false,error="Pengguna belum dapat dicari. Coba lagi.")}}
        }
    }
    suspend fun nextName(kind: String): String {
        val owner = mutable.value.owner
        val local = playlists.getAllLocalPlaylists().first()
        if(owner!=mutable.value.owner)return suggestedPlaylistName(kind,emptyList())
        val names=local.filter{it.sourceSharedPlaylistId==null}.map{it.title} +
            mutable.value.library.playlists.filter{it.ownerId==owner}.map{it.title}
        if(kind in listOf("personal","collaborative")&&same(owner)) {
            try {
                val result=withTimeout(5_000){gateway.call("next_name",buildJsonObject {
                    put("kind",kind);put("titles",JsonArray(names.take(300).map(::JsonPrimitive)))
                })}.jsonObject["title"]?.jsonPrimitive?.content
                if(owner==mutable.value.owner&&!result.isNullOrBlank())return result
            }catch(e:CancellationException){if(e !is TimeoutCancellationException)throw e}
            catch(_:Exception){ /* Offline naming still works from this account's local library. */ }
        }
        return suggestedPlaylistName(kind,names)
    }
}

/** Opening/cancelling a form does not reserve or consume a name. */
internal fun suggestedPlaylistName(kind:String,titles:List<String>):String {
    if(kind=="folder")return "Folder baru"
    if(kind=="blend")return "Blend kami"
    val prefix=if(kind=="collaborative")"Playlist kami No." else "Playlist-ku No."
    fun normalized(name:String)=name.lowercase().filterNot(Char::isWhitespace)
    val occupied=titles.map(::normalized).toHashSet()
    var number=1
    while(normalized(prefix+number) in occupied)number++
    return prefix+number
}

internal fun workspaceFailure(error: Exception): String {
    val message = error.message.orEmpty()
    return when {
        "Revision conflict" in message -> "Urutan telah diubah anggota lain. Muat ulang sebelum mengurutkan kembali."
        "Folder cycle" in message -> "Folder tidak bisa dipindahkan ke dirinya sendiri atau subfoldernya."
        "Folder depth limit" in message -> "Folder dapat disusun hingga lima tingkat. Pilih folder yang lebih dekat ke Koleksi."
        "Cover image" in message -> "Foto belum dapat dibaca. Pilih kembali foto dan crop sebelum menyimpan."
        "Cover unavailable" in message -> "Sampul belum tersimpan di server. Pilih kembali foto dan coba lagi."
        "jwt" in message.lowercase() && "expired" in message.lowercase() -> "Sesi akun perlu diperbarui. Sambungkan internet dan muat ulang."
        "Invitation expired" in message || "Invitation unavailable" in message -> "Undangan kedaluwarsa, dicabut, atau tidak tersedia untuk akun ini."
        "Member limit" in message -> "Jumlah anggota sudah mencapai batas."
        "Wait until request is accepted" in message -> "Teman perlu menerima permintaan chat sebelumnya. Kamu juga bisa membagikan link undangan."
        "Request unavailable" in message -> "Permintaan chat tidak tersedia. Bagikan link undangan melalui aplikasi lain."
        "not synchronized" in message -> "Playlist belum tersinkron. Sambungkan internet dan coba lagi."
        "Canonical playlist" in message -> "Playlist ini memakai akses anggota. Muat ulang sebelum mengubahnya."
        "Consent required" in message -> "Setujui penggunaan ringkasan selera sebelum bergabung ke Blend."
        "Owner required" in message || "Editor required" in message || "unavailable" in message -> "Playlist tidak tersedia atau akun ini belum memiliki akses."
        else -> "Perubahan belum berhasil. Periksa koneksi lalu coba lagi."
    }
}

private fun WorkspaceSong.playbackSong()=com.tan.domain.data.entities.SongEntity(videoId=videoId,title=title,
    artistName=listOf(artist),durationSeconds=duration,duration="${duration/60}:${(duration%60).toString().padStart(2,'0')}",
    thumbnails=thumbnail,isAvailable=true,isExplicit=false,likeStatus="INDIFFERENT",videoType="MUSIC_VIDEO_TYPE_ATV",category=null,resultType=null)
