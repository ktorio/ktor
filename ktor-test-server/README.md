# ktor-test-server

The server used for integration tests of Ktor itself.

## Configuration

| Gradle property                | Description                                                |
|--------------------------------|------------------------------------------------------------|
| `ktorbuild.testServer.verbose` | Print server-side exception stack traces to the build log. |

Example:
```bash
./gradlew :ktor-client-core:jvmTest -Pktorbuild.testServer.verbose=true
```
