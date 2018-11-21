# Public API binary compatibility validator

This module allows to dump and compare public binary API to ensure binary compatibility with a previous version.
This tool is slightly adapted copy of [original Kotlin compatibility validator](https://github.com/JetBrains/kotlin/tree/master/libraries/tools/binary-compatibility-validator) by @ilya-g.

To update public API dumps use:

```bash
./gradlew :binary-compatibility-validator:test -Poverwrite.output=true 
```
