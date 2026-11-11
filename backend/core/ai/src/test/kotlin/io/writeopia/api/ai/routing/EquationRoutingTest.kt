package io.writeopia.api.ai.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import io.writeopia.api.ai.configureTestPersistence
import io.writeopia.api.ai.repository.getAiUsageSummary
import io.writeopia.api.equation.EquationService
import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.app.dto.ai.EquationRecognizeRequest
import io.writeopia.app.dto.ai.EquationRenderRequest
import io.writeopia.app.dto.ai.EquationResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.backend.models.EquationImageStorage
import io.writeopia.sdk.models.drawing.DrawPoint
import io.writeopia.sdk.models.drawing.DrawingData
import io.writeopia.sdk.models.drawing.Stroke
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EquationRoutingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val secret = "equation-test-secret"

    private lateinit var db: WriteopiaDbBackend
    private val userId = "equation-user-${UUID.randomUUID()}"
    private val freeUserId = "equation-free-user-${UUID.randomUUID()}"

    private val storage = object : EquationImageStorage {
        override suspend fun store(bytes: ByteArray, contentType: String, objectName: String, debugMode: Boolean): String =
            "https://cdn.test/$objectName"
    }

    private var answer = """{"latex": "x^2 + 1"}"""

    private val genAiService = mockk<GenAiService> {
        coEvery { generateWithImageAndUsage(any(), any(), any(), any(), any()) } answers {
            Pair(AiGenerateResponse(response = answer, done = true), TokenUsage(100, 20, 120))
        }
    }

    private val drawingJson = Json.encodeToString(
        DrawingData.serializer(),
        DrawingData(strokes = listOf(Stroke(points = listOf(DrawPoint(0f, 0f), DrawPoint(100f, 20f)))))
    )

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
        listOf(userId to "PREMIUM", freeUserId to "FREE").forEach { (id, accountType) ->
            db.userEntityQueries.insertUser(
                id = id,
                name = "Equation User",
                username = "equation-user-$id",
                created_at = System.currentTimeMillis(),
                email = "$id@example.com",
                password = "hashedpassword",
                salt = "salt",
                confirmation_code = null,
                confirmation_code_expiry = null,
                account_type = accountType,
                status = "ACTIVE"
            )
        }
    }

    @AfterTest
    fun tearDown() {
        db.userEntityQueries.deleteUser(userId)
        db.userEntityQueries.deleteUser(freeUserId)
        db.aiUsageEntityQueries.deleteOldRecords(Long.MAX_VALUE)
    }

    private fun ApplicationTestBuilder.setUpApp() {
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(Algorithm.HMAC256(secret)).build())
                    validate { credential -> JWTPrincipal(credential.payload) }
                }
            }
            routing {
                equationRoute(EquationService(genAiService, storage, debugMode = false), debugMode = false, writeopiaDb = db)
            }
        }
    }

    private fun token(id: String = userId): String =
        JWT.create().withClaim("userId", id).sign(Algorithm.HMAC256(secret))

    @Test
    fun `a drawing is read into an equation with its picture, and the tokens are charged`() = testApplication {
        setUpApp()

        val response = client.post("/${EndPoints.aiEquationRecognize()}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EquationRecognizeRequest(drawingJson)))
        }

        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        val body = json.decodeFromString<EquationResponse>(response.bodyAsText())
        assertEquals("x^2 + 1", body.latex)
        assertEquals("https://cdn.test/${EquationService.objectNameOf("x^2 + 1")}", body.imageUrl)

        assertEquals(120, db.getAiUsageSummary(userId, 0, Long.MAX_VALUE).totalTokens)
    }

    @Test
    fun `a drawing without mathematics answers 422`() = testApplication {
        setUpApp()
        answer = """{"latex": ""}"""

        val response = client.post("/${EndPoints.aiEquationRecognize()}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EquationRecognizeRequest(drawingJson)))
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(NO_EQUATION, json.decodeFromString<EquationResponse>(response.bodyAsText()).error)
        // The model still answered.
        assertEquals(120, db.getAiUsageSummary(userId, 0, Long.MAX_VALUE).totalTokens)
    }

    @Test
    fun `reading needs a premium account, rendering only a session`() = testApplication {
        setUpApp()

        val denied = client.post("/${EndPoints.aiEquationRecognize()}") {
            header(HttpHeaders.Authorization, "Bearer ${token(freeUserId)}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EquationRecognizeRequest(drawingJson)))
        }
        assertEquals(HttpStatusCode.Forbidden, denied.status)

        val rendered = client.post("/${EndPoints.aiEquationRender()}") {
            header(HttpHeaders.Authorization, "Bearer ${token(freeUserId)}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EquationRenderRequest("\\frac{1}{2}")))
        }
        assertEquals(HttpStatusCode.OK, rendered.status, rendered.bodyAsText())
        val body = json.decodeFromString<EquationResponse>(rendered.bodyAsText())
        assertEquals("\\frac{1}{2}", body.latex)
        assertTrue(assertNotNull(body.imageUrl).startsWith("https://cdn.test/header-images/equations/"))

        val anonymous = client.post("/${EndPoints.aiEquationRender()}") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EquationRenderRequest("x")))
        }
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
    }

    @Test
    fun `LaTeX that does not parse answers 400 with the message`() = testApplication {
        setUpApp()

        val response = client.post("/${EndPoints.aiEquationRender()}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EquationRenderRequest("\\frac{1}{")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertNotNull(json.decodeFromString<EquationResponse>(response.bodyAsText()).error)
    }

    @Test
    fun `too long LaTeX and too large drawings are refused before any work`() = testApplication {
        setUpApp()

        val longLatex = client.post("/${EndPoints.aiEquationRender()}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EquationRenderRequest("x+".repeat(MAX_LATEX_LENGTH))))
        }
        assertEquals(HttpStatusCode.BadRequest, longLatex.status)

        val largeDrawing = client.post("/${EndPoints.aiEquationRecognize()}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EquationRecognizeRequest("{".repeat(MAX_DRAWING_LENGTH + 1))))
        }
        assertEquals(HttpStatusCode.BadRequest, largeDrawing.status)
        assertEquals(0, db.getAiUsageSummary(userId, 0, Long.MAX_VALUE).totalTokens)
    }
}
