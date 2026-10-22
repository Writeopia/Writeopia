package io.writeopia.sdk.serialization.data

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.serialization.serializers.RankSerializer
import io.writeopia.sdk.utils.iterables.Rank
import kotlinx.serialization.Serializable

@Serializable
data class StoryStepApi(
    val id: String = GenerateId.generate(),
    val type: StoryTypeApi,
    val parentId: String? = null,
    val url: String? = null,
    val path: String? = null,
    val text: String? = null,
    val checked: Boolean? = false,
    val steps: List<StoryStepApi> = emptyList(),
    val tags: Set<TagInfoApi> = emptySet(),
    val spans: Set<SpanInfoApi> = emptySet(),
    val decoration: DecorationApi = DecorationApi(),
    @Serializable(with = RankSerializer::class)
    val position: String = Rank.FIRST,
    val documentLink: DocumentLinkApi? = null,
    val lastUpdatedAt: Long? = null
)
