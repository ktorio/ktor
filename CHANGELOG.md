# 0.9.1
> Published 29 Jan 2018

* Support for blocking servlets and GAE
* `Headers` and `Parameters` types instead of `ValuesMap`
* Velocity templates support
* Unsafe (Forbidden) headers checks added
* Deprecated `Resource` type
* Added support for extensible version providers to ConditionalHeaders feature 
* Engine-specific configuration support for application.conf
* Added filtering and customisation of log level to CallLogging feature
* Added ability to skip authentication using a predicate, add documentation to Authentication feature
* Introduced auth0 JWT/JWKS authentication (#266)
* ktor http client DSL improvements
* CIO engine improvements, stability and performance
* Introduced [OutgoingContent] properties `contentLength`, `contentType` and `status` 
* Upgrade Netty in the corresponding engine implementation
* Introduced `shareWorkGroup` option for Netty engine
* More documentation
* Bump versions of dependencies

# 0.9.0
> Published 31 Oct 2017

* Package structure reworked
* Packages and maven groupId renamed org.jetbrains.ktor -> io.ktor
* Server-related artifacts having ktor-server-* name prefix (ktor-netty -> ktor-server-netty)
* Application Host renamed to Application Engine  
* FinalContent renamed to OutgoingContent as opposite to IncomingContent (introduced in 0.4.0)
* Added Application Engine configure facilities so one can specify thread pool size or some engine-specific parameter
* Initial idiomatic ktor HTTP client implementation (artifacts prefixed with ktor-client-*)
* Metrics support, DropWizard integration
* Improve routing API, tune resolution mechanics, hide some implementation details
* ContentNegotiation feature to support variable content on send and receive 
* Jackson support
* Experimental pure kotlin application engine on coroutines (CIOApplicationEngine) and CIO-based http client backend
* Improved stability under load
* Status pages processing improvements 
* A lot of documentation

# 0.4.0
> Published 16 Aug 2017

* Built with Kotlin 1.1.4
* Refactored receive/response pipelines and moved them into respective ApplicationRequest & ApplicationResponse classes 
* Fixes, improvements and integration tests for HTTP/2 support
* Update `ContentType` to treat all parts case insensitively
* Remove `ApplicationLog` and use SLF4J `Logger` directly
* Add HttpMethod.Patch and respective builder functions 
* `routing` function will now install `Routing` feature or use existing installed feature for easier modules 
* Convert sessions to proper feature, support multiple sessions, improve DSL 
* HeadRequestSupport feature is renamed to AutoHeadResponse (with deprecated typealias)
* Replace ApplicationTransform with receive pipeline
* Introduce send/receive pipelines for all call pipelines
* Gson application feature for JSON transformation of incoming & outgoing data objects
* Added HttpBin sample (thanks to @jmfayard)
* Employ `DslMarker` annotation to prevent accidental use of route functions in get/post handlers 
* Improve diagnostics for untransformed content
* Ensure missing file (`FileNotFoundException`) can be handled properly with `StatusPages` feature 
* Websocket fixes for large frames, fragmentation and more
* Support for specifying config file with command line
* Improvements in Servlet-based hosts
* Memory allocation and performance optimisations
* Add Apache 2 LICENSE file
* Add documentation to some types
* New sample for static content
* Bump versions of dependencies

# 0.3.3
> Published 22 Jun 2017

* Execution model slightly changed to avoid global executors. ApplicationEnvironment doesn't provide `executor` anymore
* Websockets refactored with channels instead of callback functions
* Fixed bug with compression not preserving status code (thanks Diego Rocha)
* Fixes in Netty support: performance issues under heavy load and keep-alive connections, cancellation, closed sockets
* Fixes in session serialization, enums support
* Optimisations in Servlet and Jetty hosts, fixes in edge cases
* Fixes in chat sample

# 0.3.2
> Published 24 Apr 2017

* Fix bug in byte array response that missed Content-Length header
* Fix default encoding in FreeMarker to be UTF-8
* Fix FreeMarker writer
* Fix charset for text/* content types to be UTF-8 by default
* Fix Kweet sample application in kweet deletion
* Fix form authentication to adhere to removal of post parameters from `parameters`
* Rework static content DSL to improve usability
* Improve Accept header handling in routes
* Support local class in locations, improve diagnostics
* Add servlet host tests running in Jetty, but using servlet deployment model
* Benchmarks can now measure GC performance (and any other JMH profiler)
* Performance optimisations


# 0.3.1
> Published 7 Apr 2017

* Replace routing function `contentType` with `accept`
* Major refactoring in internal host system to simplify and unify code
  * Use `embeddedServer(Netty, …)` instead of `embeddedNettyServer(…)`
  * Support automatic reloading in embedded hosts
  * Fix ApplicationTransform problems preventing Freemarker templates from working
* Fix json and logback dependencies to avoid propagating to clients 
* Bug fixes & performance optimisations
* Documentation

# 0.3.0
> Published 8 Mar 2017

* Major refactor to coroutines