package io.writeopia.api.equation

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.backend.models.EquationImageStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EquationServiceTest {

    private val storage = object : EquationImageStorage {
        val stored = mutableMapOf<String, ByteArray>()

        override suspend fun store(bytes: ByteArray, contentType: String, objectName: String, debugMode: Boolean): String {
            stored[objectName] = bytes
            return "https://cdn.test/$objectName"
        }
    }

    private val usage = TokenUsage(10, 5, 15)

    private fun service(genAi: GenAiService, render: (String) -> ByteArray = ::fakeRender) =
        EquationService(genAi, storage, debugMode = false, rasterize = { byteArrayOf(1) }, renderPng = render)

    private fun fakeRender(latex: String): ByteArray =
        if (latex.contains("\\bad")) throw InvalidLatexException(latex, IllegalStateException("unknown command")) else latex.toByteArray()

    @Test
    fun `a recognized equation is rendered and stored under the hash of its LaTeX`() = runTest {
        val genAi = mockk<GenAiService> {
            coEvery { generateWithImageAndUsage(any(), any(), any(), any(), any()) } returns
                Pair(AiGenerateResponse(response = """{"latex": "x^2"}""", done = true), usage)
        }

        val result = assertIs<EquationResult.Equation>(service(genAi).recognize("{}"))

        assertEquals("x^2", result.latex)
        assertEquals("https://cdn.test/${EquationService.objectNameOf("x^2")}", result.imageUrl)
        assertEquals(usage, result.tokenUsage)
        assertEquals(1, storage.stored.size)
    }

    @Test
    fun `invalid LaTeX from the model is asked again and both answers are charged`() = runTest {
        var calls = 0
        val genAi = mockk<GenAiService> {
            coEvery { generateWithImageAndUsage(any(), any(), any(), any(), any()) } answers {
                calls++
                val latex = if (calls == 1) "\\bad{x}" else "x"
                Pair(AiGenerateResponse(response = """{"latex": "${latex.replace("\\", "\\\\")}"}""", done = true), usage)
            }
        }

        val result = assertIs<EquationResult.Equation>(service(genAi).recognize("{}"))

        assertEquals("x", result.latex)
        assertEquals(TokenUsage(20, 10, 30), result.tokenUsage)
        coVerify(exactly = 2) { genAi.generateWithImageAndUsage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an empty answer means no equation was found`() = runTest {
        val genAi = mockk<GenAiService> {
            coEvery { generateWithImageAndUsage(any(), any(), any(), any(), any()) } returns
                Pair(AiGenerateResponse(response = """{"latex": ""}""", done = true), usage)
        }

        val result = assertIs<EquationResult.NotFound>(service(genAi).recognize("{}"))
        assertEquals(usage, result.tokenUsage)
    }

    @Test
    fun `a drawing with nothing to draw is not sent to the model`() = runTest {
        val genAi = mockk<GenAiService>()
        val service = EquationService(genAi, storage, debugMode = false, rasterize = { null }, renderPng = ::fakeRender)

        assertIs<EquationResult.NotFound>(service.recognize("{}"))
        coVerify(exactly = 0) { genAi.generateWithImageAndUsage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a model error fails the recognition`() = runTest {
        val genAi = mockk<GenAiService> {
            coEvery { generateWithImageAndUsage(any(), any(), any(), any(), any()) } returns
                Pair(AiGenerateResponse(error = "boom"), TokenUsage(0, 0, 0))
        }

        assertEquals("boom", assertIs<EquationResult.Failed>(service(genAi).recognize("{}")).message)
    }

    @Test
    fun `rendering refuses LaTeX that does not parse and renders the rest without the model`() = runTest {
        val genAi = mockk<GenAiService>()

        assertIs<EquationResult.InvalidLatex>(service(genAi).render("\\bad{x}"))
        val rendered = assertIs<EquationResult.Equation>(service(genAi).render("  \\frac{1}{2} "))
        assertEquals("\\frac{1}{2}", rendered.latex)
        coVerify(exactly = 0) { genAi.generateWithImageAndUsage(any(), any(), any(), any(), any()) }
    }
}
