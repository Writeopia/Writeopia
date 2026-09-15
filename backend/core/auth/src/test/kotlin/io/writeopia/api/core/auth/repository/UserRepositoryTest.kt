package io.writeopia.api.core.auth.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for UserRepository functions.
 *
 * These tests verify:
 * 1. User creation (insertUser)
 * 2. User deletion (deleteUserById) with affected row count
 * 3. User retrieval after creation
 * 4. Correct row count when deleting existing vs non-existing users
 */
class UserRepositoryTest {

    private lateinit var db: WriteopiaDbBackend
    private val testUserId = "test-user-123"
    private val testEmail = "test@example.com"

    @BeforeTest
    fun setup() {
        // Create an in-memory SQLite database for testing
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WriteopiaDbBackend.Schema.create(driver)
        db = WriteopiaDbBackend(driver)
    }

    @AfterTest
    fun teardown() {
        // Clean up test data
        db.userEntityQueries.deleteUser(testUserId)
    }

    @Test
    fun `should successfully insert a new user`() {
        // Given: user data
        val name = "Test User"
        val password = "hashedPassword123"
        val salt = "randomSalt123"

        // When: inserting a user
        db.insertUser(
            id = testUserId,
            name = name,
            email = testEmail,
            password = password,
            salt = salt,
            enabled = true
        )

        // Then: user should be retrievable from database
        val retrievedUser = db.getUserById(testUserId)
        assertNotNull(retrievedUser, "User should be found after insertion")
        assertEquals(testUserId, retrievedUser.id)
        assertEquals(name, retrievedUser.name)
        assertEquals(testEmail, retrievedUser.email)
        assertEquals(password, retrievedUser.password)
        assertEquals(salt, retrievedUser.salt)
        assertEquals(true, retrievedUser.enabled)
    }

    @Test
    fun `should return 1 affected row when deleting existing user`() = runTest {
        // Given: an existing user
        db.insertUser(
            id = testUserId,
            name = "Test User",
            email = testEmail,
            password = "hashedPassword123",
            salt = "randomSalt123",
            enabled = true
        )

        // Verify user exists
        val userBeforeDelete = db.getUserById(testUserId)
        assertNotNull(userBeforeDelete, "User should exist before deletion")

        // When: deleting the user
        val affectedRows = db.deleteUserById(testUserId)

        // Then: should return 1 affected row
        assertEquals(1L, affectedRows, "Should return 1 affected row for successful deletion")

        // And: user should no longer exist
        val userAfterDelete = db.getUserById(testUserId)
        assertNull(userAfterDelete, "User should not exist after deletion")
    }

    @Test
    fun `should return 0 affected rows when deleting non-existing user`() = runTest {
        // Given: a user ID that doesn't exist in the database
        val nonExistingUserId = "non-existing-user-999"

        // Verify user doesn't exist
        val user = db.getUserById(nonExistingUserId)
        assertNull(user, "User should not exist before deletion attempt")

        // When: attempting to delete non-existing user
        val affectedRows = db.deleteUserById(nonExistingUserId)

        // Then: should return 0 affected rows
        assertEquals(0L, affectedRows, "Should return 0 affected rows when user doesn't exist")
    }

    @Test
    fun `should insert user and then successfully delete it`() = runTest {
        // Given: insert a new user
        val userId = "test-delete-user-456"
        val email = "delete@example.com"

        db.insertUser(
            id = userId,
            name = "Delete Test User",
            email = email,
            password = "password",
            salt = "salt",
            enabled = true
        )

        // Verify user was created
        val createdUser = db.getUserById(userId)
        assertNotNull(createdUser, "User should exist after creation")
        assertEquals(userId, createdUser.id)
        assertEquals(email, createdUser.email)

        // When: deleting the user
        val affectedRows = db.deleteUserById(userId)

        // Then: deletion should succeed
        assertEquals(1L, affectedRows, "Deletion should affect 1 row")

        // And: user should no longer exist
        val deletedUser = db.getUserById(userId)
        assertNull(deletedUser, "User should not exist after deletion")
    }

    @Test
    fun `should retrieve user by email after insertion`() {
        // Given: insert a user
        db.insertUser(
            id = testUserId,
            name = "Test User",
            email = testEmail,
            password = "password",
            salt = "salt",
            enabled = true
        )

        // When: retrieving user by email
        val user = db.getUserByEmail(testEmail)

        // Then: user should be found
        assertNotNull(user, "User should be found by email")
        assertEquals(testUserId, user.id)
        assertEquals(testEmail, user.email)
    }

    @Test
    fun `should return null when user does not exist`() {
        // Given: a non-existing user ID
        val nonExistingId = "non-existing-id"

        // When: retrieving user by ID
        val user = db.getUserById(nonExistingId)

        // Then: should return null
        assertNull(user, "Should return null for non-existing user")
    }
}
