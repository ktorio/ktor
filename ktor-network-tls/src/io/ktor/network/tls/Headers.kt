package io.ktor.network.tls


class TLSRecordHeader {
    var type: TLSRecordType = TLSRecordType.Handshake
    var version: TLSVersion = TLSVersion.TLS12
    var length: Int = 0
}

class TLSHandshakeHeader {
    var type: TLSHandshakeType = TLSHandshakeType.HelloRequest
    var length: Int = 0

    var version: TLSVersion = TLSVersion.TLS12

    var random = ByteArray(32)

    var suitesCount = 0
    var suites = ShortArray(255)

    var sessionIdLength = 0
    var sessionId = ByteArray(32)

    var serverName: String? = null
}