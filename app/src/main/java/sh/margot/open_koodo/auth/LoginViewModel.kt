package sh.margot.open_koodo.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sh.margot.open_koodo.network.KoodoAuthService

class LoginViewModel : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private var profile: KoodoAuthService.Profile? = null
    private var otpSecret: String? = null

    fun start() { _state.value = LoginState.NeedCredentials }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = LoginState.Error("Enter your email and password")
            return
        }
        viewModelScope.launch {
            _state.value = LoginState.Authenticating
            KoodoAuthService.login(email.trim(), password)
                .onSuccess { p ->
                    profile = p
                    if (p.twoFactorFlag && !p.phoneNumber.isNullOrBlank()) sendOtp()
                    else _state.value = LoginState.Success
                }
                .onFailure { _state.value = LoginState.Error(it.message ?: "Login failed") }
        }
    }

    fun sendOtp() {
        viewModelScope.launch {
            _state.value = LoginState.Authenticating
            KoodoAuthService.generateOtp(profile ?: return@launch)
                .onSuccess { secret ->
                    otpSecret = secret
                    val masked = profile?.phoneNumber?.filter { it.isDigit() }
                        ?.takeLast(4)?.let { "•••-•••-$it" } ?: ""
                    _state.value = LoginState.NeedTwoFactor(masked)
                }
                .onFailure { _state.value = LoginState.Error(it.message ?: "Couldn't send code") }
        }
    }

    fun submitCode(code: String) {
        val secret = otpSecret ?: run {
            _state.value = LoginState.Error("No active code — tap Resend")
            return
        }
        if (code.trim().length < 4) {
            _state.value = LoginState.Error("Enter the full SMS code")
            return
        }
        viewModelScope.launch {
            _state.value = LoginState.VerifyingCode
            KoodoAuthService.validateOtp(secret, code)
                .onSuccess { valid ->
                    _state.value = if (valid) LoginState.Success
                    else LoginState.Error("Invalid or expired code")
                }
                .onFailure { _state.value = LoginState.Error(it.message ?: "Verification failed") }
        }
    }
}
