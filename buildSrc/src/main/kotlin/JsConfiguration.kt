/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import com.moowork.gradle.node.npm.*
import org.gradle.api.*
import org.gradle.api.tasks.*
import org.gradle.internal.file.impl.DefaultFileMetadata.*
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.plugin.*
import java.io.*

val skipModules = listOf(
    "ktor-client-cio"
)

fun Project.configureJsModules() {
    if (skipModules.contains(project.name)) return

    configureJsTasks()

    kotlin {
        val kotlin_version: String by extra
        sourceSets {
            val jsMain by getting {
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib-js:$kotlin_version")
                }
            }

            val jsTest by getting {
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-test-js:$kotlin_version")
                    api(npm("puppeteer", "*"))
                }
            }
        }
    }

    configureTestTask()
    configurePublishing()
}

private fun Project.configureJsTasks() {
    kotlin {
        js {
            nodejs {
                testTask {
                    useMocha {
                        timeout = "10000"
                    }
                }
            }

            browser {
                testTask {
                    useKarma {
                        useChromeHeadless()
                        useConfigDirectory(File(project.rootProject.projectDir, "karma"))
                    }
                }
            }

            val main by compilations.getting
            main.kotlinOptions.apply {
                metaInfo = true
                sourceMap = true
                moduleKind = "umd"
                this.main = "noCall"
                sourceMapEmbedSources = "always"
            }

            val test by compilations.getting
            test.kotlinOptions.apply {
                metaInfo = true
                sourceMap = true
                moduleKind = "umd"
                this.main = "call"
                sourceMapEmbedSources = "always"
            }
        }
    }
}

private fun Project.configureTestTask() {
    val shouldRunJsBrowserTest = !hasProperty("teamcity") || hasProperty("enable-js-tests")

    val jsLegacyBrowserTest by tasks.getting
    jsLegacyBrowserTest.onlyIf { shouldRunJsBrowserTest }

    val jsIrBrowserTest by tasks.getting
    jsIrBrowserTest.onlyIf { shouldRunJsBrowserTest }
}

private val Project.NPM_TEMP_DIR: File get() = file("$projectDir/npm")
private val Project.NPM_DEPLOY_DIR: File get() = file("$buildDir/npm")
private val NPM_AUTH_TOKEN: String? get() = System.getenv("NPM_AUTH_TOKEN")

private val Project.jsTarget: KotlinTarget
    get() = kotlin.targets.findByName("jsLegacy") ?: kotlin.targets.findByName("js") ?: error("Fail to find js target")

private fun Project.configurePublishing() {
    NPM_AUTH_TOKEN ?: return

    val kotlin_version: String by extra

    apply(plugin = "com.moowork.node")

    val prepareTask = tasks.create<Copy>("preparePublishTask") {
        from(NPM_TEMP_DIR) {
            afterEvaluate {
                this@from.expand(properties + mapOf("kotlinDependency" to "\"kotlin\": \"$kotlin_version\""))
            }
        }

        val main by jsTarget.compilations.getting

        from(main.output.allOutputs)
        into(NPM_DEPLOY_DIR)
    }

    tasks.create<NpmTask>("publishNpm") {
        dependsOn(prepareTask)

        setWorkingDir(NPM_DEPLOY_DIR)
        setArgs(listOf("publish", "--//registry.npmjs.org/:_authToken=$NPM_AUTH_TOKEN", "--tag=latest"))
    }
}
