# ktor-test-server

The server used for integration tests of Ktor itself.

- `server/` is the server application.
- The root project is the `test-server` Gradle plugin. Its `TestServerService` runs the server in a separate JVM
  process, started before the first test task that needs it.
  The server output is written to `build/test-server/test-server.log` in the root project of the Ktor build.
- On `OutOfMemoryError` the server writes a heap dump (`java_pid<pid>.hprof`) to the same directory and exits.

To run the server outside of tests, use `./gradlew :ktor-test-server:server:run`.

## Configuration

| Gradle property                | Description                                                |
|--------------------------------|------------------------------------------------------------|
| `ktorbuild.testServer.verbose` | Print server-side exception stack traces to the build log. |

Example:
```bash
./gradlew :ktor-client-core:jvmTest -Pktorbuild.testServer.verbose=true
```
