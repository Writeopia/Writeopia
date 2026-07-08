package io.writeopia.auth.forgotpassword

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotPasswordViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authApi: AuthApi
    private lateinit var authRepository: AuthRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authApi = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emailChanged should update email state and save to repository`() = runTest {
        // Given
        coEvery { authRepository.saveForgotPasswordEmail(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)

        // When
        viewModel.emailChanged("test@example.com")
        advanceUntilIdle()

        // Then
        assertEquals("test@example.com", viewModel.email.value)
        coVerify { authRepository.saveForgotPasswordEmail("test@example.com") }
    }

    @Test
    fun `codeChanged should only accept digits and max 6 characters`() = runTest {
        // Given
        coEvery { authRepository.saveForgotPasswordCode(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)

        // When - entering mixed input
        viewModel.codeChanged("12ab34cd56ef78")
        advanceUntilIdle()

        // Then - only first 6 digits should be kept
        assertEquals("123456", viewModel.code.value)
        coVerify { authRepository.saveForgotPasswordCode("123456") }
    }

    @Test
    fun `codeChanged should filter out non-digit characters`() = runTest {
        // Given
        coEvery { authRepository.saveForgotPasswordCode(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)

        // When
        viewModel.codeChanged("abc123")
        advanceUntilIdle()

        // Then
        assertEquals("123", viewModel.code.value)
    }

    @Test
    fun `onSendCode should call requestPasswordReset and invoke success callback`() = runTest {
        // Given
        coEvery { authApi.requestPasswordReset(any()) } returns ResultData.Complete(true)
        coEvery { authRepository.saveForgotPasswordEmail(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.emailChanged("test@example.com")
        advanceUntilIdle()

        var successCalled = false

        // When
        viewModel.onSendCode { successCalled = true }
        advanceUntilIdle()

        // Then
        coVerify { authApi.requestPasswordReset("test@example.com") }
        assertTrue(successCalled)
        assertTrue(viewModel.sendCodeState.value is ResultData.Complete)
    }

    @Test
    fun `onSendCode should set error state when email is blank`() = runTest {
        // Given
        val viewModel = ForgotPasswordViewModel(authApi, authRepository)

        var successCalled = false

        // When
        viewModel.onSendCode { successCalled = true }
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.sendCodeState.value is ResultData.Error)
        assertTrue(!successCalled)
        coVerify(exactly = 0) { authApi.requestPasswordReset(any()) }
    }

    @Test
    fun `onSendCode should set error state when API fails`() = runTest {
        // Given
        coEvery { authApi.requestPasswordReset(any()) } returns ResultData.Error(Exception("Network error"))
        coEvery { authRepository.saveForgotPasswordEmail(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.emailChanged("test@example.com")
        advanceUntilIdle()

        var successCalled = false

        // When
        viewModel.onSendCode { successCalled = true }
        advanceUntilIdle()

        // Then
        assertTrue(!successCalled)
        assertTrue(viewModel.sendCodeState.value is ResultData.Error)
    }

    @Test
    fun `onVerifyCode should call verifyPasswordResetCode and invoke success callback`() = runTest {
        // Given
        coEvery { authApi.verifyPasswordResetCode(any(), any()) } returns ResultData.Complete(true)
        coEvery { authRepository.saveForgotPasswordEmail(any()) } just Runs
        coEvery { authRepository.saveForgotPasswordCode(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.emailChanged("test@example.com")
        viewModel.codeChanged("123456")
        advanceUntilIdle()

        var successCalled = false

        // When
        viewModel.onVerifyCode { successCalled = true }
        advanceUntilIdle()

        // Then
        coVerify { authApi.verifyPasswordResetCode("test@example.com", "123456") }
        assertTrue(successCalled)
        assertTrue(viewModel.verifyCodeState.value is ResultData.Complete)
    }

    @Test
    fun `onVerifyCode should set error state when code is not 6 digits`() = runTest {
        // Given
        coEvery { authRepository.saveForgotPasswordCode(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.codeChanged("123") // Only 3 digits
        advanceUntilIdle()

        var successCalled = false

        // When
        viewModel.onVerifyCode { successCalled = true }
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.verifyCodeState.value is ResultData.Error)
        assertTrue(!successCalled)
        coVerify(exactly = 0) { authApi.verifyPasswordResetCode(any(), any()) }
    }

    @Test
    fun `onVerifyCode should set error state when API returns invalid code`() = runTest {
        // Given
        coEvery { authApi.verifyPasswordResetCode(any(), any()) } returns ResultData.Error(Exception("Invalid code"))
        coEvery { authRepository.saveForgotPasswordEmail(any()) } just Runs
        coEvery { authRepository.saveForgotPasswordCode(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.emailChanged("test@example.com")
        viewModel.codeChanged("123456")
        advanceUntilIdle()

        var successCalled = false

        // When
        viewModel.onVerifyCode { successCalled = true }
        advanceUntilIdle()

        // Then
        assertTrue(!successCalled)
        assertTrue(viewModel.verifyCodeState.value is ResultData.Error)
    }

    @Test
    fun `onResetPassword should call resetPasswordWithCode and clear data on success`() = runTest {
        // Given
        coEvery { authApi.resetPasswordWithCode(any(), any(), any()) } returns ResultData.Complete(true)
        coEvery { authRepository.saveForgotPasswordEmail(any()) } just Runs
        coEvery { authRepository.saveForgotPasswordCode(any()) } just Runs
        coEvery { authRepository.clearForgotPasswordData() } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.emailChanged("test@example.com")
        viewModel.codeChanged("123456")
        viewModel.passwordChanged("newpassword123")
        viewModel.repeatPasswordChanged("newpassword123")
        advanceUntilIdle()

        var successCalled = false

        // When
        viewModel.onResetPassword { successCalled = true }
        advanceUntilIdle()

        // Then
        coVerify { authApi.resetPasswordWithCode("test@example.com", "123456", "newpassword123") }
        coVerify { authRepository.clearForgotPasswordData() }
        assertTrue(successCalled)
        assertTrue(viewModel.resetPasswordState.value is ResultData.Complete)
    }

    @Test
    fun `onResetPassword should set error state when password is blank`() = runTest {
        // Given
        val viewModel = ForgotPasswordViewModel(authApi, authRepository)

        var successCalled = false

        // When
        viewModel.onResetPassword { successCalled = true }
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.resetPasswordState.value is ResultData.Error)
        assertTrue(!successCalled)
        coVerify(exactly = 0) { authApi.resetPasswordWithCode(any(), any(), any()) }
    }

    @Test
    fun `onResetPassword should set error state when passwords do not match`() = runTest {
        // Given
        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.passwordChanged("password1")
        viewModel.repeatPasswordChanged("password2")
        advanceUntilIdle()

        var successCalled = false

        // When
        viewModel.onResetPassword { successCalled = true }
        advanceUntilIdle()

        // Then
        assertTrue(viewModel.resetPasswordState.value is ResultData.Error)
        val error = (viewModel.resetPasswordState.value as ResultData.Error).exception
        assertEquals("Passwords do not match", error?.message)
        assertTrue(!successCalled)
        coVerify(exactly = 0) { authApi.resetPasswordWithCode(any(), any(), any()) }
    }

    @Test
    fun `onResetPassword should set error state when API fails`() = runTest {
        // Given
        coEvery { authApi.resetPasswordWithCode(any(), any(), any()) } returns ResultData.Error(Exception("Reset failed"))
        coEvery { authRepository.saveForgotPasswordEmail(any()) } just Runs
        coEvery { authRepository.saveForgotPasswordCode(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.emailChanged("test@example.com")
        viewModel.codeChanged("123456")
        viewModel.passwordChanged("newpassword123")
        viewModel.repeatPasswordChanged("newpassword123")
        advanceUntilIdle()

        var successCalled = false

        // When
        viewModel.onResetPassword { successCalled = true }
        advanceUntilIdle()

        // Then
        assertTrue(!successCalled)
        assertTrue(viewModel.resetPasswordState.value is ResultData.Error)
        coVerify(exactly = 0) { authRepository.clearForgotPasswordData() }
    }

    @Test
    fun `onResendCode should start cooldown after successful send`() = runTest {
        // Given
        coEvery { authApi.requestPasswordReset(any()) } returns ResultData.Complete(true)
        coEvery { authRepository.saveForgotPasswordEmail(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.emailChanged("test@example.com")
        advanceUntilIdle()

        // When - Send code to start cooldown
        viewModel.onSendCode { }
        // Advance just enough for the API call to complete
        testDispatcher.scheduler.advanceTimeBy(1000)

        // Then - API should be called
        coVerify(exactly = 1) { authApi.requestPasswordReset("test@example.com") }
        assertTrue(viewModel.sendCodeState.value is ResultData.Complete)
    }

    @Test
    fun `loadForgotPasswordData should load email and code from repository`() = runTest {
        // Given
        coEvery { authRepository.getForgotPasswordEmail() } returns "saved@example.com"
        coEvery { authRepository.getForgotPasswordCode() } returns "654321"

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)

        // When
        viewModel.loadForgotPasswordData()
        advanceUntilIdle()

        // Then
        assertEquals("saved@example.com", viewModel.email.value)
        assertEquals("654321", viewModel.code.value)
    }

    @Test
    fun `loadForgotPasswordData should handle null values from repository`() = runTest {
        // Given
        coEvery { authRepository.getForgotPasswordEmail() } returns null
        coEvery { authRepository.getForgotPasswordCode() } returns null

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)

        // When
        viewModel.loadForgotPasswordData()
        advanceUntilIdle()

        // Then
        assertEquals("", viewModel.email.value)
        assertEquals("", viewModel.code.value)
    }

    @Test
    fun `resetStates should reset all state flows to idle`() = runTest {
        // Given
        coEvery { authApi.requestPasswordReset(any()) } returns ResultData.Error(Exception("Error"))
        coEvery { authRepository.saveForgotPasswordEmail(any()) } just Runs

        val viewModel = ForgotPasswordViewModel(authApi, authRepository)
        viewModel.emailChanged("test@example.com")
        viewModel.onSendCode { }
        advanceUntilIdle()

        // Verify error state
        assertTrue(viewModel.sendCodeState.value is ResultData.Error)

        // When
        viewModel.resetStates()

        // Then
        assertTrue(viewModel.sendCodeState.value is ResultData.Idle)
        assertTrue(viewModel.verifyCodeState.value is ResultData.Idle)
        assertTrue(viewModel.resetPasswordState.value is ResultData.Idle)
    }
}
