package model

data class LoginResponse(
    val success: Boolean = false,
    val message: String? = null,
    val token: String? = null
) 