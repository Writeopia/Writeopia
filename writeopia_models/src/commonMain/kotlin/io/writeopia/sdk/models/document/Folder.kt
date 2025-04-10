package io.writeopia.sdk.models.document

import io.writeopia.sdk.models.id.GenerateId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class Folder(
    override val id: String,
    override val parentId: String,
    override val title: String,
    override val createdAt: Instant,
    override val lastUpdatedAt: Instant,
    override val userId: String,
    override val favorite: Boolean = false,
    override val icon: MenuItem.Icon? = null,
    val itemCount: Long,
    val documentList: List<Document> = emptyList(),
) : MenuItem {
    companion object {
        const val ROOT_PATH = "root"

        fun fromName(name: String, userId: String): Folder {
            val now = Clock.System.now()

            return Folder(
                id = GenerateId.generate(),
                parentId = ROOT_PATH,
                title = name,
                createdAt = now,
                lastUpdatedAt = now,
                itemCount = 0,
                userId = userId,
            )
        }
    }
}
