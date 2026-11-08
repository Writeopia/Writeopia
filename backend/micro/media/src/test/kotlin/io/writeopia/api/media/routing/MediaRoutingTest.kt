package io.writeopia.api.media.routing

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
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
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.backend.models.ImageStorageService
import io.writeopia.databse.HikariCp
import io.writeopia.databse.createWriteopiaSchema
import io.writeopia.sdk.serialization.request.MediaDeleteRequest
import io.writeopia.sdk.serialization.response.StorageUsageResponse
import io.writeopia.sql.WriteopiaDbBackend
import io.writeopia.storage.StorageUsageService
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaRoutingTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val secret = "media-test-secret"
    private val quota = 1_000L

    private lateinit var db: WriteopiaDbBackend
    private val userId = "media-user-${UUID.randomUUID()}"
    private val otherUserId = "media-other-${UUID.randomUUID()}"

    /** Keeps the pictures in memory, named like the bucket does. */
    private val images = object : ImageStorageService {
        val stored = mutableMapOf<String, Long>()

        override suspend fun uploadImageBytes(
            bytes: ByteArray, contentType: String, fileName: String, userId: String, debugMode: Boolean
        ): String {
            val url = "https://storage.test/bucket/uploads/$userId/${stored.size}-$fileName"
            stored[url] = bytes.size.toLong()
            return url
        }

        override suspend fun deleteImage(url: String, userId: String, debugMode: Boolean): Long? {
            if (!url.contains("/uploads/$userId/")) return null
            return stored.remove(url) ?: 0L
        }
    }

    @BeforeTest
    fun setUp() {
        val driver = HikariCp.driver(debugMode = true)
        HikariCp.initializeSchemaIfNeeded(::createWriteopiaSchema)
        db = WriteopiaDbBackend(driver)
    }

    @AfterTest
    fun tearDown() {
        db.userStorageUsageEntityQueries.deleteByUser(userId)
        db.userStorageUsageEntityQueries.deleteByUser(otherUserId)
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
                mediaRoute(
                    debug = false,
                    imageStorageService = images,
                    storageUsage = StorageUsageService(db, quotaBytes = quota)
                )
            }
        }
    }

    private fun token(user: String = userId): String =
        JWT.create().withClaim("userId", user).sign(Algorithm.HMAC256(secret))

    private suspend fun ApplicationTestBuilder.upload(size: Int, user: String = userId) =
        client.submitFormWithBinaryData(
            url = "/${EndPoints.mediaUpload()}",
            formData = formData {
                append(
                    "image",
                    ByteArray(size),
                    Headers.build {
                        append(HttpHeaders.ContentType, "image/png")
                        append(HttpHeaders.ContentDisposition, "filename=\"photo.png\"")
                    }
                )
            }
        ) {
            header(HttpHeaders.Authorization, "Bearer ${token(user)}")
        }

    private suspend fun ApplicationTestBuilder.usage(): StorageUsageResponse {
        val response = client.get("/${EndPoints.mediaUsage()}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        return json.decodeFromString(response.bodyAsText())
    }

    @Test
    fun `an upload is stored and its bytes counted in the user's storage`() = testApplication {
        setUpApp()

        val response = upload(400)

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<ImageUploadResponse>(response.bodyAsText())
        assertEquals(400, images.stored[body.imageUrl])

        assertEquals(StorageUsageResponse(usedBytes = 400, quotaBytes = quota, version = 1), usage())
    }

    @Test
    fun `an upload that would overflow the quota is refused with the usage`() = testApplication {
        setUpApp()
        upload(700)

        val response = upload(301)

        assertEquals(HttpStatusCode.InsufficientStorage, response.status)
        val body = json.decodeFromString<StorageUsageResponse>(response.bodyAsText())
        assertEquals(700, body.usedBytes)
        assertEquals(quota, body.quotaBytes)
        assertEquals(1, images.stored.size)
    }

    @Test
    fun `deleting a picture gives its bytes back`() = testApplication {
        setUpApp()
        val url = json.decodeFromString<ImageUploadResponse>(upload(400).bodyAsText()).imageUrl
        upload(100)

        val response = client.post("/${EndPoints.mediaDelete()}") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(MediaDeleteRequest.serializer(), MediaDeleteRequest(url)))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(StorageUsageResponse(usedBytes = 100, quotaBytes = quota, version = 3), usage())
        assertEquals(1, images.stored.size)
    }

    @Test
    fun `a picture of another user cannot be deleted`() = testApplication {
        setUpApp()
        val url = json.decodeFromString<ImageUploadResponse>(upload(400).bodyAsText()).imageUrl

        val response = client.post("/${EndPoints.mediaDelete()}") {
            header(HttpHeaders.Authorization, "Bearer ${token(otherUserId)}")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(MediaDeleteRequest.serializer(), MediaDeleteRequest(url)))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(400, usage().usedBytes)
        assertEquals(1, images.stored.size)
    }

    @Test
    fun `without a token nothing is answered`() = testApplication {
        setUpApp()

        assertEquals(HttpStatusCode.Unauthorized, client.get("/${EndPoints.mediaUsage()}").status)
    }
}
