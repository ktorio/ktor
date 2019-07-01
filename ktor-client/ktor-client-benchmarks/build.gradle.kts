import kotlinx.benchmark.gradle.*

plugins {
    id("kotlinx.benchmark")
    id("kotlin-allopen")
    id("kotlinx-atomicfu")
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

val jmh_version by extra.properties
val benchmarks_version by extra.properties

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":ktor-client:ktor-client-core"))
                implementation("org.jetbrains.kotlinx:kotlinx.benchmark.runtime:$benchmarks_version")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(project(":ktor-client:ktor-client-cio"))
                implementation(project(":ktor-client:ktor-client-apache"))
                implementation(project(":ktor-client:ktor-client-android"))
                implementation(project(":ktor-client:ktor-client-okhttp"))
                implementation(project(":ktor-client:ktor-client-jetty"))
            }
        }
//        val posixMain by getting {
//            dependencies {
//                implementation(project(":ktor-client:ktor-client-curl"))
//            }
//        }
    }
}

benchmark {
    reportsDir = "../reports"
    targets {
        register("jvm") {
            if (this is JvmBenchmarkTarget) {
                jmhVersion = "$jmh_version"
            }
        }
//        register("posix")
//        register("macosX64")
//        register("linuxX64")
//        register("mingwX64")
    }
    configurations {
        configureEach {
            iterationTime = 1
            iterationTimeUnit = "SECONDS"

            iterations = 10
            warmups = 10
        }
    }
}

/**
 * Run benchmarks:
 * ./gradlew :ktor-client:ktor-client-benchmarks:benchmark
 **/
