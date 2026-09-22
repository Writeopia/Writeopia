package io.writeopia.sdk.serialization.data

import kotlin.test.Test
import kotlin.test.assertTrue

class DocumentApiBinaryCompatibilityTest {

    @Test
    fun legacyJvmDataClassSignaturesRemainAvailable() {
        val methods = DocumentApi::class.java.declaredMethods
        val constructors = DocumentApi::class.java.declaredConstructors

        assertTrue(methods.any { it.name == "copy" && it.parameterCount == 13 })
        assertTrue(methods.any { it.name == "copy\$default" && it.parameterCount == 16 })
        assertTrue(
            (1..13).all { index ->
                methods.any { method ->
                    method.name == "component$" + index && method.parameterCount == 0
                }
            }
        )
        assertTrue(constructors.any { it.parameterCount == 13 })
        assertTrue(constructors.any { it.parameterCount == 15 })
    }
}
