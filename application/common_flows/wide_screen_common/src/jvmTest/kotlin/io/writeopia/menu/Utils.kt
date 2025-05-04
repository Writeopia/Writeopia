package io.writeopia.menu

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import io.writeopia.model.ColorThemeOption
import io.writeopia.notes.desktop.components.DesktopApp
import io.writeopia.sql.WriteopiaDb
import io.writeopia.sqldelight.database.DatabaseFactory
import io.writeopia.sqldelight.database.driver.DriverFactory
import io.writeopia.sqldelight.di.WriteopiaDbInjector
import io.writeopia.ui.keyboard.KeyboardEvent
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.startApp(
    databaseConfig: (WriteopiaDb) -> Unit = {}
) {
    setContent {
        val database: WriteopiaDb = DatabaseFactory.createDatabase(DriverFactory())
        databaseConfig(database)

        WriteopiaDbInjector.initialize(database)

        DesktopApp(
            coroutineScope = rememberCoroutineScope(),
            colorThemeOption = MutableStateFlow(ColorThemeOption.DARK),
            selectColorTheme = {},
            selectionState = MutableStateFlow(false),
            keyboardEventFlow = MutableStateFlow(KeyboardEvent.IDLE),
            toggleMaxScreen = {}
        )
    }
}
