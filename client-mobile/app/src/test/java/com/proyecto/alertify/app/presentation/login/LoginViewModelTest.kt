package com.proyecto.alertify.app.presentation.login

import com.proyecto.alertify.app.data.auth.AuthError
import com.proyecto.alertify.app.data.auth.AuthRepository
import com.proyecto.alertify.app.data.auth.AuthSessionManager
import com.proyecto.alertify.app.data.local.TokenStorage
import com.proyecto.alertify.app.network.ApiResult
import com.proyecto.alertify.app.network.dto.LoginResponse
import com.proyecto.alertify.app.network.dto.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests unitarios para [LoginViewModel].
 *
 * Usa fakes de [AuthRepository] y [TokenStorage] para aislar la lógica
 * del ViewModel sin dependencias de red ni Android framework.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeTokenStorage: FakeTokenStorage
    private lateinit var sessionManager: AuthSessionManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeTokenStorage = FakeTokenStorage()
        sessionManager = AuthSessionManager(fakeTokenStorage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Login exitoso ───────────────────────────────────────────────────

    @Test
    fun `login exitoso emite Loading, Success y NavigateToMain`() = runTest {
        val fakeRepo = FakeAuthRepository(
            loginResult = ApiResult.Success(fakeLoginResponse())
        )
        val vm = LoginViewModel(fakeRepo, sessionManager)

        val events = mutableListOf<LoginUiEvent>()
        val collectJob = launch { vm.events.collect { events.add(it) } }

        vm.login("user@test.com", "Password1")
        advanceUntilIdle()

        // Estado final debe ser Success
        assertEquals(LoginUiState.Success, vm.uiState.value)

        // Debe emitir NavigateToMain
        assertTrue(events.any { it is LoginUiEvent.NavigateToMain })

        // Tokens deben estar persistidos
        assertEquals("fake_access_token", fakeTokenStorage.getAccessToken())
        assertEquals("fake_refresh_token", fakeTokenStorage.getRefreshToken())

        collectJob.cancel()
    }

    // ── Login con error de credenciales ─────────────────────────────────

    @Test
    fun `login con error 401 emite estado Error y evento ShowError`() = runTest {
        val fakeRepo = FakeAuthRepository(
            loginResult = ApiResult.Error(httpCode = 401)
        )
        val vm = LoginViewModel(fakeRepo, sessionManager)

        val events = mutableListOf<LoginUiEvent>()
        val collectJob = launch { vm.events.collect { events.add(it) } }

        vm.login("user@test.com", "wrong")
        advanceUntilIdle()

        // Estado final debe ser Error
        val state = vm.uiState.value
        assertTrue(state is LoginUiState.Error)

        // Debe emitir ShowError con InvalidCredentials
        val errorEvent = events.filterIsInstance<LoginUiEvent.ShowError>().firstOrNull()
        assertTrue(errorEvent != null)
        assertTrue(errorEvent!!.authError is AuthError.InvalidCredentials)

        collectJob.cancel()
    }

    // ── checkSession con token existente ────────────────────────────────

    @Test
    fun `checkSession con token existente emite NavigateToMain`() = runTest {
        // Pre-cargar token
        fakeTokenStorage.saveAccessTokenSync("existing_token")

        val fakeRepo = FakeAuthRepository()
        val vm = LoginViewModel(fakeRepo, sessionManager)

        val events = mutableListOf<LoginUiEvent>()
        val collectJob = launch { vm.events.collect { events.add(it) } }

        vm.checkSession()
        advanceUntilIdle()

        assertTrue(events.any { it is LoginUiEvent.NavigateToMain })

        collectJob.cancel()
    }

    // ── checkSession sin token ──────────────────────────────────────────

    @Test
    fun `checkSession sin token no emite evento`() = runTest {
        val fakeRepo = FakeAuthRepository()
        val vm = LoginViewModel(fakeRepo, sessionManager)

        val events = mutableListOf<LoginUiEvent>()
        val collectJob = launch { vm.events.collect { events.add(it) } }

        vm.checkSession()
        advanceUntilIdle()

        assertTrue(events.isEmpty())

        // Estado se mantiene en Idle
        assertEquals(LoginUiState.Idle, vm.uiState.value)

        collectJob.cancel()
    }

    // ── Login con error de red ──────────────────────────────────────────

    @Test
    fun `login con error de red emite NetworkError`() = runTest {
        val fakeRepo = FakeAuthRepository(
            loginResult = ApiResult.Error(throwable = java.io.IOException("Sin conexión"))
        )
        val vm = LoginViewModel(fakeRepo, sessionManager)

        val events = mutableListOf<LoginUiEvent>()
        val collectJob = launch { vm.events.collect { events.add(it) } }

        vm.login("user@test.com", "pass")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state is LoginUiState.Error)
        assertTrue((state as LoginUiState.Error).authError is AuthError.NetworkError)

        collectJob.cancel()
    }

    // ── resetState ──────────────────────────────────────────────────────

    @Test
    fun `resetState vuelve a Idle tras error`() = runTest {
        val fakeRepo = FakeAuthRepository(
            loginResult = ApiResult.Error(httpCode = 500)
        )
        val vm = LoginViewModel(fakeRepo, sessionManager)

        vm.login("user@test.com", "pass")
        advanceUntilIdle()
        assertTrue(vm.uiState.value is LoginUiState.Error)

        vm.resetState()
        assertEquals(LoginUiState.Idle, vm.uiState.value)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun fakeLoginResponse() = LoginResponse(
        accessToken = "fake_access_token",
        refreshToken = "fake_refresh_token",
        user = UserDto(id = 1, email = "user@test.com", username = "testuser", roles = listOf("user"))
    )
}

