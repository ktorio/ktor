kotlin {
    posixTargets().forEach {
        it.compilations {
            val main by getting {
                cinterops {
                    val bits by creating { defFile = file("posix/interop/bits.def") }
                    val sockets by creating { defFile = file("posix/interop/sockets.def") }
                }
            }
            val test by getting {
                cinterops {
                    val testSockets by creating { defFile = file("posix/interop/testSockets.def") }
                }
            }
        }
    }

    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }
    }
}
