package com.tan.gratify.viewModel.auth

import com.tan.domain.repository.YOUTUBE_READ_SCOPE
import io.github.jan.supabase.auth.providers.ExternalAuthConfigDefaults

/** The choice belongs to this login attempt; basic login never needs collection access. */
fun ExternalAuthConfigDefaults.configureGoogleLogin(connectYouTube: Boolean) {
    if (connectYouTube) withYouTubeReadAccess()
}

/** Reading collections is the only YouTube permission requested. No edit/upload scope. */
fun ExternalAuthConfigDefaults.withYouTubeReadAccess() {
    scopes.add(YOUTUBE_READ_SCOPE)
    queryParams["access_type"] = "offline"
    queryParams["prompt"] = "consent"
    queryParams["include_granted_scopes"] = "true"
}
