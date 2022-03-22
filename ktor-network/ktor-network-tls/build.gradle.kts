val netty_version: String by project.extra

kotlin {
    jvm {
        testRuns.all {
            executionTask.get().apply {
//                systemProperty("javax.net.debug", "all") //debug ssl engine
            }
        }
    }

    val paths = listOf(
        "/usr/include",
        "/usr/include/x86_64-linux-gnu"
    )
    nixTargets().forEach {
        val main by it.compilations.getting {
            val openssl by cinterops.creating {
                defFile(project.file("nix/interop/openssl.def"))
                compilerOpts(paths.map { "-I$it" })
                includeDirs.allHeaders(paths)
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
                api("io.netty:netty-handler:$netty_version")
            }
        }
    }
}
