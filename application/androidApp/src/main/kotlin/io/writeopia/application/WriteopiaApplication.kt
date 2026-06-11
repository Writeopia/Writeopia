package io.writeopia.application

import android.app.Application
import io.writeopia.persistence.room.DatabaseConfigAndroid
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.ui.drawer.video.VideoFrameConfig

class WriteopiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        VideoFrameConfig.configCoilForVideoFrame(this)

        WriteopiaApplicationDatabase.database(DatabaseConfigAndroid.roomBuilder(this))
    }
}
