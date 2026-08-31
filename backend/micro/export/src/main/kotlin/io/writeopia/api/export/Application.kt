package io.writeopia.api.export

import io.writeopia.api.export.service.ExportEmailService
import io.writeopia.databse.HikariCp
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ExportApplication")

fun main() = runBlocking {
    logger.info("Workspace Export Job starting...")

    try {
        val request = ExportConfig.loadFromEnvironment()
        logger.info("Export request: workspaceId=${request.workspaceId}, userId=${request.userId}")

        val database = WriteopiaDbBackend(HikariCp.driver())
        logger.info("Database connection established")

        val exportJob = ExportJob(database)
        val result = exportJob.execute(request)

        val json = Json { prettyPrint = true }
        println(json.encodeToString(result))

        logger.info("Export completed successfully")
        logger.info("Signed URL: ${result.signedUrl}")
        logger.info("Documents exported: ${result.documentCount}")
        logger.info("Folders exported: ${result.folderCount}")

        val emailSent = ExportEmailService.sendExportReadyEmail(
            toEmail = request.userEmail,
            userName = request.userName,
            downloadUrl = result.signedUrl,
            documentCount = result.documentCount,
            folderCount = result.folderCount
        )

        if (emailSent) {
            logger.info("Export notification email sent to ${request.userEmail}")
        } else {
            logger.warn("Failed to send export notification email to ${request.userEmail}")
        }
    } catch (e: Exception) {
        logger.error("Export failed", e)
        throw e
    } finally {
        HikariCp.close()
    }
}
