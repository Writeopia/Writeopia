package io.writeopia.auth.core.repository

// Todo: Create a real class
class MockAuthRepository : AuthRepository {

    override fun saveUserChoiceOffline() {
    }

    override fun eraseUserChoiceOffline() {
    }

    override fun isUserOfflineByChoice(): Boolean = false
}
