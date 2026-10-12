package com.tan.data.repository

import com.tan.data.db.datasource.LocalDataSource
import com.tan.data.mapping.toDomainSearchSuggestions
import com.tan.data.parser.parsePodcast
import com.tan.data.parser.search.*
import com.tan.domain.data.entities.SearchHistory
import com.tan.domain.data.model.searchResult.SearchSuggestions
import com.tan.domain.repository.SearchRepository
import com.tan.domain.utils.Resource
import com.tan.kotlinytmusicscraper.YouTube
import com.tan.kotlinytmusicscraper.pages.SearchResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Emit the first page immediately, preserve cumulative results and stop cyclic cursors. */
internal fun <T> progressiveSearch(
    initial: suspend () -> SearchResult, next: suspend (String) -> SearchResult,
    parse: (SearchResult) -> List<T>, identity: (T) -> String,
): Flow<Resource<ArrayList<T>>> = flow<Resource<ArrayList<T>>> {
    var page = initial()
    val rows = linkedMapOf<String, T>()
    val cursors = mutableSetOf<String>()
    repeat(3) { index ->
        currentCoroutineContext().ensureActive()
        parse(page).forEach { item -> identity(item).takeIf { it.isNotBlank() }?.let { rows[it] = item } }
        emit(Resource.Success(ArrayList(rows.values)))
        val cursor = page.continuation?.takeIf { it.isNotBlank() && cursors.add(it) } ?: return@flow
        if (index < 2) page = next(cursor)
    }
}.catch { error ->
    if (error is CancellationException) throw error
    emit(Resource.Error("Search unavailable"))
}.flowOn(Dispatchers.IO)

internal class SearchRepositoryImpl(private val localDataSource: LocalDataSource, private val youTube: YouTube) : SearchRepository {
    override fun getSearchHistory() = localDataSource.observeSearchHistory().flowOn(Dispatchers.IO)
    override fun insertSearchHistory(searchHistory: SearchHistory) = flow { emit(localDataSource.insertSearchHistory(searchHistory)) }.flowOn(Dispatchers.IO)
    override fun insertSearchHistoryForOwner(searchHistory: SearchHistory, owner: String?) = flow {
        emit(localDataSource.insertOwnedSearchHistory(owner, searchHistory))
    }.flowOn(Dispatchers.IO)
    override suspend fun deleteSearchHistory() = withContext(Dispatchers.IO) { localDataSource.deleteSearchHistory() }
    override suspend fun deleteSearchHistoryForOwner(owner: String?, query: String?) = withContext(Dispatchers.IO) {
        localDataSource.deleteOwnedSearchHistory(owner, query)
    }
    private fun <T> pages(query: String, filter: YouTube.SearchFilter, parse: (SearchResult) -> List<T>, identity: (T) -> String) =
        progressiveSearch({ youTube.search(query, filter).getOrThrow() }, { youTube.searchContinuation(it).getOrThrow() }, parse, identity)

    override fun getSearchDataSong(query: String) = pages(query, YouTube.SearchFilter.FILTER_SONG, ::parseSearchSong) { it.videoId }
    override fun getSearchDataVideo(query: String) = pages(query, YouTube.SearchFilter.FILTER_VIDEO, ::parseSearchVideo) { it.videoId }
    override fun getSearchDataAlbum(query: String) = pages(query, YouTube.SearchFilter.FILTER_ALBUM, ::parseSearchAlbum) { it.browseId }
    override fun getSearchDataArtist(query: String) = pages(query, YouTube.SearchFilter.FILTER_ARTIST, ::parseSearchArtist) { it.browseId }
    override fun getSearchDataPlaylist(query: String) = pages(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST, ::parseSearchPlaylist) { it.browseId }
    override fun getSearchDataFeaturedPlaylist(query: String) = pages(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST, ::parseSearchPlaylist) { it.browseId }
    override fun getSearchDataPodcast(query: String) = pages(query, YouTube.SearchFilter.FILTER_PODCAST, { parsePodcast(it.listPodcast) }) { it.browseId }
    override fun getSuggestQuery(query: String) = flow<Resource<SearchSuggestions>> {
        val suggestions = youTube.getYTMusicSearchSuggestions(query).getOrThrow()
        currentCoroutineContext().ensureActive()
        emit(Resource.Success(suggestions.toDomainSearchSuggestions()))
    }.catch { error -> if (error is CancellationException) throw error; emit(Resource.Error("Suggestions unavailable")) }.flowOn(Dispatchers.IO)
}
