package io.writeopia.menu

import androidx.compose.material3.DropdownMenu
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.writeopia.commonui.IconsPicker
import org.junit.Test

class IconsPickerUiTests {

    /**
     * A DropdownMenu measures its content with intrinsics, which SubcomposeLayout based
     * composables like BoxWithConstraints can't answer. The folder card opens its icon picker
     * inside a DropdownMenu, so the picker must stay free of them.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `icons picker can be measured inside a dropdown menu`() = runComposeUiTest {
        setContent {
            DropdownMenu(expanded = true, onDismissRequest = {}) {
                IconsPicker(iconSelect = { _, _ -> })
            }
        }

        waitForIdle()
    }
}
