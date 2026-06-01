package io.writeopia.ui.spellcheck.macos

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * JNA interface for Objective-C runtime functions.
 * Used to interact with macOS native APIs.
 */
interface ObjCRuntime : Library {
    companion object {
        val INSTANCE: ObjCRuntime? by lazy {
            try {
                Native.load("objc", ObjCRuntime::class.java)
            } catch (e: UnsatisfiedLinkError) {
                null
            }
        }
    }

    /**
     * Gets a class by name from the Objective-C runtime.
     */
    fun objc_getClass(name: String): Pointer?

    /**
     * Registers a selector (method name) with the Objective-C runtime.
     */
    fun sel_registerName(name: String): Pointer?

    /**
     * Sends a message to an Objective-C object (no arguments).
     */
    fun objc_msgSend(receiver: Pointer?, selector: Pointer?): Pointer?

    /**
     * Sends a message to an Objective-C object with a Pointer argument.
     */
    fun objc_msgSend(receiver: Pointer?, selector: Pointer?, arg1: Pointer?): Pointer?

    /**
     * Sends a message to an Objective-C object with string, range, long, boolean, and pointer arguments.
     * Used for rangeOfMisspelledWordInString:range:startingAt:wrap:language:
     */
    fun objc_msgSend(
        receiver: Pointer?,
        selector: Pointer?,
        string: Pointer?,
        rangeLocation: NativeLong,
        rangeLength: NativeLong,
        startingAt: NativeLong,
        wrap: Boolean,
        language: Pointer?
    ): Pointer?
}

/**
 * JNA interface for Foundation framework functions.
 */
interface Foundation : Library {
    companion object {
        val INSTANCE: Foundation? by lazy {
            try {
                Native.load("Foundation", Foundation::class.java)
            } catch (e: UnsatisfiedLinkError) {
                null
            }
        }
    }
}

/**
 * Represents an NSRange structure from Objective-C.
 */
@Structure.FieldOrder("location", "length")
open class NSRange(
    @JvmField var location: NativeLong = NativeLong(0),
    @JvmField var length: NativeLong = NativeLong(0)
) : Structure() {

    constructor(location: Long, length: Long) : this(NativeLong(location), NativeLong(length))

    class ByValue(
        location: NativeLong = NativeLong(0),
        length: NativeLong = NativeLong(0)
    ) : NSRange(location, length), Structure.ByValue {
        constructor() : this(NativeLong(0), NativeLong(0))
    }

    fun toIntRange(): IntRange? {
        val loc = location.toLong()
        val len = length.toLong()
        return if (loc >= 0 && len > 0) {
            loc.toInt() until (loc + len).toInt()
        } else {
            null
        }
    }
}
