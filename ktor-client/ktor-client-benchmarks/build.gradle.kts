import org.jetbrains.gradle.benchmarks.*

plugins {
    id("org.jetbrains.gradle.benchmarks.plugin")
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
                implementation("org.jetbrains.gradle.benchmarks:runtime:$benchmarks_version")
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
    }
}

benchmark {
    configurations {
        (register("jvm") as? JvmBenchmarkConfiguration)?.apply {
            jmhVersion = "$jmh_version"
        }
    }

    defaults.apply {
        iterationTime = 100
        iterations = 3
    }
}

/**
 * Run benchmarks:
 * ./gradlew :ktor-client:ktor-client-benchmarks:benchmark
 **/
