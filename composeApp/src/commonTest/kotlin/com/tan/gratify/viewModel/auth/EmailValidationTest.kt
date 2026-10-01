package com.tan.gratify.viewModel.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailValidationTest {
    @Test
    fun acceptsLongDomainSuffixesAndSubdomains() {
        listOf("listener@example.technology", "audit@example.invalid", "name+music@mail.example.co.id")
            .forEach { assertTrue(isValidEmailAddress(it), it) }
    }

    @Test
    fun rejectsMalformedAddressesAndOversizedLabels() {
        listOf("name@", "name@@example.com", "name@example..com", "name@-example.com",
            "name@example-.com", ".name@example.com", "name..music@example.com",
            "name@example.com ", "${"a".repeat(65)}@example.com", "name@${"a".repeat(64)}.com")
            .forEach { assertFalse(isValidEmailAddress(it), it) }
    }
}
