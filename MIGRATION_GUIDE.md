## Ktor Migration Guide

While Ktor provides migration support in the code itself (by using the `@Deprecated` annotation), this document
serves as a reference point for all migrations as of version 1.6.0. 


### Upgrading to 1.6.0

* `TestApplicationCall.requestHandled` is now [deprecated](https://youtrack.jetbrains.com/issue/KTOR-2712). For proper
validation, it is recommended to check the corresponding status, header, or content of the request, depending on the 
  system under test. 
  
