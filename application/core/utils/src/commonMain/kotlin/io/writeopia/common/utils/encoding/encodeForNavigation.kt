package io.writeopia.common.utils.encoding

fun String.encodeForNavigation(): String =
    this.replace("%", "%25") // Must be first!
        .replace("/", "%2F")
        .replace("?", "%3F")
        .replace("#", "%23")
        .replace("&", "%26")
        .replace(" ", "%20")

fun String.decodeFromNavigation(): String =
    this.replace("%20", " ")
        .replace("%26", "&")
        .replace("%23", "#")
        .replace("%3F", "?")
        .replace("%2F", "/")
        .replace("%25", "%")
