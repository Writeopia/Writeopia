package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.network.subscription.SubscriptionApi
import io.writeopia.sdk.network.subscription.SubscriptionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubscriptionViewModel(
    private val subscriptionApi: SubscriptionApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _subscriptionState = MutableStateFlow<ResultData<SubscriptionResponse>>(ResultData.Idle())
    val subscriptionState: StateFlow<ResultData<SubscriptionResponse>> = _subscriptionState.asStateFlow()

    private val _checkoutUrlState = MutableStateFlow<ResultData<String>>(ResultData.Idle())
    val checkoutUrlState: StateFlow<ResultData<String>> = _checkoutUrlState.asStateFlow()

    private val _portalUrlState = MutableStateFlow<ResultData<String>>(ResultData.Idle())
    val portalUrlState: StateFlow<ResultData<String>> = _portalUrlState.asStateFlow()

    private val _userTier = MutableStateFlow(Tier.FREE)
    val userTier: StateFlow<Tier> = _userTier.asStateFlow()

    init {
        loadUserTier()
        loadSubscription()
    }

    private fun loadUserTier() {
        viewModelScope.launch {
            val user = authRepository.getUser()
            _userTier.value = user.tier
        }
    }

    fun loadSubscription() {
        viewModelScope.launch {
            _subscriptionState.value = ResultData.Loading()
            val token = authRepository.getAuthToken()
            if (token == null) {
                _subscriptionState.value = ResultData.Error(Exception("Not authenticated"))
                return@launch
            }

            _subscriptionState.value = subscriptionApi.getSubscription(token)
        }
    }

    fun createCheckoutSession(successUrl: String? = null, cancelUrl: String? = null) {
        viewModelScope.launch {
            _checkoutUrlState.value = ResultData.Loading()
            val token = authRepository.getAuthToken()
            if (token == null) {
                _checkoutUrlState.value = ResultData.Error(Exception("Not authenticated"))
                return@launch
            }

            val result = subscriptionApi.createCheckoutSession(token, successUrl, cancelUrl)
            _checkoutUrlState.value = when (result) {
                is ResultData.Complete -> ResultData.Complete(result.data.checkoutUrl)
                is ResultData.Error -> ResultData.Error(result.exception)
                is ResultData.Loading -> ResultData.Loading()
                is ResultData.Idle -> ResultData.Idle()
            }
        }
    }

    fun createPortalSession() {
        viewModelScope.launch {
            _portalUrlState.value = ResultData.Loading()
            val token = authRepository.getAuthToken()
            if (token == null) {
                _portalUrlState.value = ResultData.Error(Exception("Not authenticated"))
                return@launch
            }

            val result = subscriptionApi.createPortalSession(token)
            _portalUrlState.value = when (result) {
                is ResultData.Complete -> ResultData.Complete(result.data.portalUrl)
                is ResultData.Error -> ResultData.Error(result.exception)
                is ResultData.Loading -> ResultData.Loading()
                is ResultData.Idle -> ResultData.Idle()
            }
        }
    }

    fun clearCheckoutUrl() {
        _checkoutUrlState.value = ResultData.Idle()
    }

    fun clearPortalUrl() {
        _portalUrlState.value = ResultData.Idle()
    }

    fun isPremium(): Boolean = _userTier.value == Tier.PREMIUM
}
