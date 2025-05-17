package io.writeopia.auth.core.manager

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import io.writeopia.auth.core.data.User
import io.writeopia.common.utils.ResultData
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    override suspend fun getUser(): User =
        auth.currentUser?.let { firebaseUser ->
            User(
                id = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                name = firebaseUser.displayName ?: "",
            )
        } ?: User.disconnectedUser()

    override suspend fun isLoggedIn(): ResultData<Boolean> =
        ResultData.Complete(auth.currentUser != null)

    override suspend fun signUp(
        email: String,
        password: String,
        name: String
    ): ResultData<Boolean> =
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            val userCreated = user != null

            try {
                if (user != null) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()

                    user.updateProfile(profileUpdates).await()
                }

                ResultData.Complete(userCreated)
            } catch (e: Exception) {
                ResultData.Complete(userCreated)
            }
        } catch (e: Exception) {
            ResultData.Error(e)
        }

    override suspend fun signIn(email: String, password: String): ResultData<Boolean> =
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            ResultData.Complete(result.user != null)
        } catch (e: Exception) {
            ResultData.Error(e)
        }

    override suspend fun logout(): ResultData<Boolean> {
        auth.signOut()
        return ResultData.Complete(true)
    }
}
