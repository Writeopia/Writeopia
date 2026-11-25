package io.writeopia.api.core.email

object ExportEmailService {
    suspend fun sendExportReadyEmail(
        toEmail: String,
        userName: String,
        downloadUrl: String,
        documentCount: Int,
        folderCount: Int
    ): Boolean = mailgunSender.send(
        toEmail = toEmail,
        subject = "Your Writeopia workspace export is ready",
        text = buildEmailText(userName, downloadUrl, documentCount, folderCount),
        html = buildEmailHtml(userName, downloadUrl, documentCount, folderCount),
    )

    private fun buildEmailText(
        userName: String,
        downloadUrl: String,
        documentCount: Int,
        folderCount: Int
    ): String {
        return """
            Hi $userName,

            Your Writeopia workspace export is ready for download!

            Export summary:
            - Documents: $documentCount
            - Folders: $folderCount

            Download your export here:
            $downloadUrl

            Please note: This download link will expire in 7 days.

            Best regards,
            The Writeopia Team
        """.trimIndent()
    }

    private fun buildEmailHtml(
        userName: String,
        downloadUrl: String,
        documentCount: Int,
        folderCount: Int
    ): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .summary { background: #f5f5f5; padding: 15px 20px; border-radius: 8px; margin: 20px 0; }
                    .summary-item { margin: 5px 0; }
                    .download-btn { display: inline-block; background: #4A90D9; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; margin: 20px 0; }
                    .download-btn:hover { background: #357ABD; }
                    .footer { color: #666; font-size: 14px; margin-top: 30px; }
                    .expiry-notice { color: #888; font-size: 13px; margin-top: 10px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Your Export is Ready!</h1>
                    <p>Hi $userName,</p>
                    <p>Your Writeopia workspace export has been completed and is ready for download.</p>

                    <div class="summary">
                        <div class="summary-item"><strong>Documents:</strong> $documentCount</div>
                        <div class="summary-item"><strong>Folders:</strong> $folderCount</div>
                    </div>

                    <a href="$downloadUrl" class="download-btn">Download Export</a>

                    <p class="expiry-notice">This download link will expire in 7 days.</p>

                    <p class="footer">Best regards,<br>The Writeopia Team</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}
