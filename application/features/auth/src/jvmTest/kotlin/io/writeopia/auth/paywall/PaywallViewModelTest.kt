package io.writeopia.auth.paywall

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.response.SubscriptionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var authApi: AuthApi

    private val user = WriteopiaUser(id = "user-1", email = "a@b.c", name = "A", tier = Tier.FREE)

    private fun subscription(premium: Boolean) = ResultData.Complete(
        SubscriptionResponse(
            premium = premium,
            tier = if (premium) "PREMIUM" else "FREE",
            monthlyPriceCents = 500,
            currency = "USD"
        )
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        authApi = mockk(relaxed = true)
        coEvery { authRepository.getUser() } returns user
        coEvery { authRepository.logout() } returns ResultData.Complete(true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a premium user goes on and is stored as premium`() = runTest {
        coEvery { authApi.subscription() } returns subscription(premium = true)

        val viewModel = PaywallViewModel(authRepository, authApi)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.premium)
        coVerify { authRepository.saveUser(user.copy(tier = Tier.PREMIUM), true) }
    }

    @Test
    fun `a free user sees the paywall with the monthly price`() = runTest {
        coEvery { authApi.subscription() } returns subscription(premium = false)

        val viewModel = PaywallViewModel(authRepository, authApi)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.premium)
        assertFalse(state.checking)
        assertFalse(state.stillNotPremium)
        assertEquals("$5.00", state.monthlyPrice)
    }

    @Test
    fun `a failed check never lets the user through`() = runTest {
        coEvery { authApi.subscription() } returns ResultData.Error(Exception("offline"))

        val viewModel = PaywallViewModel(authRepository, authApi)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.failed)
        assertFalse(viewModel.state.value.premium)
    }

    @Test
    fun `checking again tells when there is still no subscription, and goes on once there is`() = runTest {
        coEvery { authApi.subscription() } returns subscription(premium = false)
        val viewModel = PaywallViewModel(authRepository, authApi)
        advanceUntilIdle()

        viewModel.checkAgain()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.stillNotPremium)

        coEvery { authApi.subscription() } returns subscription(premium = true)
        viewModel.checkAgain()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.premium)
    }

    @Test
    fun `signing out logs the online account out`() = runTest {
        coEvery { authApi.subscription() } returns subscription(premium = false)
        val viewModel = PaywallViewModel(authRepository, authApi)
        advanceUntilIdle()

        var signedOut = false
        viewModel.signOut { signedOut = true }
        advanceUntilIdle()

        assertTrue(signedOut)
        coVerify { authRepository.logout() }
    }

    @Test
    fun `a failed sign out keeps the user on the paywall`() = runTest {
        coEvery { authApi.subscription() } returns subscription(premium = false)
        coEvery { authRepository.logout() } returns ResultData.Error(Exception("disk"))
        val viewModel = PaywallViewModel(authRepository, authApi)
        advanceUntilIdle()

        var signedOut = false
        var failed = false
        viewModel.signOut(onFailed = { failed = true }) { signedOut = true }
        advanceUntilIdle()

        assertFalse(signedOut)
        assertTrue(failed)
        assertTrue(viewModel.state.value.signOutFailed)
    }

    @Test
    fun `a check answering after the sign out stores nothing`() = runTest {
        coEvery { authApi.subscription() } coAnswers {
            delay(1_000)
            subscription(premium = true)
        }
        val viewModel = PaywallViewModel(authRepository, authApi)
        runCurrent()

        viewModel.signOut {}
        advanceUntilIdle()

        assertFalse(viewModel.state.value.premium)
        coVerify(exactly = 0) { authRepository.saveUser(any(), any()) }
    }

    @Test
    fun `prices are formatted with two decimals`() {
        assertEquals("$5.00", formatPrice(500, "USD"))
        assertEquals("€12.99", formatPrice(1299, "EUR"))
        assertEquals("R$ 29.90", formatPrice(2990, "BRL"))
        assertEquals("7.05 CHF", formatPrice(705, "CHF"))
    }

    @Test
    fun `prices follow the minor unit of their currency`() {
        assertEquals("¥500", formatPrice(500, "JPY"))
        assertEquals("5000 KRW", formatPrice(5000, "KRW"))
        assertEquals("1.500 KWD", formatPrice(1500, "KWD"))
    }
}
