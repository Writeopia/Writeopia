package com.github.leandroborgesferreira.storyteller.serialization.data

import kotlinx.serialization.Serializable

@Serializable
data class StoryTypeApi(val name: String, val number: Int)