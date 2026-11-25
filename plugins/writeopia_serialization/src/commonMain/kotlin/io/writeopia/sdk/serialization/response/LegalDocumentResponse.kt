package io.writeopia.sdk.serialization.response

import kotlinx.serialization.Serializable

/**
 * A legal document (Terms of Service, Privacy Policy) served by the public legal endpoint.
 * The endpoint is CDN-cacheable, so the response must not depend on the caller.
 *
 * @property type `terms` or `privacy`.
 * @property version Changes whenever the text changes in a way users must accept again.
 * @property effectiveDate ISO date (`yyyy-MM-dd`) from which this version applies.
 */
@Serializable
data class LegalDocumentResponse(
    val type: String,
    val version: String,
    val effectiveDate: String,
    val title: String,
    val sections: List<LegalSectionResponse>,
)

/**
 * @property body Plain text. Paragraphs are separated by a blank line; a line starting with `- ` is a bullet.
 */
@Serializable
data class LegalSectionResponse(
    val heading: String,
    val body: String,
)
