package io.writeopia.api.core.auth

import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.hash.toBase64
import io.writeopia.api.core.auth.models.WriteopiaBeUser
import io.writeopia.api.core.auth.repository.getCompanyByDomain
import io.writeopia.api.core.auth.repository.insertCompany
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID

object AuthService {
    fun createUser(
        writeopiaDb: WriteopiaDbBackend,
        registerRequest: RegisterRequest,
        enabled: Boolean
    ): WriteopiaUser {
        val (name, email, companyDomain, password) = registerRequest

        if (companyDomain.isNotEmpty()) {
            val company = writeopiaDb.getCompanyByDomain(companyDomain)

            if (company == null) {
                writeopiaDb.insertCompany(companyDomain)
            }
        }

        val id = UUID.randomUUID().toString()

        val salt = HashUtils.generateSalt()
        val hash = HashUtils.hashPassword(password, salt).toBase64()

        writeopiaDb.insertUser(
            id = id,
            name = name,
            email = email,
            password = hash,
            salt = salt.toBase64(),
            companyDomain = companyDomain,
            enabled = enabled
        )

        return WriteopiaUser(
            id = id,
            name = name,
            email = email,
            company = ""
        )
    }

    fun resetPassword(
        writeopiaDb: WriteopiaDbBackend,
        user: WriteopiaBeUser,
        newPassword: String
    ) {
        val salt = HashUtils.generateSalt()
        val hash = HashUtils.hashPassword(newPassword, salt).toBase64()

        writeopiaDb.insertUser(user.copy(password = hash, salt = salt.toBase64()))
    }
}
