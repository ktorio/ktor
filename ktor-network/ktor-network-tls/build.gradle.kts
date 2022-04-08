kotlin {
    linuxX64 {
        val main by compilations.getting {
            val openssl by cinterops.creating {
                defFile(project.file("linuxX64/interop/openssl.def"))
            }
        }
    }
    sourceSets {
        jvmAndNixMain {
            dependencies {
                api(project(":ktor-network"))
                api(project(":ktor-utils"))
            }
        }
        jvmTest {
            dependencies {
                api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
                api(libs.netty.handler)
            }
        }
    }
}
