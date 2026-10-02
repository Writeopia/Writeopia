package io.writeopia.api.core.auth.utils

import java.sql.SQLException

private const val SQLSTATE_UNIQUE_VIOLATION = "23505"

/** True when this exception, or any of its causes, is a Postgres unique-constraint violation. */
fun Throwable.isUniqueViolation(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SQLException && current.sqlState == SQLSTATE_UNIQUE_VIOLATION) {
            return true
        }

        current = current.cause
    }
    return false
}
