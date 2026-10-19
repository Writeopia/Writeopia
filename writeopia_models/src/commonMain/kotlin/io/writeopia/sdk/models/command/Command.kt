package io.writeopia.sdk.models.command

/**
 * A command typed at the start of a line. A [commandText] ending with a space is triggered by
 * that space, so a command that is the beginning of another one ("#" of "##", "-" of "---") can
 * still be typed; the others trigger as soon as they are written.
 */
data class Command(val commandText: String, val whereToFind: WhereToFind)

enum class WhereToFind {
    START,
}

object CommandFactory {
    fun checkItem() = Command("-[]", WhereToFind.START)

    fun checkItem2() = Command("[]", WhereToFind.START)

    fun box() = Command("/box", WhereToFind.START)

    fun card() = Command("/card", WhereToFind.START)

    fun unOrderedList() = Command("- ", WhereToFind.START)

    fun h1() = Command("# ", WhereToFind.START)

    fun h2() = Command("## ", WhereToFind.START)

    fun h3() = Command("### ", WhereToFind.START)

    fun h4() = Command("#### ", WhereToFind.START)

    fun codeBlock() = Command("```", WhereToFind.START)

    fun divider() = Command("---", WhereToFind.START)

    fun defaultCommands(): Set<Command> =
        setOf(checkItem(), unOrderedList(), h1(), h2(), h3(), h4(), codeBlock())
}
