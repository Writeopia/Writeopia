package io.writeopia.application

import android.app.Application
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.persistence.room.DatabaseConfigAndroid
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.ui.drawer.video.VideoFrameConfig

class WriteopiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize secure token storage before any auth operations
        AuthCoreInjectionNeo.initialize(this)

        VideoFrameConfig.configCoilForVideoFrame(this)
        WriteopiaApplicationDatabase.database(DatabaseConfigAndroid.roomBuilder(this))
    }
}
