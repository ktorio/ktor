package io.ktor.client.engine.winhttp

enum class WinHttpSecurityProtocol(val value: Int) {
    Default(0),
    Tls10(0x00000080),
    Tls11(0x00000200),
    Tls12(0x00000800),
}
