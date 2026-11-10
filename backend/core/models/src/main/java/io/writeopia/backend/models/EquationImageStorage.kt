package io.writeopia.backend.models

/**
 * Where the pictures of the equations are kept. Like the header pictures, they are public and
 * served by a CDN: an equation is drawn once for its LaTeX and shown by every member of the
 * workspace. Nothing in them is private, and they are never deleted.
 */
interface EquationImageStorage {
    /**
     * Stores the picture of an equation under [objectName] (`equations/<hash>.png`), unless it is
     * there already.
     * @return The public URL of the picture.
     */
    suspend fun store(bytes: ByteArray, contentType: String, objectName: String, debugMode: Boolean): String
}
