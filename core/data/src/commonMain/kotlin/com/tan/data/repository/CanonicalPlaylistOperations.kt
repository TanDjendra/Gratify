package com.tan.data.repository

import com.tan.data.db.datasource.LocalDataSource
import com.tan.domain.data.entities.LocalPlaylistEntity
import com.tan.domain.data.entities.SongEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

@Serializable private data class CanonicalSong(val id:String,@SerialName("video_id") val videoId:String,
    val title:String,val artist:String="",val duration:Int=0,@SerialName("thumbnail_url") val thumbnail:String?=null)
@Serializable private data class CanonicalDetail(val id:String,val title:String,val member:Boolean=false,
    @SerialName("owner_id") val ownerId:String,
    @SerialName("source_shared_id") val sharedId:String?=null,
    val revision:Long=0,
    @SerialName("client_sync_id") val syncId:String?=null,@SerialName("legacy_thumbnail_url") val legacyCover:String?=null,
    @SerialName("cover_path") val coverPath:String?=null,val items:List<CanonicalSong> = emptyList())

/** Server mutations precede cache writes. Failed/offline mutations never fork a playlist. */
@OptIn(ExperimentalUuidApi::class)
internal class CanonicalPlaylistOperations(private val client:SupabaseClient,private val local:LocalDataSource) {
    private val json=Json { ignoreUnknownKeys=true }
    private suspend fun call(owner:String,id:String,action:String,extras:JsonObject=buildJsonObject {}):JsonElement {
        check(client.auth.currentUserOrNull()?.id==owner && local.getLibraryOwner()==owner) { "Account ownership changed" }
        val result=withTimeout(25_000) { client.postgrest.rpc("gratify_workspace",buildJsonObject {
            put("p_action",action);put("p_args",buildJsonObject {put("playlist_id",id);extras.forEach{(k,v)->put(k,v)}})
        }).decodeAs<JsonElement>() }
        check(client.auth.currentUserOrNull()?.id==owner && local.getLibraryOwner()==owner) { "Account ownership changed" }
        return result
    }
    suspend fun refresh(owner:String,id:String):Long {
        val p=json.decodeFromJsonElement<CanonicalDetail>(call(owner,id,"detail"))
        check(p.member) { "Member required" }
        val image=p.coverPath?.let{client.storageUrl(it)}?:p.legacyCover
        return local.cacheCanonicalPlaylist(owner,LocalPlaylistEntity(title=p.title,thumbnail=image,
            syncId=p.syncId?:p.id,canonicalPlaylistId=p.id,sourceSharedPlaylistId=p.sharedId.takeIf{p.ownerId!=owner}),p.items.map { song -> SongEntity(videoId=song.videoId,
                title=song.title,artistName=listOf(song.artist),durationSeconds=song.duration,
                duration="${song.duration/60}:${(song.duration%60).toString().padStart(2,'0')}",
                thumbnails=song.thumbnail,isAvailable=true,isExplicit=false,likeStatus="INDIFFERENT",
                videoType="MUSIC_VIDEO_TYPE_ATV",category=null,resultType=null) })
    }
    suspend fun add(owner:String,id:String,songs:List<SongEntity>):Int {
        val before=json.decodeFromJsonElement<CanonicalDetail>(call(owner,id,"detail")).items.map{it.videoId}.toSet()
        val additions=songs.distinctBy{it.videoId}.filter{it.videoId !in before}
        for(song in additions)call(owner,id,"add",buildJsonObject {
            put("operation_id",Uuid.random().toString());put("video_id",song.videoId);put("title",song.title)
            put("artist",song.artistName.orEmpty().joinToString(", "));put("duration",song.durationSeconds)
            put("thumbnail_url",song.thumbnails?.let(::JsonPrimitive)?:JsonNull)
        })
        refresh(owner,id)
        return additions.size
    }
    suspend fun remove(owner:String,id:String,videoId:String):Boolean {
        val song=json.decodeFromJsonElement<CanonicalDetail>(call(owner,id,"detail")).items.find{it.videoId==videoId}?:return false
        call(owner,id,"remove",buildJsonObject {put("operation_id",Uuid.random().toString());put("item_id",song.id)})
        refresh(owner,id);return true
    }
    suspend fun move(owner:String,id:String,videoId:String,to:Int) {
        val p=json.decodeFromJsonElement<CanonicalDetail>(call(owner,id,"detail"))
        val items=p.items.toMutableList();val from=items.indexOfFirst{it.videoId==videoId}
        require(from>=0&&to in items.indices) { "Playlist changed; reload before moving" }
        items.add(to,items.removeAt(from))
        call(owner,id,"reorder",buildJsonObject {put("operation_id",Uuid.random().toString());put("revision",p.revision);put("ids",JsonArray(items.map{JsonPrimitive(it.id)}))})
        refresh(owner,id)
    }
    suspend fun deleteOrLeave(owner:String,id:String):String {
        val detail=call(owner,id,"detail").jsonObject
        val action=if(detail["owner_id"]?.jsonPrimitive?.content==owner)"delete" else "leave"
        call(owner,id,action);return action
    }
}

private fun SupabaseClient.storageUrl(path:String)=storage.from("workspace-covers").publicUrl(path)
