package com.tan.domain.utils

/** A recorded music play starts at 30 seconds of elapsed listening, independent of track length. */
fun shouldRecordListening(elapsedMillis: Long): Boolean = elapsedMillis >= 30_000
