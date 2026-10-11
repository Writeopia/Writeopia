package io.writeopia.auth.email

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmailConfirmationViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var authApi: AuthApi

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authApi = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.registrationViewModel(email: String) =
        EmailConfirmationViewModel(authRepository, authApi, EmailConfirmationPurpose.REGISTRATION).also {
            it.canSendCode.launchIn(backgroundScope)
            it.emailChanged(email)
            advanceUntilIdle()
        }

    @Test
    fun `registration sends the code to the normalized email`() = runTest {
        coEvery { authApi.sendRegisterCode(any()) } returns ResultData.Complete(true)
        var codeSent = false

        val viewModel = registrationViewModel(" Test@Example.com ")
        viewModel.onSendCode { codeSent = true }
        advanceUntilIdle()

        coVerify { authApi.sendRegisterCode("test@example.com") }
        assertTrue(codeSent)
    }

    @Test
    fun `registration does not send a code to an invalid email`() = runTest {
        var codeSent = false

        val viewModel = registrationViewModel("not-an-email")
        viewModel.onSendCode { codeSent = true }
        advanceUntilIdle()

        coVerify(exactly = 0) { authApi.sendRegisterCode(any()) }
        assertFalse(codeSent)
    }

    @Test
    fun `registration stays on the email step when the code can't be sent`() = runTest {
        coEvery { authApi.sendRegisterCode(any()) } returns ResultData.Error(Exception("boom"))
        var codeSent = false

        val viewModel = registrationViewModel("test@example.com")
        viewModel.onSendCode { codeSent = true }
        advanceUntilIdle()

        assertFalse(codeSent)
        assertIs<ResultData.Error<*>>(viewModel.sendCodeState.value)
    }

    @Test
    fun `registration verifies the code without signing in`() = runTest {
        coEvery { authApi.sendRegisterCode(any()) } returns ResultData.Complete(true)
        coEvery { authApi.verifyRegisterCode(any(), any()) } returns ResultData.Complete(true)
        var verified = false

        val viewModel = registrationViewModel("test@example.com")
        viewModel.onSendCode {}
        advanceUntilIdle()
        viewModel.codeChanged("123456")
        viewModel.onConfirm { verified = true }
        advanceUntilIdle()

        assertTrue(verified)
        // The code is kept, because registering sends it again.
        assertEquals("123456", viewModel.code.value)
        coVerify(exactly = 0) { authApi.confirmEmail(any(), any()) }
        coVerify(exactly = 0) { authRepository.saveTokens(any(), any(), any(), any()) }
    }

    @Test
    fun `registration rejects a wrong code`() = runTest {
        coEvery { authApi.verifyRegisterCode(any(), any()) } returns ResultData.Error(Exception("Invalid"))
        var verified = false

        val viewModel = registrationViewModel("test@example.com")
        viewModel.codeChanged("000000")
        viewModel.onConfirm { verified = true }
        advanceUntilIdle()

        assertFalse(verified)
        assertIs<ResultData.Error<*>>(viewModel.confirmState.value)
    }

    @Test
    fun `registration resends through the sign-up endpoint`() = runTest {
        coEvery { authApi.sendRegisterCode(any()) } returns ResultData.Complete(true)

        val viewModel = registrationViewModel("test@example.com")
        viewModel.onResend()
        advanceUntilIdle()

        coVerify { authApi.sendRegisterCode("test@example.com") }
        coVerify(exactly = 0) { authApi.resendConfirmationEmail(any()) }
    }

    @Test
    fun `pending accounts still confirm through the old endpoint`() = runTest {
        coEvery { authRepository.getPendingConfirmationEmail() } returns "old@example.com"
        coEvery { authApi.confirmEmail(any(), any()) } returns ResultData.Error(Exception("Invalid"))

        val viewModel = EmailConfirmationViewModel(authRepository, authApi)
        viewModel.loadPendingEmail()
        advanceUntilIdle()
        viewModel.codeChanged("123456")
        viewModel.onConfirm {}
        advanceUntilIdle()

        coVerify { authApi.confirmEmail("old@example.com", "123456") }
        coVerify(exactly = 0) { authApi.verifyRegisterCode(any(), any()) }
    }
}
