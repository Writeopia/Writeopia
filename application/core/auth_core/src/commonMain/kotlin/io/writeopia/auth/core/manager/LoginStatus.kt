package io.writeopia.auth.core.manager

enum class LoginStatus {
    OFFLINE_NOT_CHOSEN,
    OFFLINE_CHOSEN,
    CHOOSE_WORKSPACE,
    ONLINE,
    EMAIL_NOT_CONFIRMED
}
