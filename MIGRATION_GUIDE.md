## Ktor Migration Guide

While Ktor provides migration support in the code itself (by using the `@Deprecated` annotation), this document
serves as a reference point for all migrations as of version 1.6.0. 


### Upgrading to 1.6.0

* `TestApplicationCall.requestHandled` has been [deprecated](https://youtrack.jetbrains.com/issue/KTOR-2712). For proper
validation, it is recommended to check the corresponding status, header, or content of the request, depending on the 
  system under test. 
* [Updates to `basic` and `digest` authentication providers](https://youtrack.jetbrains.com/issue/KTOR-2637), deprecating
`sendWithoutRequest` property, and favouring `sendWithoutRequest` function. Also, `username` and `password` properties are
  deprecated in favour of `credentials` function, which takes as parameter `BasicAuthCredentials` or `DigestAuthCredentials` data classes.
* Application extension functions `uninstallAllFeatures`, `uninstall`, and `uninstallFeature` have been [deprecated](https://youtrack.jetbrains.com/issue/KTOR-367). Currently, there are no replacements
for these functions. Please [consider commenting on your use-cases on the corresponding issue](https://youtrack.jetbrains.com/issue/KTOR-2696).
* `ApplicationCall.locationOrNull` has been [deprecated](https://youtrack.jetbrains.com/issue/KTOR-1684). Please use `ApplicationCall.location`.
* `ContentNegotiation` constructor has been [deprecated](https://youtrack.jetbrains.com/issue/KTOR-2194) as it will become internal. It should not be 
explicitly called from the application code. Please consider passing in the necessary configuration options during installation of the `ContentNegotiation` plugin.
*  `ByteChannelSequentialBase.readByteOrder` and `ByteChannelSequentialBase.writeByOrder` have been [deprecated](https://youtrack.jetbrains.com/issue/KTOR-1094). Please read/write
using big endian, and call the `ByteChannelSequentialBase.reverseByteOrder()` extension function if necessary.
* `AbstractInput` class has been [deprecated](https://youtrack.jetbrains.com/issue/KTOR-2204) and will be merged with `Input` class as of version 2.0.0.
* `AbstractOutput` class has been [deprecated](https://youtrack.jetbrains.com/issue/KTOR-2204) and will be merged with `Output` class as of version 2.0.0.
* `IoBuffer` class has been [deprecated](https://youtrack.jetbrains.com/issue/KTOR-2204). Please use `ChunkBuffer` instead.


