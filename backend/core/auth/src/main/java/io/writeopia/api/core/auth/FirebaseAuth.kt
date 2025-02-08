package io.writeopia.api.core.auth

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

fun configureFirebase() {
    val option = FirebaseOptions.builder()
        .setProjectId(loadProjectId())
        .setCredentials(GoogleCredentials.getApplicationDefault())
        .build()

    FirebaseApp.initializeApp(option)
}

private fun loadProjectId(): String = System.getenv("WRITEOPIA_FIREBASE_ID")
