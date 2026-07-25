package com.tan.gratify.nowplayingcenter.domain

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import java.io.File

internal class ImageLoader {
    private val client: HttpClient = createClient()

    private fun createClient(): HttpClient {
        return HttpClient(CIO)
    }

    suspend fun loadImage(url: String): File {
        return client.get(url).bodyAsBytes().let { bytes ->
            val tempFile = File.createTempFile("nowplaying_artwork_", null)
            tempFile.writeBytes(bytes)
            tempFile
        }
    }
}
