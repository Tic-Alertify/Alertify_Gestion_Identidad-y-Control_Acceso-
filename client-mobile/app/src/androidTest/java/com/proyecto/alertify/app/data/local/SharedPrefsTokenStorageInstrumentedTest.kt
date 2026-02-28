package com.proyecto.alertify.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests instrumentados para [SharedPrefsTokenStorage].
 *
 * Ejecutan en un dispositivo/emulador Android real para validar que
 * SharedPreferences funciona correctamente como backend de persistencia.
 */
@RunWith(AndroidJUnit4::class)
class SharedPrefsTokenStorageInstrumentedTest {

    private lateinit var tokenStorage: SharedPrefsTokenStorage

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        tokenStorage = SharedPrefsTokenStorage(context)
    }

    @After
    fun tearDown() = runTest {
        // Limpiar estado entre tests
        tokenStorage.clear()
    }

    @Test
    fun saveAndGetAccessToken() = runTest {
        tokenStorage.saveAccessToken("eyJhbGciOiJIUzI1NiJ9.test")
        val result = tokenStorage.getAccessToken()
        assertEquals("eyJhbGciOiJIUzI1NiJ9.test", result)
    }

    @Test
    fun getAccessTokenReturnsNullWhenEmpty() = runTest {
        val result = tokenStorage.getAccessToken()
        assertNull(result)
    }

    @Test
    fun clearRemovesToken() = runTest {
        tokenStorage.saveAccessToken("some-token")
        tokenStorage.clear()
        val result = tokenStorage.getAccessToken()
        assertNull(result)
    }

    @Test
    fun fullCycle_save_get_clear_getNull() = runTest {
        // save
        tokenStorage.saveAccessToken("jwt-full-cycle")

        // get → debe devolver el token
        assertEquals("jwt-full-cycle", tokenStorage.getAccessToken())

        // clear
        tokenStorage.clear()

        // get → debe ser null
        assertNull(tokenStorage.getAccessToken())
    }

    @Test
    fun overwriteToken() = runTest {
        tokenStorage.saveAccessToken("token-v1")
        tokenStorage.saveAccessToken("token-v2")
        assertEquals("token-v2", tokenStorage.getAccessToken())
    }

    @Test
    fun getAccessTokenSync_returnsTokenAfterSave() = runTest {
        tokenStorage.saveAccessToken("sync-test-token")
        assertEquals("sync-test-token", tokenStorage.getAccessTokenSync())
    }

    @Test
    fun getAccessTokenSync_returnsNullAfterClear() = runTest {
        tokenStorage.saveAccessToken("some-token")
        tokenStorage.clear()
        assertNull(tokenStorage.getAccessTokenSync())
    }
}
