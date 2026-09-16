package io.writeopia.update.model

enum class DesktopPlatform(
    val downloadFileName: String
) {
    WINDOWS("Writeopia.msi"),
    LINUX("Writeopia.deb"),
    MAC("Writeopia.dmg")
}
