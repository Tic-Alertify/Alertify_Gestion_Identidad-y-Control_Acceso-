package com.proyecto.alertify.app.data.local

import com.proyecto.alertify.app.data.auth.AuthSessionManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios JVM para [TokenStorage] y [AuthSessionManager].
 *
 * Usa una implementación fake en memoria (sin dependencia de Android Context)
 * para validar el contrato de la interfaz y la lógica del gestor de sesión.
 */
class TokenStorageTest {

    private lateinit var tokenStorage: TokenStorage
    private lateinit var sessionManager: AuthSessionManager

    /** Implementación fake en memoria para tests JVM */
    private class FakeTokenStorage : TokenStorage {
        private var token: String? = null

        override suspend fun saveAccessToken(token: String) {
            this.token = token
        }

        override suspend fun getAccessToken(): String? {
            return token?.takeIf { it.isNotBlank() }
        }

        override suspend fun clear() {
            token = null
        }

        override fun getAccessTokenSync(): String? {
            return token?.takeIf { it.isNotBlank() }
        }
    }

    @Before
    fun setUp() {
        tokenStorage = FakeTokenStorage()
        sessionManager = AuthSessionManager(tokenStorage)
    }

    // ── TokenStorage contract tests ──

    @Test
    fun `saveAccessToken stores the token`() = runTest {
        tokenStorage.saveAccessToken("jwt-abc-123")
        assertEquals("jwt-abc-123", tokenStorage.getAccessToken())
    }

    @Test
    fun `getAccessToken returns null when no token saved`() = runTest {
        assertNull(tokenStorage.getAccessToken())
    }

    @Test
    fun `clear removes the stored token`() = runTest {
        tokenStorage.saveAccessToken("jwt-abc-123")
        tokenStorage.clear()
        assertNull(tokenStorage.getAccessToken())
    }

    @Test
    fun `save then get then clear then get null - full cycle`() = runTest {
        // save
        tokenStorage.saveAccessToken("my-jwt-token")
        // get
        assertEquals("my-jwt-token", tokenStorage.getAccessToken())
        // clear
        tokenStorage.clear()
        // get null
        assertNull(tokenStorage.getAccessToken())
    }

    @Test
    fun `saving a new token overwrites the previous one`() = runTest {
        tokenStorage.saveAccessToken("token-v1")
        tokenStorage.saveAccessToken("token-v2")
        assertEquals("token-v2", tokenStorage.getAccessToken())
    }

    @Test
    fun `blank token is treated as null`() = runTest {
        tokenStorage.saveAccessToken("   ")
        assertNull(tokenStorage.getAccessToken())
    }

    // ── AuthSessionManager tests ──

    @Test
    fun `isLoggedIn returns false when no token`() = runTest {
        assertFalse(sessionManager.isLoggedIn())
    }

    @Test
    fun `isLoggedIn returns true after onLoginSuccess`() = runTest {
        sessionManager.onLoginSuccess("valid-jwt")
        assertTrue(sessionManager.isLoggedIn())
    }

    @Test
    fun `logout clears the session`() = runTest {
        sessionManager.onLoginSuccess("valid-jwt")
        sessionManager.logout()
        assertFalse(sessionManager.isLoggedIn())
    }

    @Test
    fun `getAccessToken returns stored token via session manager`() = runTest {
        sessionManager.onLoginSuccess("my-token")
        assertEquals("my-token", sessionManager.getAccessToken())
    }

    @Test
    fun `getAccessToken returns null after logout`() = runTest {
        sessionManager.onLoginSuccess("my-token")
        sessionManager.logout()
        assertNull(sessionManager.getAccessToken())
    }

    // ── Sync access tests ──

    @Test
    fun `getAccessTokenSync returns token after save`() = runTest {
        tokenStorage.saveAccessToken("sync-token")
        assertEquals("sync-token", tokenStorage.getAccessTokenSync())
    }

    @Test
    fun `getAccessTokenSync returns null when empty`() {
        assertNull(tokenStorage.getAccessTokenSync())
    }

    @Test
    fun `isLoggedInSync returns false when no token`() {
        assertFalse(sessionManager.isLoggedInSync())
    }

    @Test
    fun `isLoggedInSync returns true after onLoginSuccess`() = runTest {
        sessionManager.onLoginSuccess("valid-jwt")
        assertTrue(sessionManager.isLoggedInSync())
    }
}
