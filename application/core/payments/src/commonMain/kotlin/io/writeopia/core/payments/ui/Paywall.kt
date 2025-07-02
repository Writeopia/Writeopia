package io.writeopia.core.payments.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions

@Composable
fun ProOfferScreen() {
    val options = remember {
        PaywallOptions(dismissRequest = { }) {
            shouldDisplayDismissButton = true
        }
    }

    Paywall(options)
}
