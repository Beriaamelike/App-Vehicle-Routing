package model

data class RegisterRequest(
    val username: String,
    val password: String,
    val roles: List<String>
)
