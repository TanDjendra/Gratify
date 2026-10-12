package com.tan.gratify

import com.tan.gratify.viewModel.auth.ProfileLoginReturn
import kotlin.test.*

class ProfileLoginReturnTest {
    private val id = "67ce1d48-ef0f-42e7-82dc-c5668b330ae4"
    @AfterTest fun clear() { ProfileLoginReturn.clear() }
    @Test fun loginReturnsToTheRequestedAccountOnce() {
        ProfileLoginReturn.request(id, 1000)
        assertEquals(id, ProfileLoginReturn.consume(true, 2000))
        assertNull(ProfileLoginReturn.consume(true, 2000))
    }
    @Test fun continuingAsGuestClearsTheReturnDestination() {
        ProfileLoginReturn.request(id, 1000)
        assertNull(ProfileLoginReturn.consume(false, 2000))
        assertNull(ProfileLoginReturn.consume(true, 2000))
    }
    @Test fun expiredAndFutureDestinationsCannotCauseLaterNavigation() {
        ProfileLoginReturn.request(id, 1000); assertNull(ProfileLoginReturn.consume(true, 602000))
        ProfileLoginReturn.request(id, 2000); assertNull(ProfileLoginReturn.consume(true, 1000))
    }
    @Test fun arbitraryTextCannotBecomeAPostLoginRoute() {
        ProfileLoginReturn.request("https://evil.invalid", 1000)
        assertNull(ProfileLoginReturn.consume(true, 2000))
    }
}
