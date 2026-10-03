package io.writeopia.ai

import io.mockk.coEvery
import io.mockk.mockk
import io.writeopia.LocalAiRepository
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.genai.repository.GenAiRepository
import io.writeopia.model.AiProvider
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiClientsTest {

    private val userId = "user"
    private val localAiRepository: LocalAiRepository = mockk()
    private val genAiRepository: GenAiRepository = mockk()
    private val authRepository: AuthRepository = mockk()

    private fun clients(
        local: LocalAiRepository? = localAiRepository,
        cloud: GenAiRepository? = genAiRepository,
        provider: AiProvider? = null,
    ) = AiClients(local, cloud, authRepository, InMemoryAiProviderStore(provider))

    private fun localConfigured(configured: Boolean) {
        coEvery { localAiRepository.getConfiguredUrl(userId) } returns "http://localhost:11434"
        coEvery { localAiRepository.getSelectedModel(userId) } returns if (configured) "llama3" else null
    }

    private fun online(online: Boolean) {
        coEvery { authRepository.isLoggedIn() } returns online
        coEvery { authRepository.getWorkspace() } returns if (online) {
            Workspace.disconnectedWorkspace().copy(id = "workspace")
        } else {
            Workspace.disconnectedWorkspace()
        }
    }

    @Test
    fun `the phones only offer the cloud and default to it`() = runTest {
        online(true)
        val clients = clients(local = null)

        assertEquals(listOf(AiProvider.CLOUD), clients.availableProviders)
        assertEquals(AiProvider.CLOUD, clients.selectedProvider(userId))
    }

    @Test
    fun `the desktop offers both and defaults to the local AI`() = runTest {
        online(true)
        val clients = clients()

        assertEquals(listOf(AiProvider.LOCAL, AiProvider.CLOUD), clients.availableProviders)
        assertEquals(AiProvider.LOCAL, clients.selectedProvider(userId))
    }

    @Test
    fun `a saved choice is kept`() = runTest {
        online(true)
        val clients = clients()
        clients.selectProvider(userId, AiProvider.CLOUD)

        assertEquals(AiProvider.CLOUD, clients.selectedProvider(userId))
    }

    @Test
    fun `the cloud is not offered offline`() = runTest {
        online(false)

        assertEquals(listOf(AiProvider.LOCAL), clients().offeredProviders())
        assertEquals(emptyList(), clients(local = null).offeredProviders())
    }

    @Test
    fun `the cloud is offered online`() = runTest {
        online(true)

        assertEquals(listOf(AiProvider.LOCAL, AiProvider.CLOUD), clients().offeredProviders())
        assertEquals(listOf(AiProvider.CLOUD), clients(local = null).offeredProviders())
    }

    @Test
    fun `a saved cloud choice shows as local while offline`() = runTest {
        online(false)

        assertEquals(AiProvider.LOCAL, clients(provider = AiProvider.CLOUD).selectedProvider(userId))
    }

    @Test
    fun `a saved choice that this app cannot offer is ignored`() = runTest {
        online(true)
        assertEquals(AiProvider.CLOUD, clients(local = null, provider = AiProvider.LOCAL).selectedProvider(userId))
    }

    @Test
    fun `local AI answers when picked and configured`() = runTest {
        localConfigured(true)
        online(true)

        val resolved = clients(provider = AiProvider.LOCAL).resolve(userId)

        assertIs<ResolvedAi.Local>(resolved)
        assertEquals("llama3", resolved.model)
        assertEquals("http://localhost:11434", resolved.url)
    }

    @Test
    fun `local AI without a model falls back to the cloud`() = runTest {
        localConfigured(false)
        online(true)

        assertIs<ResolvedAi.Cloud>(clients(provider = AiProvider.LOCAL).resolve(userId))
    }

    @Test
    fun `cloud answers when picked and online`() = runTest {
        localConfigured(true)
        online(true)

        assertIs<ResolvedAi.Cloud>(clients(provider = AiProvider.CLOUD).resolve(userId))
    }

    @Test
    fun `cloud offline falls back to the local AI`() = runTest {
        localConfigured(true)
        online(false)

        assertIs<ResolvedAi.Local>(clients(provider = AiProvider.CLOUD).resolve(userId))
    }

    @Test
    fun `nothing answers offline without a local model`() = runTest {
        localConfigured(false)
        online(false)

        assertNull(clients(provider = AiProvider.CLOUD).resolve(userId))
        assertNull(clients(local = null).resolve(userId))
    }

    @Test
    fun `the disconnected workspace is not online`() = runTest {
        coEvery { authRepository.isLoggedIn() } returns true
        coEvery { authRepository.getWorkspace() } returns Workspace.disconnectedWorkspace()

        assertTrue(!clients().isCloudAiReady())
    }
}
