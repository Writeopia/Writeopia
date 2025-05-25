package io.writeopia.api.core.auth.repository

import io.writeopia.api.core.auth.models.Company
import io.writeopia.sql.WriteopiaDbBackend

fun WriteopiaDbBackend.getCompanyByDomain(domain: String): Company? =
    this.companyQueries
        .selectByDomain(domain)
        .executeAsOneOrNull()
        ?.let { companyEntity ->
            Company(domain = companyEntity.domain, name = companyEntity.name)
        }

fun WriteopiaDbBackend.insertCompany(domain: String) {
    this.companyQueries.insertCompany(domain = domain, name = domain)
}
