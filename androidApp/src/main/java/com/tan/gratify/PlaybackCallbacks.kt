package com.tan.gratify

import com.tan.domain.mediaservice.handler.MediaPlayerHandler
import com.tan.domain.mediaservice.handler.ToastType
import com.tan.gratify.crashlytics.pushPlayerError
import com.tan.gratify.utils.ComposeResUtils
import com.tan.gratify.viewModel.SharedViewModel
import kotlinx.coroutines.runBlocking

/**
 * The handler and SharedViewModel are application-scoped. These callbacks deliberately
 * survive unbinding/recreation; constructing them outside an Activity prevents an
 * implicit Activity receiver from being retained by the shared handler.
 */
internal fun installPlaybackCallbacks(
    mediaPlayerHandler: MediaPlayerHandler,
    sharedViewModel: SharedViewModel,
) {
    mediaPlayerHandler.pushPlayerError = { error -> pushPlayerError(error) }
    mediaPlayerHandler.showToast = { type ->
        sharedViewModel.makeToast(
            when (type) {
                is ToastType.ExplicitContent -> {
                    runBlocking { ComposeResUtils.getResString(ComposeResUtils.StringType.EXPLICIT_CONTENT_BLOCKED) }
                }

                is ToastType.PlayerError -> {
                    runBlocking { ComposeResUtils.getResString(ComposeResUtils.StringType.TIME_OUT_ERROR, type.error) }
                }
            },
        )
    }
}
