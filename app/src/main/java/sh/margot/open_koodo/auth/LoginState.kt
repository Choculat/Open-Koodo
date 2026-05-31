package sh.margot.open_koodo.auth

import sh.margot.open_koodo.network.KoodoAuthService

sealed class LoginState {
    object Idle : LoginState()
    object NeedCredentials : LoginState()
    object Authenticating : LoginState()
    data class NeedTwoFactor(val phoneMasked: String) : LoginState()
    object VerifyingCode : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}
