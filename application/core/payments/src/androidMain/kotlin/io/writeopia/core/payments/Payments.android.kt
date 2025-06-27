package io.writeopia.core.payments

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

actual object Payments {
    actual fun configure() {
        Purchases.configure(
            PurchasesConfiguration(
                apiKey = "goog_TGjvUMpxiJStHSyLLYtxiVciQjA",
            )
        )
    }
}
