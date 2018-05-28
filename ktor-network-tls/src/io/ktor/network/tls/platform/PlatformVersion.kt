package io.ktor.network.tls.platform

internal class PlatformVersion(val major: String, val minor: Int = -1) {

    companion object {
        operator fun invoke(rawVersion: String): PlatformVersion {
            val versionString = rawVersion.split("_")
            if (versionString.size == 2) {
                val (major, minor) = versionString
                return PlatformVersion(major, minor.toInt())
            }

            return PlatformVersion(major = rawVersion)
        }
    }
}

internal val platformVersion: PlatformVersion = PlatformVersion(System.getProperty("java.version"))
