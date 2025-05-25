package model

data class RegisterRequest(
    val name: String,
    val username: String,
    val password: String,
    val roles: List<String>
)
