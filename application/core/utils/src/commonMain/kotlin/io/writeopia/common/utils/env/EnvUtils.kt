package io.writeopia.common.utils.env

expect object EnvUtils {
    fun getAdminKey(): String?

    fun getLocalAiUrl(): String?
}
