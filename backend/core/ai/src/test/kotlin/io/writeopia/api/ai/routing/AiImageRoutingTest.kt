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
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.api.ai.configureTestPersistence
import io.writeopia.api.ai.repository.getAiUsageSummary
import io.writeopia.api.genai.model.GeneratedImage
import io.writeopia.api.genai.model.ImageGeneration
import io.writeopia.api.genai.model.TokenUsage
import io.writeopia.api.genai.service.GenAiService
import io.writeopia.app.dto.ai.AiImageRequest
import io.writeopia.app.dto.ai.AiImageResponse
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.backend.models.ImageStorageService
import io.writeopia.sql.WriteopiaDbBackend
import io.writeopia.storage.StorageUsageService
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiImageRoutingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val secret = "ai-image-test-secret"
    private val pictureBytes = byteArrayOf(1, 2, 3, 4, 5)

    private lateinit var db: WriteopiaDbBackend
    private val userId = "image-user-${UUID.randomUUID()}"

    private val images = object : ImageStorageService {
        val stored = mutableMapOf<String, Long>()

        override suspend fun uploadImageBytes(
            bytes: ByteArray, contentType: String, fileName: String, userId: String, debugMode: Boolean
        ): String {
            val url = "https://storage.test/bucket/uploads/$userId/${stored.size}-$fileName"
            stored[url] = bytes.size.toLong()
            return url
        }

        override suspend fun deleteImage(url: String, userId: String, debugMode: Boolean): Long? =
            if (url.contains("/uploads/$userId/")) stored.remove(url) ?: 0L else null
    }

    private val genAiService = mockk<GenAiService> {
        coEvery { generateImageWithUsage(any(), any(), any()) } returns ImageGeneration(
            image = GeneratedImage(bytes = pictureBytes, mimeType = "image/png"),
            tokenUsage = TokenUsage(inputTokens = 100, outputTokens = 1_200, totalTokens = 1_300)
        )
    }

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
        db.userEntityQueries.insertUser(
            id = userId,
            name = "Image User",
            username = "image-user",
            created_at = System.currentTimeMillis(),
            email = "$userId@example.com",
            password = "hashedpassword",
            salt = "salt",
            confirmation_code = null,
            confirmation_code_expiry = null,
            account_type = "PREMIUM",
            status = "ACTIVE"
        )
    }

    @AfterTest
    fun tearDown() {
        db.userEntityQueries.deleteUser(userId)
        db.userStorageUsageEntityQueries.deleteByUser(userId)
        db.aiUsageEntityQueries.deleteOldRecords(Long.MAX_VALUE)
    }

    private fun ApplicationTestBuilder.setUpApp(quotaBytes: Long) {
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(JWT.require(Algorithm.HMAC256(secret)).build())
                    validate { credential -> JWTPrincipal(credential.payload) }
                }
            }
            routing {
                aiImageRoute(
                    genAiService,
                    images,
                    debugMode = false,
                    writeopiaDb = db,
                    storageUsage = StorageUsageService(db, quotaBytes = quotaBytes)
                )
            }
        }
    }

    private fun token(): String = JWT.create().withClaim("userId", userId).sign(Algorithm.HMAC256(secret))

    private suspend fun ApplicationTestBuilder.askPicture() = client.post("/${EndPoints.aiImage()}") {
        header(HttpHeaders.Authorization, "Bearer ${token()}")
        contentType(ContentType.Application.Json)
        setBody(json.encodeToString(AiImageRequest.serializer(), AiImageRequest("A lighthouse at dusk.", "Coast")))
    }

    @Test
    fun `a drawn picture is stored with the user's media and counted in their storage`() = testApplication {
        setUpApp(quotaBytes = 1_000)

        val response = askPicture()

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<AiImageResponse>(response.bodyAsText())
        val url = assertNotNull(body.imageUrl)
        assertEquals(pictureBytes.size.toLong(), images.stored[url])

        val storage = StorageUsageService(db, quotaBytes = 1_000).usage(userId)
        assertEquals(pictureBytes.size.toLong(), storage.usedBytes)
        assertEquals(1, storage.version)
        assertEquals(1_300, db.getAiUsageSummary(userId, 0, Long.MAX_VALUE).totalTokens)
    }

    @Test
    fun `a full storage refuses the picture before it is drawn`() = testApplication {
        setUpApp(quotaBytes = pictureBytes.size.toLong())
        assertEquals(HttpStatusCode.OK, askPicture().status) // Fills the storage exactly

        val response = askPicture()

        assertEquals(HttpStatusCode.InsufficientStorage, response.status)
        val body = json.decodeFromString<AiImageResponse>(response.bodyAsText())
        assertTrue(body.error.orEmpty().contains("storage", ignoreCase = true))
        assertEquals(1, images.stored.size)
        // Drawn once only: the refused request spent no tokens.
        coVerify(exactly = 1) { genAiService.generateImageWithUsage(any(), any(), any()) }
        assertEquals(1_300, db.getAiUsageSummary(userId, 0, Long.MAX_VALUE).totalTokens)
    }

    @Test
    fun `a picture that does not fit in what is left is refused after drawing and not counted`() = testApplication {
        setUpApp(quotaBytes = pictureBytes.size * 2L - 1)
        assertEquals(HttpStatusCode.OK, askPicture().status)

        val response = askPicture()

        assertEquals(HttpStatusCode.InsufficientStorage, response.status)
        assertEquals(1, images.stored.size)
        assertEquals(pictureBytes.size.toLong(), StorageUsageService(db).usage(userId).usedBytes)
    }
}
