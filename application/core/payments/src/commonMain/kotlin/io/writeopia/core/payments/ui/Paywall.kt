package io.writeopia.core.payments.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun ProOfferScreen() {
    val options = remember {
        
//        PaywallOptions(dismissRequest = { TODO("Handle dismiss") }) {
//            shouldDisplayDismissButton = true
//        }
    }

    Paywall(options)
}
