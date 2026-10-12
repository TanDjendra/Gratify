package com.tan.gratify.viewModel

import kotlin.test.*

class FriendsServiceErrorTest {
    @Test fun missingBackendIsExplainedInsteadOfMisreportedAsAConnectionFailure() {
        val missing = IllegalStateException("PGRST202: Could not find the function public.gratify_dm_inbox")
        assertTrue(friendsBackendMissing(missing))
        assertEquals("Story dan pesan belum aktif. Coba lagi setelah layanan diperbarui.", friendsFailureMessage(missing, "Connection failed"))
        assertTrue(friendsBackendMissing(IllegalStateException("Request failed", missing)))
    }
    @Test fun ordinaryFailuresKeepTheActionSpecificRecoveryMessage() {
        val unavailable = IllegalStateException("Network unreachable")
        assertFalse(friendsBackendMissing(unavailable))
        assertEquals("Coba lagi", friendsFailureMessage(unavailable, "Coba lagi"))
    }
}
