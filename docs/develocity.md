# Develocity usage guide

> [!NOTE]
> Build scans and remote build cache are for JetBrains developers only.
> 
> To disable publishing attempts, add `ktor.develocity.skipBuildScans=true` property
to your `~/.gradle/gradle.properties` file

Develocity is configured for this project.
That means that you can use both [build scans](https://docs.gradle.org/current/userguide/build_scans.html)
and remote [build cache](https://docs.gradle.org/current/userguide/build_cache.html)
features.

To use build scans, first you need to log in here: https://ge.jetbrains.com.
You can do that directly in Gradle:

```Bash
./gradlew :provisionDevelocityAccessKey
```

Sing in with your Google work account, and that is it.
Now your scans will automatically be published after each build, and
Gradle will read the remote build caches so that your local build may be faster.

This also allows you to check various metrics about your scans (https://ge.jetbrains.com).

Build scan logs are collected locally in the `scan-journal.log` file.
