package io.writeopia.application

import android.app.Application
import io.writeopia.analytics.di.AnalyticsInjection
import io.writeopia.auth.core.di.AuthCoreInjectionNeo
import io.writeopia.persistence.room.DatabaseConfigAndroid
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.persistence.room.injection.WriteopiaRoomInjector
import io.writeopia.ui.drawer.video.VideoFrameConfig

class WriteopiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        AnalyticsInjection.initialize(this)

        // Initialize Room database first
        val database = WriteopiaApplicationDatabase.database(DatabaseConfigAndroid.roomBuilder(this))
        WriteopiaRoomInjector.init(database)

        // Initialize secure token storage (depends on Room being ready)
        AuthCoreInjectionNeo.initialize(this)

        VideoFrameConfig.configCoilForVideoFrame(this)
    }
}
