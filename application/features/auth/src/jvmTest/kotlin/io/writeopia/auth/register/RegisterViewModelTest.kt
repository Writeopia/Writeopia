package io.writeopia.auth.register

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.data.WriteopiaUserApi
import io.writeopia.sdk.serialization.data.auth.RegisterResponse
import io.writeopia.sdk.serialization.response.LegalDocumentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
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
class RegisterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var authApi: AuthApi

    private val registerResponse = RegisterResponse(
        writeopiaUser = WriteopiaUserApi(
            id = "user-123",
            name = "Test User",
            email = "test@example.com"
        ),
        emailConfirmationRequired = false,
        accessToken = "access",
        refreshToken = "refresh",
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authApi = mockk(relaxed = true)
        coEvery { authApi.isUsernameAvailable(any()) } returns ResultData.Complete(true)
        coEvery { authApi.legalDocument(any()) } answers {
            ResultData.Complete(LegalDocumentResponse(firstArg(), "v1", "2026-11-12", "Doc", emptyList()))
        }
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun filledViewModel() = RegisterViewModel(authRepository, authApi).apply {
        setVerifiedEmail("test@example.com", "123456")
        nameChanged("Test User")
        usernameChanged("testuser")
        workspaceChanged("My Workspace")
        passwordChanged("password123")
        termsAcceptedChanged(true)
    }

    @Test
    fun `onRegister should send the verification code and sign the new user in`() = runTest {
        coEvery { authApi.register(any(), any(), any(), any(), any(), any(), any()) } returns
            ResultData.Complete(registerResponse)

        val viewModel = filledViewModel()
        viewModel.onRegister()
        advanceUntilIdle()

        coVerify {
            authApi.register(
                name = "Test User",
                email = "test@example.com",
                workspaceName = "My Workspace",
                password = "password123",
                username = "testuser",
                verificationCode = "123456",
                acceptedLegalVersion = "v1"
            )
        }
        coVerify { authRepository.saveUser(any(), selected = true) }
        coVerify {
            authRepository.saveTokens(
                userId = "user-123",
                accessToken = "access",
                refreshToken = "refresh",
                expiresAt = any()
            )
        }
        assertEquals(ResultData.Complete(true), viewModel.register.value)
    }

    @Test
    fun `on web onRegister should sign in with cookies instead of saving tokens`() = runTest {
        every { authRepository.useWebLogin } returns true
        coEvery { authApi.register(any(), any(), any(), any(), any(), any(), any()) } returns
            ResultData.Complete(registerResponse)

        val viewModel = filledViewModel()
        viewModel.onRegister()
        advanceUntilIdle()

        coVerify { authApi.loginWeb("test@example.com", "password123") }
        coVerify(exactly = 0) { authRepository.saveTokens(any(), any(), any(), any()) }
    }

    @Test
    fun `onRegister should report a registration error`() = runTest {
        coEvery { authApi.register(any(), any(), any(), any(), any(), any(), any()) } returns
            ResultData.Error(Exception("Registration failed"))

        val viewModel = filledViewModel()
        viewModel.onRegister()
        advanceUntilIdle()

        assertIs<ResultData.Error<*>>(viewModel.register.value)
        coVerify(exactly = 0) { authRepository.saveUser(any(), any()) }
    }

    @Test
    fun `onRegister should handle registration error gracefully`() = runTest {
        coEvery { authApi.register(any(), any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("Network error")

        val viewModel = filledViewModel()
        viewModel.onRegister()
        advanceUntilIdle()

        assertIs<ResultData.Error<*>>(viewModel.register.value)
    }

    @Test
    fun `a taken username is flagged and blocks registering`() = runTest {
        coEvery { authApi.isUsernameAvailable("taken") } returns ResultData.Complete(false)

        val viewModel = filledViewModel()
        viewModel.canRegister.launchIn(backgroundScope)
        viewModel.usernameChanged("taken")
        advanceUntilIdle()

        assertEquals(UsernameAvailability.TAKEN, viewModel.usernameAvailability.value)
        assertFalse(viewModel.canRegister.value)
    }

    @Test
    fun `a free username can be registered`() = runTest {
        val viewModel = filledViewModel()
        viewModel.passwordChanged("password123!")
        viewModel.canRegister.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(UsernameAvailability.AVAILABLE, viewModel.usernameAvailability.value)
        assertTrue(viewModel.canRegister.value)
    }

    @Test
    fun `only the username typed last is checked`() = runTest {
        val viewModel = RegisterViewModel(authRepository, authApi)
        viewModel.usernameChanged("ana")
        viewModel.usernameChanged("ana_b")
        viewModel.usernameChanged("ana_bo")
        advanceUntilIdle()

        coVerify(exactly = 1) { authApi.isUsernameAvailable(any()) }
        coVerify { authApi.isUsernameAvailable("ana_bo") }
    }

    @Test
    fun `an invalid username is not checked`() = runTest {
        val viewModel = RegisterViewModel(authRepository, authApi)
        viewModel.usernameChanged("ab")
        advanceUntilIdle()

        coVerify(exactly = 0) { authApi.isUsernameAvailable(any()) }
        assertEquals(UsernameAvailability.UNKNOWN, viewModel.usernameAvailability.value)
    }

    @Test
    fun `a failed check does not block registering`() = runTest {
        coEvery { authApi.isUsernameAvailable(any()) } returns ResultData.Error(Exception("offline"))

        val viewModel = filledViewModel()
        viewModel.passwordChanged("password123!")
        viewModel.canRegister.launchIn(backgroundScope)
        advanceUntilIdle()

        assertEquals(UsernameAvailability.UNKNOWN, viewModel.usernameAvailability.value)
        assertTrue(viewModel.canRegister.value)
    }

    @Test
    fun `registering needs the terms to be accepted`() = runTest {
        val viewModel = filledViewModel()
        viewModel.passwordChanged("password123!")
        viewModel.canRegister.launchIn(backgroundScope)
        advanceUntilIdle()
        assertTrue(viewModel.canRegister.value)

        viewModel.termsAcceptedChanged(false)
        advanceUntilIdle()

        assertFalse(viewModel.canRegister.value)
    }

    @Test
    fun `opening a legal document loads it and closing hides it`() = runTest {
        val terms = LegalDocumentResponse("terms", "v1", "2026-11-12", "Terms of Service", emptyList())
        coEvery { authApi.legalDocument("terms") } returns ResultData.Complete(terms)
        val viewModel = RegisterViewModel(authRepository, authApi)

        viewModel.openLegalDocument("terms")
        assertEquals(LegalDocumentState.Loading("terms"), viewModel.legalDocument.value)
        advanceUntilIdle()
        assertEquals(LegalDocumentState.Loaded(terms), viewModel.legalDocument.value)

        viewModel.closeLegalDocument()
        assertEquals(LegalDocumentState.Hidden, viewModel.legalDocument.value)
    }

    @Test
    fun `a legal document that fails to load can be tried again`() = runTest {
        val privacy = LegalDocumentResponse("privacy", "v1", "2026-11-12", "Privacy Policy", emptyList())
        coEvery { authApi.legalDocument("privacy") } returnsMany listOf(
            ResultData.Error(Exception("offline")),
            ResultData.Complete(privacy)
        )
        val viewModel = RegisterViewModel(authRepository, authApi)

        viewModel.openLegalDocument("privacy")
        advanceUntilIdle()
        assertEquals(LegalDocumentState.Failed("privacy"), viewModel.legalDocument.value)

        viewModel.openLegalDocument("privacy")
        advanceUntilIdle()
        assertEquals(LegalDocumentState.Loaded(privacy), viewModel.legalDocument.value)
    }

    @Test
    fun `registering does not call the backend when the legal version cannot be loaded`() = runTest {
        coEvery { authApi.legalDocument(any()) } returns ResultData.Error(Exception("offline"))

        val viewModel = filledViewModel()
        viewModel.onRegister()
        advanceUntilIdle()

        assertIs<ResultData.Error<*>>(viewModel.register.value)
        coVerify(exactly = 0) { authApi.register(any(), any(), any(), any(), any(), any(), any()) }
    }
}
