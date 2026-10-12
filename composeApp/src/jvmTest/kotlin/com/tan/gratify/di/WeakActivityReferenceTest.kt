package com.tan.gratify.di

import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class WeakActivityReferenceTest {
    // Equality is deliberately not identity: old-owner cleanup must use ===.
    private data class Host(val id: Int = 1, var finishing: Boolean = false, var destroyed: Boolean = false)

    private fun reference() = WeakActivityReference<Host> { !it.finishing && !it.destroyed }

    @Test
    fun lookupIsEmptyBeforeAttachAndAfterOwnerDestroy() {
        val reference = reference()
        assertNull(reference.currentOrNull())
        val host = Host()
        reference.attach(host)
        assertSame(host, reference.currentOrNull())
        reference.detach(host)
        assertNull(reference.currentOrNull())
    }

    @Test
    fun destroyingPreviousEqualOwnerCannotClearReplacement() {
        val reference = reference()
        val old = Host()
        val replacement = Host()
        reference.attach(old)
        reference.attach(replacement)
        reference.detach(old)
        assertSame(replacement, reference.currentOrNull())
        reference.detach(replacement)
        assertNull(reference.currentOrNull())
    }

    @Test
    fun finishingOrDestroyedHostIsNotReturned() {
        val reference = reference()
        val host = Host()
        reference.attach(host)
        host.finishing = true
        assertNull(reference.currentOrNull())
        host.finishing = false
        host.destroyed = true
        assertNull(reference.currentOrNull())
    }

    @Test
    fun temporaryExternalUiDoesNotDetachHost() {
        val reference = reference()
        val host = Host()
        reference.attach(host)
        // No detach on pause/stop: an external chooser or permission UI may be open.
        repeat(3) { assertSame(host, reference.currentOrNull()) }
    }

    @Test
    fun clearedWeakReferentBehavesLikeAbsentHostWithoutGcTiming() {
        val reference = reference()
        reference.attach(Host())
        val field = reference.javaClass.getDeclaredField("activity").apply { isAccessible = true }
        (field.get(reference) as WeakReference<*>).clear()
        assertNull(reference.currentOrNull())
    }

    @Test
    fun koinFactoryResolvesReplacementWithoutResettingSharedState() {
        val reference = reference()
        val shared = Any()
        val activityModule = module { factory<Host> { checkNotNull(reference.currentOrNull()) } }
        val app = koinApplication { modules(module { single { shared } }, activityModule) }
        try {
            assertFails { app.koin.get<Host>() }
            val first = Host()
            reference.attach(first)
            assertSame(first, app.koin.get<Host>())
            val replacement = Host()
            reference.attach(replacement)
            // Same static module is loaded on each MainActivity creation.
            app.koin.loadModules(listOf(activityModule))
            reference.detach(first)
            assertSame(replacement, app.koin.get<Host>())
            assertSame(shared, app.koin.get<Any>())
            reference.detach(replacement)
            assertFails { app.koin.get<Host>() }
        } finally {
            app.close()
        }
    }
}
