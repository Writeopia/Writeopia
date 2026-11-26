package io.writeopia.core.folders.sync.cloud

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * [CloudFileStore] over the Google Drive API, in the hidden app data folder of the user's Drive
 * (scope `drive.appdata`): the user doesn't see the files, and the app sees nothing else.
 *
 * @param accessToken a valid OAuth access token; asked again with `forceRefresh` once when Drive
 * answers 401.
 */
class GoogleDriveFileStore(
    private val httpClient: HttpClient,
    private val accessToken: suspend (forceRefresh: Boolean) -> String?,
) : CloudFileStore {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun list(): List<CloudFile> {
        val files = mutableListOf<CloudFile>()
        var pageToken: String? = null

        do {
            val response = authorized { token ->
                httpClient.get("$DRIVE/files") {
                    bearer(token)
                    parameter("spaces", APP_DATA_FOLDER)
                    parameter("fields", "nextPageToken,files($FILE_FIELDS)")
                    parameter("pageSize", 1000)
                    pageToken?.let { parameter("pageToken", it) }
                }
            }
            val page = json.decodeFromString<DriveFileList>(response.bodyAsText())
            files += page.files.map { file -> file.toCloudFile() }
            pageToken = page.nextPageToken
        } while (pageToken != null)

        return files
    }

    override suspend fun download(fileId: String): String =
        authorized { token ->
            httpClient.get("$DRIVE/files/$fileId") {
                bearer(token)
                parameter("alt", "media")
            }
        }.bodyAsText()

    override suspend fun create(name: String, content: String): CloudFile {
        val metadata = buildJsonObject {
            put("name", name)
            put("parents", buildJsonArray { add(APP_DATA_FOLDER) })
        }
        // multipart/related: the metadata, then the content (Drive's "multipart upload").
        val body = buildString {
            append("--$BOUNDARY\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$BOUNDARY\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content)
            append("\r\n--$BOUNDARY--\r\n")
        }

        val response = authorized { token ->
            httpClient.post("$UPLOAD/files") {
                bearer(token)
                parameter("uploadType", "multipart")
                parameter("fields", FILE_FIELDS)
                setBody(TextContent(body, ContentType("multipart", "related").withParameter("boundary", BOUNDARY)))
            }
        }
        return json.decodeFromString<DriveFile>(response.bodyAsText()).toCloudFile()
    }

    override suspend fun update(fileId: String, content: String): CloudFile {
        val response = authorized { token ->
            httpClient.patch("$UPLOAD/files/$fileId") {
                bearer(token)
                parameter("uploadType", "media")
                parameter("fields", FILE_FIELDS)
                setBody(TextContent(content, ContentType.Application.Json))
            }
        }
        return json.decodeFromString<DriveFile>(response.bodyAsText()).toCloudFile()
    }

    override suspend fun delete(fileId: String) {
        try {
            authorized { token -> httpClient.delete("$DRIVE/files/$fileId") { bearer(token) } }
        } catch (e: CloudStoreException) {
            // Already gone: what was asked for.
            if (!e.message.orEmpty().startsWith("${HttpStatusCode.NotFound.value} ")) throw e
        }
    }

    /** Runs [request] with a token; on 401 once more with a fresh one. Fails on any other error. */
    private suspend fun authorized(request: suspend (token: String) -> HttpResponse): HttpResponse {
        var response = request(token(forceRefresh = false))
        if (response.status == HttpStatusCode.Unauthorized) {
            response = request(token(forceRefresh = true))
        }
        if (!response.status.isSuccess()) {
            throw CloudStoreException(
                "${response.status.value} ${response.bodyAsText().take(300)}",
                unauthorized = response.status == HttpStatusCode.Unauthorized,
            )
        }
        return response
    }

    private suspend fun token(forceRefresh: Boolean): String =
        accessToken(forceRefresh) ?: throw CloudStoreException("Not connected to Google Drive", unauthorized = true)

    private fun HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    @Serializable
    private data class DriveFileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

    @Serializable
    private data class DriveFile(val id: String, val name: String = "", val version: String = "")

    private fun DriveFile.toCloudFile() = CloudFile(id = id, name = name, version = version)

    companion object {
        /** Lets the app see and change only its own hidden app data folder. */
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

        private const val DRIVE = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val APP_DATA_FOLDER = "appDataFolder"
        private const val FILE_FIELDS = "id,name,version"
        private const val BOUNDARY = "writeopia_cloud_sync_boundary"
    }
}
