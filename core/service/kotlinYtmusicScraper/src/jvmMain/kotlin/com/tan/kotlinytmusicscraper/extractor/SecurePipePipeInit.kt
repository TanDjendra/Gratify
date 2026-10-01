package com.tan.kotlinytmusicscraper.extractor

import dev.maxrave.pipepipe.extractor.NewPipe
import dev.maxrave.pipepipe.extractor.downloader.Downloader
import dev.maxrave.pipepipe.extractor.localization.Localization
import dev.maxrave.pipepipe.extractor.localization.ContentCountry

/** Adapter for pinned c3139c584d: its public init changes process TLS to trust-all.
 * Never fall back to that init if the dependency's private layout changes.
 */
internal fun initializePipePipeSecurely(downloader: Downloader) {
    synchronized(NewPipe::class.java) {
        val field = NewPipe::class.java.getDeclaredField("downloader")
        field.isAccessible = true
        field.set(null, downloader)
        NewPipe.setupLocalization(Localization.DEFAULT, ContentCountry.DEFAULT)
        check(NewPipe.getDownloader() === downloader)
    }
}
