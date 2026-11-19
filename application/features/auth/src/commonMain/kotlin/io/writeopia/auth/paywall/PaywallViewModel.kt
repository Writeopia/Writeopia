package io.writeopia.auth.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.data.AuthApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the paywall shows.
 *
 * @property checking Asking the backend whether the user is premium.
 * @property premium The backend said the user is premium: the sign-in goes on.
 * @property monthlyPrice The price of the subscription, formatted; null until it is known.
 * @property failed The check failed: the user can only retry, never go through.
 * @property stillNotPremium A check asked by the user ("check again") found no subscription yet.
 */
data class PaywallState(
    val checking: Boolean = true,
    val premium: Boolean = false,
    val monthlyPrice: String? = null,
    val failed: Boolean = false,
    val stillNotPremium: Boolean = false,
)

/**
 * Shown right after signing in or signing up: asks the backend whether the user is premium. A
 * premium user goes on to the workspaces; the others are offered the subscription, the free
 * offline space, or signing out.
 */
class PaywallViewModel(
    private val authRepository: AuthRepository,
    private val authApi: AuthApi,
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallState())
    val state: StateFlow<PaywallState> = _state.asStateFlow()

    private var checkJob: Job? = null

    init {
        check(requestedByUser = false)
    }

    /** "I've subscribed, check again". */
    fun checkAgain() {
        check(requestedByUser = true)
    }

    private fun check(requestedByUser: Boolean) {
        if (checkJob?.isActive == true) return

        _state.update { it.copy(checking = true, failed = false, stillNotPremium = false) }

        checkJob = viewModelScope.launch {
            when (val result = authApi.subscription()) {
                is ResultData.Complete -> {
                    val subscription = result.data

                    if (subscription.premium) {
                        val user = authRepository.getUser()
                        if (user.tier != Tier.PREMIUM) {
                            authRepository.saveUser(user.copy(tier = Tier.PREMIUM), selected = true)
                        }
                    }

                    _state.value = PaywallState(
                        checking = false,
                        premium = subscription.premium,
                        monthlyPrice = formatPrice(subscription.monthlyPriceCents, subscription.currency),
                        stillNotPremium = requestedByUser && !subscription.premium,
                    )
                }

                else -> _state.update { it.copy(checking = false, failed = true) }
            }
        }
    }

    /**
     * Signs the online account out on this device, for the offline space or another account.
     */
    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.unselectAllWorkspaces()
            authRepository.logout()
            onSignedOut()
        }
    }
}

/**
 * Formats a price in the minor unit of [currency] (cents for USD), such as `$5.00` or `R$ 29.90`.
 * Common code has no locale-aware currency formatter, so the symbol of the most common currencies
 * is used and the ISO code otherwise.
 */
internal fun formatPrice(cents: Long, currency: String): String {
    val units = cents / 100
    val minor = (cents % 100).toString().padStart(2, '0')
    val amount = "$units.$minor"

    return when (currency.uppercase()) {
        "USD" -> "$$amount"
        "EUR" -> "€$amount"
        "GBP" -> "£$amount"
        "JPY" -> "¥$units"
        "BRL" -> "R$ $amount"
        else -> "$amount ${currency.uppercase()}"
    }
}
