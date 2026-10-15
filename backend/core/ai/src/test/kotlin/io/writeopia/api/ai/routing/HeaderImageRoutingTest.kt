package io.writeopia.api.ai.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.get
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
import io.writeopia.api.genai.model.GeneratedImage
import io.writeopia.api.genai.model.ImageGeneration
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.sdk.serialization.request.HeaderImageGenerateRequest
import io.writeopia.sdk.serialization.response.HeaderImageGenerateResponse
import io.writeopia.sdk.serialization.request.HeaderImageSaveRequest
import io.writeopia.sdk.serialization.response.HeaderImagesResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.backend.models.HeaderImageStorage
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeaderImageRoutingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val secret = "header-image-test-secret"

    private lateinit var db: WriteopiaDbBackend
    private val userId = "header-user-${UUID.randomUUID()}"
    private val workspaceId = "header-workspace-${UUID.randomUUID()}"
    private val otherWorkspaceId = "other-workspace-${UUID.randomUUID()}"

    private val storage = object : HeaderImageStorage {
        val stored = mutableListOf<String>()

        override suspend fun store(bytes: ByteArray, contentType: String, workspaceId: String, debugMode: Boolean): String =
            "https://cdn.test/header-images/$workspaceId/${stored.size}.png".also(stored::add)

        override fun isHeaderOf(url: String, workspaceId: String, debugMode: Boolean): Boolean =
            url.startsWith("https://cdn.test/header-images/$workspaceId/")
    }

    private val genAiService = mockk<GenAiService> {
        coEvery { generateImageWithUsage(any(), any(), any()) } returns ImageGeneration(
            image = GeneratedImage(bytes = byteArrayOf(1, 2, 3), mimeType = "image/png"),
            tokenUsage = TokenUsage(inputTokens = 100, outputTokens = 1_200, totalTokens = 1_300)
        )
    }

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
        db.userEntityQueries.insertUser(
            id = userId,
            name = "Header User",
            username = "header-user",
            created_at = System.currentTimeMillis(),
            email = "$userId@example.com",
            password = "hashedpassword",
            salt = "salt",
            confirmation_code = null,
            confirmation_code_expiry = null,
            account_type = "PREMIUM",
            status = "ACTIVE"
        )
        db.workspaceEntityQueries.insert(workspaceId, "Workspace", null, null)
        db.workspaceEntityQueries.insert(otherWorkspaceId, "Other", null, null)
        db.workspaceToUserQueries.insertWorkspaceToUser(workspaceId, userId, "USER")
    }

    @AfterTest
    fun tearDown() {
        db.workspaceHeaderImageEntityQueries.deleteByWorkspace(workspaceId)
        db.workspaceToUserQueries.deleteByWorkspaceId(workspaceId)
        db.workspaceEntityQueries.delete(workspaceId)
        db.workspaceEntityQueries.delete(otherWorkspaceId)
        db.userEntityQueries.deleteUser(userId)
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
                headerImageRoute(genAiService, storage, debugMode = false, writeopiaDb = db)
            }
        }
    }

    private fun token(): String = JWT.create().withClaim("userId", userId).sign(Algorithm.HMAC256(secret))

    @Test
    fun `a generated header is stored on the CDN and charged to the user`() = testApplication {
        setUpApp()

        val response = client.post("/${EndPoints.aiHeaderImage()}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(HeaderImageGenerateRequest(workspaceId, "Coastal Journal", "The lighthouse.")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<HeaderImageGenerateResponse>(response.bodyAsText())
        assertEquals("https://cdn.test/header-images/$workspaceId/0.png", body.imageUrl)

        val usage = db.getAiUsageSummary(userId, 0, Long.MAX_VALUE)
        assertEquals(1_300, usage.totalTokens)

        // A candidate doesn't join the workspace until it is approved.
        assertTrue(db.workspaceHeaderImageEntityQueries.selectByWorkspace(workspaceId).executeAsList().isEmpty())
    }

    @Test
    fun `a header is not drawn for a workspace the user is not in`() = testApplication {
        setUpApp()

        val response = client.post("/${EndPoints.aiHeaderImage()}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(HeaderImageGenerateRequest(otherWorkspaceId)))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(storage.stored.isEmpty())
        assertEquals(0, db.getAiUsageSummary(userId, 0, Long.MAX_VALUE).totalTokens)
    }

    @Test
    fun `an approved header joins the workspace once`() = testApplication {
        setUpApp()
        val url = "https://cdn.test/header-images/$workspaceId/approved.png"

        repeat(2) {
            val saved = client.post("/${EndPoints.workspaceHeaderImages(workspaceId)}") {
                header(HttpHeaders.Authorization, "Bearer ${token()}")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(HeaderImageSaveRequest(url)))
            }
            assertEquals(HttpStatusCode.OK, saved.status)
        }

        val response = client.get("/${EndPoints.workspaceHeaderImages(workspaceId)}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val images = json.decodeFromString<HeaderImagesResponse>(response.bodyAsText()).images
        assertEquals(listOf(url), images.map { it.url })
        assertNotNull(images.first().id)
    }

    @Test
    fun `an address that is not a header of the workspace is refused`() = testApplication {
        setUpApp()

        val response = client.post("/${EndPoints.workspaceHeaderImages(workspaceId)}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(HeaderImageSaveRequest("https://cdn.test/header-images/$otherWorkspaceId/x.png")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(db.workspaceHeaderImageEntityQueries.selectByWorkspace(workspaceId).executeAsList().isEmpty())
    }

    @Test
    fun `the headers of a workspace are only listed to its members`() = testApplication {
        setUpApp()

        val response = client.get("/${EndPoints.workspaceHeaderImages(otherWorkspaceId)}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }
}
