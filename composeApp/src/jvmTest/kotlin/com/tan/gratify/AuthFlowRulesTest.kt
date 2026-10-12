package com.tan.gratify.viewModel.auth

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowRulesTest {
    @Test fun signupRequiresMatchingPasswordNameAndConsent() {
        val complete = SignUpFormState(email = "synthetic@example.invalid", isEmailValid = true, password = "synthetic-password", isPasswordValid = true,
            confirmPassword = "synthetic-password", isConfirmPasswordMatch = true, name = "Synthetic", isNameValid = true, isTermsAccepted = true)
        assertTrue(complete.isSignUpReady())
        assertFalse(complete.copy(isTermsAccepted = false).isSignUpReady())
        assertFalse(complete.copy(isConfirmPasswordMatch = false).isSignUpReady())
        assertFalse(complete.copy(isNameValid = false).isSignUpReady())
    }
    @Test fun credentialsAndPastedCodesAreNormalizedWithoutChangingPasswords() {
        assertEquals("Synthetic+music@example.invalid", normalizeAuthEmail("  Synthetic+music@example.invalid \n"))
        assertEquals("123456", normalizeOtpCode("123 456\n"))
        assertFalse(OtpFormState(code = "١٢٣٤٥٦").isCodeComplete())
        assertTrue(OtpFormState(code = "123456").isCodeComplete())
        assertFalse(isValidNewPassword("1234567"))
        assertTrue(isValidNewPassword("a long memorable phrase"))
        assertEquals(PasswordStrength.STRONG, evaluatePasswordStrength("a long memorable phrase"))
        assertTrue(LoginFormState(isEmailValid = true, password = "short", isPasswordValid = true).isValid())
    }
    @Test fun rapidRequestsAreRejectedAndCancellationAllowsAnotherAttempt() = runBlocking {
        val gate = AuthRequestGate(this)
        var calls = 0
        assertTrue(gate.launch { calls++; awaitCancellation() })
        repeat(100) { assertFalse(gate.launch { calls++ }) }
        assertEquals(1, calls)
        gate.cancel(); withTimeout(5_000) { while (gate.busy) yield() }
        assertFalse(gate.busy)
        assertTrue(gate.launch { calls++ })
        assertEquals(2, calls)
    }
    @Test fun resendCountdownStartsImmediatelyAndCompletesAfterSixtySeconds() = runBlocking {
        var seconds = -1
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        val cooldown = AuthResendCooldown(this, tick = { ticks.receive(); Unit }) { seconds = it }
        cooldown.start(); assertEquals(60, seconds)
        repeat(30) { ticks.send(Unit) }; withTimeout(5_000) { while (seconds != 30) yield() }
        cooldown.start(); assertEquals(60, seconds)
        repeat(60) { ticks.send(Unit) }; withTimeout(5_000) { while (seconds != 0) yield() }
    }
}
