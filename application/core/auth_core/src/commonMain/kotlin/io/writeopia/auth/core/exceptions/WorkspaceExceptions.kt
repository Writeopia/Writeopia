package io.writeopia.auth.core.exceptions

/**
 * Exceptions related to adding users to workspaces
 */
class UserAlreadyInWorkspaceException : Exception("User is already in this workspace")

class UserNotFoundException : Exception("User not found")
