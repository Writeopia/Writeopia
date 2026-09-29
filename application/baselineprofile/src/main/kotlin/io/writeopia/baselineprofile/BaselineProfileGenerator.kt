package io.writeopia.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile for the Android app. It covers startup, the notes menu and,
 * most importantly, opening and scrolling documents, so the editor's text drawers are
 * AOT-compiled at install time instead of being JIT-compiled while the user scrolls.
 *
 * Run with: ./gradlew :application:androidApp:generateReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()

            chooseOfflineSpaceIfNeeded()

            TUTORIAL_DOCUMENTS.forEach { title ->
                openDocumentAndScroll(title)
            }
        }
    }

    private fun MacrobenchmarkScope.chooseOfflineSpaceIfNeeded() {
        val privateSpace = device.wait(Until.findObject(By.res(PRIVATE_SPACE_CARD_TAG)), TIMEOUT)
        privateSpace?.click()
        device.wait(Until.hasObject(By.text(TUTORIAL_DOCUMENTS.first())), TIMEOUT)
    }

    private fun MacrobenchmarkScope.openDocumentAndScroll(title: String) {
        val document = device.wait(Until.findObject(By.text(title)), TIMEOUT) ?: return
        document.click()
        device.waitForIdle()

        val editor = device.wait(Until.findObject(By.scrollable(true)), TIMEOUT)
        if (editor != null) {
            // Keep the gesture away from the system navigation edges.
            editor.setGestureMargin(device.displayWidth / 5)
            repeat(SCROLL_REPETITIONS) {
                editor.fling(Direction.DOWN)
                device.waitForIdle()
            }
            repeat(SCROLL_REPETITIONS) {
                editor.fling(Direction.UP)
                device.waitForIdle()
            }
        }

        device.pressBack()
        device.waitForIdle()
    }

    private companion object {
        const val TARGET_PACKAGE = "io.writeopia"
        const val PRIVATE_SPACE_CARD_TAG = "privateSpaceCard"
        const val TIMEOUT = 10_000L
        const val SCROLL_REPETITIONS = 3

        // Seeded on first run when the offline space is chosen (see :tutorials).
        val TUTORIAL_DOCUMENTS = listOf("Using Commands", "Welcome!", "Saving Notes", "Using AI")
    }
}
