package io.writeopia.api.ai

import io.ktor.server.application.Application
import io.writeopia.api.core.auth.utils.installAuth as coreInstallAuth

fun Application.installAuth() {
    coreInstallAuth()
}
