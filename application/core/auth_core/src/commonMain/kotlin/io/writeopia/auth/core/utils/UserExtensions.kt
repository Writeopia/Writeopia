package io.writeopia.auth.core.utils

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration
import io.writeopia.app.sql.Writeopia_user_entity
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser

fun Writeopia_user_entity.toModel(): WriteopiaUser {
    return WriteopiaUser(
        id = this.id,
        email = this.email,
        name = this.name,
        tier = Tier.valueOf(this.tier),
        company = ""
    )
}

