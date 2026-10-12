package com.tan.data.repository

import com.tan.domain.utils.Resource
import com.tan.kotlinytmusicscraper.pages.SearchResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class ProgressiveSearchTest {
    private fun page(cursor: String? = null) = SearchResult(emptyList(), emptyList(), cursor)
    @Test fun firstPageAppearsBeforeContinuationFinishesAndSnapshotsAccumulate(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>(); val seen = mutableListOf<List<String>>()
        var parses = 0
        val job = launch { progressiveSearch({ page("next") }, { gate.await(); page() },
            { if (parses++ == 0) listOf("a", "b", "") else listOf("b", "c") }, { it })
            .collect { seen += it.data.orEmpty().toList() } }
        withTimeout(3000) { while (seen.isEmpty()) delay(10) }
        assertEquals(listOf(listOf("a", "b")), seen); gate.complete(Unit); job.join()
        assertEquals(listOf("a", "b", "c"), seen.last()); assertEquals(listOf("a", "b"), seen.first())
    }
    @Test fun cyclicCursorsStopAndRepeatedRowsAreDeduplicated(): Unit = runBlocking {
        var calls = 0
        val result = progressiveSearch({ page("same") }, { calls++; page("same") }, { listOf("a", "a") }, { it }).toList()
        assertEquals(1, calls); assertEquals(2, result.size); assertEquals(listOf("a"), result.last().data?.toList())
    }
    @Test fun atMostTwoContinuationRequestsAreMade(): Unit = runBlocking {
        var calls = 0
        val result = progressiveSearch({ page("0") }, { page((++calls).toString()) }, { listOf(it.continuation!!) }, { it }).toList()
        assertEquals(2, calls); assertEquals(listOf("0", "1", "2"), result.last().data?.toList())
    }
    @Test fun continuationFailureKeepsTheAlreadyEmittedPage(): Unit = runBlocking {
        val result = progressiveSearch({ page("next") }, { error("offline") }, { listOf("a") }, { it }).toList()
        assertEquals(listOf("a"), result.first().data?.toList()); assertTrue(result.last() is Resource.Error)
    }
    @Test fun initialFailureIsAnErrorAndEmptySuccessIsDistinct(): Unit = runBlocking {
        val failed = progressiveSearch({ error("offline") }, { page() }, { emptyList<String>() }, { it }).toList()
        assertTrue(failed.single() is Resource.Error)
        val empty = progressiveSearch({ page() }, { page() }, { emptyList<String>() }, { it }).toList()
        assertTrue(empty.single() is Resource.Success); assertTrue(empty.single().data!!.isEmpty())
    }
    @Test fun cancellationIsNeverConvertedIntoAnError(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>(); val seen = mutableListOf<Resource<ArrayList<String>>>()
        val job = launch { progressiveSearch({ page("next") }, { started.complete(Unit); awaitCancellation() }, { listOf("a") }, { it }).collect { seen += it } }
        started.await(); job.cancelAndJoin(); assertTrue(seen.all { it is Resource.Success })
    }
}
