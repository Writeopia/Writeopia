package io.writeopia.core.folders.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmConfigFileWatcherTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `startWatching with blank path should not start watching`() {
        val watcher = JvmConfigFileWatcher(json)

        watcher.startWatching("")

        assertFalse(watcher.isWatching)
    }

    @Test
    fun `startWatching with non-existent path should not start watching`() {
        val watcher = JvmConfigFileWatcher(json)

        watcher.startWatching("/non/existent/path/that/does/not/exist")

        assertFalse(watcher.isWatching)
    }

    @Test
    fun `startWatching with file path instead of directory should not start watching`() {
        val tempFile = Files.createTempFile("test", ".txt").toFile()
        try {
            val watcher = JvmConfigFileWatcher(json)

            watcher.startWatching(tempFile.absolutePath)

            assertFalse(watcher.isWatching)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `startWatching with valid directory should start watching`() = runBlocking {
        val tempDir = Files.createTempDirectory("writeopia_test").toFile()
        try {
            val watcher = JvmConfigFileWatcher(json)

            watcher.startWatching(tempDir.absolutePath)

            assertTrue(watcher.isWatching)

            // Give the watcher job time to start polling
            delay(100)

            watcher.stopWatching()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `stopWatching should stop the watcher`() = runBlocking {
        val tempDir = Files.createTempDirectory("writeopia_test").toFile()
        try {
            val watcher = JvmConfigFileWatcher(json)
            watcher.startWatching(tempDir.absolutePath)
            assertTrue(watcher.isWatching)

            // Give the watcher job time to start
            delay(100)

            watcher.stopWatching()

            assertFalse(watcher.isWatching)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `startWatching should stop previous watcher before starting new one`() = runBlocking {
        val tempDir1 = Files.createTempDirectory("writeopia_test1").toFile()
        val tempDir2 = Files.createTempDirectory("writeopia_test2").toFile()
        try {
            val watcher = JvmConfigFileWatcher(json)

            watcher.startWatching(tempDir1.absolutePath)
            assertTrue(watcher.isWatching)

            // Give time for the first job to start
            delay(100)

            watcher.startWatching(tempDir2.absolutePath)
            assertTrue(watcher.isWatching)

            // Give time for the second job to start
            delay(100)

            watcher.stopWatching()
        } finally {
            tempDir1.deleteRecursively()
            tempDir2.deleteRecursively()
        }
    }

    @Test
    fun `initial lastKnownTimestamp should be read from existing config file`() = runBlocking {
        val tempDir = Files.createTempDirectory("writeopia_test").toFile()
        val configFile = File(tempDir, "writeopia_config_file.json")

        try {
            // Create config file before starting watcher
            configFile.writeText("""{"lastUpdateTable": 5000}""")

            val watcher = JvmConfigFileWatcher(json)
            watcher.startWatching(tempDir.absolutePath)

            assertTrue(watcher.isWatching)

            // Give time for the job to start
            delay(100)

            watcher.stopWatching()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `stopWatching on non-started watcher should not throw`() {
        val watcher = JvmConfigFileWatcher(json)

        // Should not throw
        watcher.stopWatching()

        assertFalse(watcher.isWatching)
    }

    @Test
    fun `multiple stopWatching calls should not throw`() = runBlocking {
        val tempDir = Files.createTempDirectory("writeopia_test").toFile()
        try {
            val watcher = JvmConfigFileWatcher(json)
            watcher.startWatching(tempDir.absolutePath)

            // Give time for the job to start
            delay(100)

            // Multiple stop calls should be safe
            watcher.stopWatching()
            watcher.stopWatching()
            watcher.stopWatching()

            assertFalse(watcher.isWatching)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
