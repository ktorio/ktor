/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/
pluginManagement {
    val build_snapshot_train: String? by settings
    repositories {
        maven("https://plugins.gradle.org/m2")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        if (build_snapshot_train?.toBoolean() == true) {
            mavenLocal()
        }

        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-atomicfu/maven").credentials {
            username = "margarita.bobova"
            password =
                "eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiIxcm1UZ20wbEFKaEoiLCJhdWQiOiJjaXJjbGV0LXdlYi11aSIsIm9yZ0RvbWFpbiI6InB1YmxpYyIsIm5hbWUiOiJtYXJnYXJpdGEuYm9ib3ZhIiwiaXNzIjoiaHR0cHM6XC9cL3B1YmxpYy5qZXRicmFpbnMuc3BhY2UiLCJwZXJtX3Rva2VuIjoiSVBwZlkwQ3M1cjUiLCJwcmluY2lwYWxfdHlwZSI6IlVTRVIiLCJpYXQiOjE2NDk5MjM2NDF9.olTvoKz6KSX1rMCkid3vCSvwy-95rQTYL9gVlj7ueudTEVGqXaq1tJc37FDnKL6i6oc26XLVDK0y4G_B7ZKJGoMh77nckx-XMmRxB4Q3LZY1cXo_Mt4zD9lPxfFAfHW9RboJFgNlLWzg3OVQvMwDgHetYhnuGmlTtzCKfCW3Ke4"
        }
    }
}

dependencyResolutionManagement {
    versionCatalogs {
        val libs by creating {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
