package io.writeopia.application

import android.app.Application
import io.writeopia.editor.features.summarization.SummarizationService
import io.writeopia.persistence.room.DatabaseConfigAndroid
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.ui.drawer.video.VideoFrameConfig

class WriteopiaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        VideoFrameConfig.configCoilForVideoFrame(this)

        WriteopiaApplicationDatabase.database(DatabaseConfigAndroid.roomBuilder(this))
        SummarizationService.initialize(this)
    }
}