// ── Fakes ────────────────────────────────────────────────────────────────

/**
 * Fake de [AuthRepository] que devuelve resultados predefinidos
 * sin realizar llamadas HTTP reales.
 *
 * Extiende [AuthRepository] pasando un AuthApi dummy (nunca se usa porque
 * los métodos override no delegan al super). Requiere que AuthRepository
 * sea open class con métodos open.
 */
private class FakeAuthRepository(
    private val loginResult: ApiResult<LoginResponse> = ApiResult.Error(httpCode = 500)
) : AuthRepository(object : com.proyecto.alertify.app.network.AuthApi {
    override suspend fun login(request: com.proyecto.alertify.app.network.dto.LoginRequest) =
        throw UnsupportedOperationException("Fake")
    override suspend fun register(request: com.proyecto.alertify.app.network.dto.RegisterRequest) =
        throw UnsupportedOperationException("Fake")
    override suspend fun refresh(request: com.proyecto.alertify.app.network.dto.RefreshRequest) =
        throw UnsupportedOperationException("Fake")
}) {

    override suspend fun login(email: String, password: String): ApiResult<LoginResponse> {
        return loginResult
    }
}

/**
 * Implementación en-memoria de [TokenStorage] para tests unitarios.
 * No depende de SharedPreferences ni de Context.
 */
private class FakeTokenStorage : TokenStorage {

    private var accessToken: String? = null
    private var refreshToken: String? = null

    override suspend fun saveAccessToken(token: String) { accessToken = token }
    override suspend fun getAccessToken(): String? = accessToken
    override fun getAccessTokenSync(): String? = accessToken
    override suspend fun saveRefreshToken(token: String) { refreshToken = token }
    override suspend fun getRefreshToken(): String? = refreshToken
    override fun getRefreshTokenSync(): String? = refreshToken
    override fun saveAccessTokenSync(token: String) { accessToken = token }
    override fun saveRefreshTokenSync(token: String) { refreshToken = token }
    override suspend fun clear() { accessToken = null; refreshToken = null }
    override fun clearSync() { accessToken = null; refreshToken = null }
}
