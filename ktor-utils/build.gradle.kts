kotlin {
    posixTargets().forEach {
        it.compilations.getByName("main").cinterops {
            val utils by creating {
                defFile("posix/interop/utils.def")
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":ktor-io"))
            }
        }
        val commonTest by getting {
            dependencies {
                api(project(":ktor-test-dispatcher"))
            }
        }

        val jsTest by getting {
            dependencies {
                implementation(kotlin("test-js"))
            }
        }
    }
}
