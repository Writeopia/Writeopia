package io.writeopia.sdk.models.command

data class Command(val commandText: String, val whereToFind: WhereToFind)

enum class WhereToFind {
    START,
}

object CommandFactory {
    fun checkItem() = Command("-[]", WhereToFind.START)

    fun checkItem2() = Command("[]", WhereToFind.START)

    fun box() = Command("/box", WhereToFind.START)

    fun unOrderedList() = Command("-", WhereToFind.START)

    fun h1() = Command("#", WhereToFind.START)

    fun h2() = Command("##", WhereToFind.START)

    fun h3() = Command("###", WhereToFind.START)

    fun h4() = Command("####", WhereToFind.START)

    fun codeBlock() = Command("```", WhereToFind.START)

    fun divider() = Command("---", WhereToFind.START)

    fun defaultCommands(): Set<Command> =
        setOf(checkItem(), unOrderedList(), h1(), h2(), h3(), h4(), codeBlock())
}
