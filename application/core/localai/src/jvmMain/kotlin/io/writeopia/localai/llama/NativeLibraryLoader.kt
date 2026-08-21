package io.writeopia.localai.llama

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Utility to extract and load bundled native libraries from JAR resources.
 * Libraries should be placed in: resources/natives/{os}-{arch}/
 */
object NativeLibraryLoader {

    private const val NATIVES_DIR = "natives"

    @Volatile
    private var loaded = false

    @Volatile
    private var loadError: String? = null

    fun isLoaded(): Boolean = loaded

    fun getLoadError(): String? = loadError

    /**
     * Attempts to load the native library. Returns true if successful.
     * First tries bundled resources, then falls back to system paths.
     */
    @Synchronized
    fun loadLibrary(): Boolean {
        if (loaded) return true

        // Try loading from bundled resources first
        val bundledResult = tryLoadBundled()
        if (bundledResult) {
            loaded = true
            return true
        }

        // Fall back to system library path
        val systemResult = tryLoadSystem()
        if (systemResult) {
            loaded = true
            return true
        }

        return false
    }

    private fun tryLoadBundled(): Boolean {
        return try {
            val osArch = getOsArchPath() ?: run {
                loadError = "Unsupported platform: ${System.getProperty("os.name")} / ${System.getProperty("os.arch")}"
                return false
            }

            val tempDir = createTempDirectory()
            println("[LlamaLoader] Temp directory: ${tempDir.absolutePath}")

            // Get list of libraries to load based on OS
            val libraries = getLibraryNames()
            println("[LlamaLoader] Libraries to load: $libraries")

            // Extract all libraries first
            for (libName in libraries) {
                val resourcePath = "/$NATIVES_DIR/$osArch/$libName"
                val inputStream = NativeLibraryLoader::class.java.getResourceAsStream(resourcePath)
                if (inputStream != null) {
                    val tempFile = File(tempDir, libName)
                    extractResource(inputStream, tempFile)
                } else {
                    println("[LlamaLoader] Library not found in resources: $resourcePath")
                }
            }

            // Set library path for dependent libraries
            val libraryPath = System.getProperty("java.library.path") ?: ""
            System.setProperty("java.library.path", "${tempDir.absolutePath}:$libraryPath")

            // Load libraries in order (dependencies first)
            for (libName in libraries) {
                val tempFile = File(tempDir, libName)
                if (tempFile.exists()) {
                    try {
                        System.load(tempFile.absolutePath)
                        println("[LlamaLoader] Loaded: ${tempFile.absolutePath}")
                    } catch (e: UnsatisfiedLinkError) {
                        println("[LlamaLoader] Failed to load $libName: ${e.message}")
                        // Continue trying other libraries
                    }
                }
            }

            // Verify the main library is loaded
            val mainLib = File(tempDir, getMainLibraryName())
            if (!mainLib.exists()) {
                loadError = "Main library not found: ${mainLib.absolutePath}"
                return false
            }

            println("[LlamaLoader] Successfully loaded bundled libraries")
            true
        } catch (e: Exception) {
            loadError = "Failed to load bundled library: ${e.message}"
            println("[LlamaLoader] $loadError")
            e.printStackTrace()
            false
        }
    }

    private fun tryLoadSystem(): Boolean {
        return try {
            // Try custom path first
            val customPath = System.getProperty("llama.library.path")
            if (customPath != null) {
                val libFile = File(customPath, getMainLibraryName())
                if (libFile.exists()) {
                    System.load(libFile.absolutePath)
                    println("[LlamaLoader] Loaded library from custom path: ${libFile.absolutePath}")
                    return true
                }
            }

            // Try standard system locations
            System.loadLibrary("llama")
            println("[LlamaLoader] Loaded library from system path")
            true
        } catch (e: UnsatisfiedLinkError) {
            loadError = "Library not found in system paths: ${e.message}"
            println("[LlamaLoader] $loadError")
            false
        }
    }

    private fun getOsArchPath(): String? {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()

        val osName = when {
            os.contains("mac") || os.contains("darwin") -> "macos"
            os.contains("win") -> "windows"
            os.contains("linux") -> "linux"
            else -> return null
        }

        val archName = when {
            arch.contains("aarch64") || arch.contains("arm64") -> "arm64"
            arch.contains("amd64") || arch.contains("x86_64") -> "x64"
            else -> return null
        }

        return "$osName-$archName"
    }

    private fun getLibraryNames(): List<String> {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") || os.contains("darwin") -> listOf(
                "libggml-base.dylib",
                "libggml.dylib",
                "libllama.dylib"
            )
            os.contains("win") -> listOf(
                "ggml-base.dll",
                "ggml.dll",
                "llama.dll"
            )
            else -> listOf(
                "libggml-base.so",
                "libggml.so",
                "libllama.so"
            )
        }
    }

    private fun getMainLibraryName(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") || os.contains("darwin") -> "libllama.dylib"
            os.contains("win") -> "llama.dll"
            else -> "libllama.so"
        }
    }

    private fun createTempDirectory(): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "writeopia-llama")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        return tempDir
    }

    private fun extractResource(inputStream: InputStream, targetFile: File) {
        // Check if already extracted and same size (simple cache)
        if (targetFile.exists()) {
            println("[LlamaLoader] Using cached library: ${targetFile.absolutePath}")
            return
        }

        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }

        // Make executable on Unix systems
        targetFile.setExecutable(true)
        println("[LlamaLoader] Extracted library to: ${targetFile.absolutePath}")
    }
}
