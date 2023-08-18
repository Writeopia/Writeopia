package com.github.leandroborgesferreira.storyteller.serialization.data

import kotlinx.serialization.Serializable

@Serializable
data class StoryStepApi(
    val id: String,
    val type: StoryTypeApi,
    val parentId: String? = null,
    val url: String? = null,
    val path: String? = null,
    val text: String? = null,
    val title: String? = null,
    val checked: Boolean? = false,
    val steps: List<StoryStepApi> = emptyList(),
    val decoration: DecorationApi = DecorationApi(),
    val position: Int,
)