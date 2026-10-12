package com.tan.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*

/** One bounded request for covers, with server-side visibility checks per playlist. */
internal suspend fun playlistArtwork(client:SupabaseClient,ids:List<String>):Map<String,List<String>> {
    val selected=ids.filter{it.matches(Regex("[a-fA-F0-9-]{36}"))}.distinct().take(100)
    if(selected.isEmpty())return emptyMap()
    return try {
        withTimeout(5_000) {
            client.postgrest.rpc("gratify_workspace",buildJsonObject {
                put("p_action","shared_artwork");put("p_args",buildJsonObject{put("ids",JsonArray(selected.map(::JsonPrimitive)))})
            }).decodeAs<JsonObject>().mapValues{(_,value)->value.jsonArray.map{it.jsonPrimitive.content}}
        }
    }catch(e:TimeoutCancellationException){emptyMap()}
    catch(e:CancellationException){throw e}
    catch(_:Exception){emptyMap()}
}
