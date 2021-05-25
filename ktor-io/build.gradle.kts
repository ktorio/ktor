import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

val ideaActive: Boolean by extra

val nativeTargets: List<KotlinNativeTarget> by extra
kotlin {
    nativeTargets.forEach {
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

// Hack: register the Native interop klibs as outputs of Kotlin source sets:
        val bitsMain by creating { dependsOn(commonMain) }
        val socketsMain by creating { dependsOn(commonMain) }

        val posixMain by getting {
            dependsOn(bitsMain)
            dependsOn(socketsMain)
        }

        if (!ideaActive) {
            apply(from = "$rootDir/gradle/interop-as-source-set-klib.gradle")
            val registerInteropAsSourceSetOutput: groovy.lang.Closure<*> by extra
            afterEvaluate {
                registerInteropAsSourceSetOutput("bits", bitsMain)
                registerInteropAsSourceSetOutput("sockets", socketsMain)
            }
        }
    }
}
