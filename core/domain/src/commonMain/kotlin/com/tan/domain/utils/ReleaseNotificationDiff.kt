package com.tan.domain.utils

/** A first successful scan establishes a baseline, including an empty baseline. */
fun newReleaseIds(previous: List<String>?, current: List<String>): Set<String> =
    if (previous == null) emptySet() else current.filter { it.isNotBlank() }.toSet() - previous.toSet()
