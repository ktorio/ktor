description = "Ktor client Basic Auth support"

project.ext.set("commonStructure", false)

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-core"))
            api(project(":ktor-client:ktor-client-features:ktor-client-auth"))
        }
    }
    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-cio"))
            api(project(":ktor-client:ktor-client-tests"))
        }
    }
}
