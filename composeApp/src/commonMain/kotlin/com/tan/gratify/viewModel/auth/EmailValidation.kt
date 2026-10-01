package com.tan.gratify.viewModel.auth

private val emailPattern =
    "^[A-Za-z0-9._%+-]+@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}$".toRegex()

internal fun isValidEmailAddress(email: String): Boolean {
    val local = email.substringBefore('@')
    return email.length <= 254 && local.length in 1..64 &&
        !local.startsWith('.') && !local.endsWith('.') && !local.contains("..") &&
        email.matches(emailPattern)
}
