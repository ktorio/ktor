# 0.9.5
> Published 19 Sept 2018

* Added shorthand client functions for HEAD, OPTIONS, PATCH and DELETE methods (#562)
* URLBuilder's parser improved (#553, #567)
* Improved client's cookie matching and processing
* Introduced CallId feature
* Added MDC support to CallLogging feature
* Fixed setting charset encoding for non-text content types
* Added `respondOutputStream { }` response function
* Migrated to Kotlin 1.2.70
* Split Infrastructure phase into Monitoring and Features phases

# 0.9.4
> Published 29 Aug 2018

* Added multiplatform client support (android and ios)
* Added `Android` client engine (`UrlConnection`)
* Added `OkHttp` client engine (Android support)
* Added `Jackson` feature support
* Added `Ios` client engine
* Deprecated `response.contentType` and `response.contentLength`
* Strengthened `IncomingContent` deprecation
* Upgraded Jetty ALPN, Tomcat
* Fixed config evaluation issues (#488)
* Disabled async incoming upgrade stream at Tomcat
* Prohibited appending unsafe headers to request headers
* Renamed `XForwardedHeadersSupport` to `XForwardedHeaderSupport` (#547)
* Added `HttpResponse.receive<T>` method to run response pipeline on raw response
* Introduced kotlin multiplatform url-parser
* Supported client form data and multipart 
* Added missing client builders for `post` and `put` methods
* Simplify client configuration API
* Fixed several compression issues
* Fixed client attributes evaluation
* Fixed CIO engine random algorithm selection
* Fixed url parsing (#511)
* Fixed ambiguity in writing client `Content-Type` and `Content-Length` headers
* Minor performance fixes
* Netty HTTP/2 fixes
* Fixed IOOBE in satic resource resolution (#493)
* Fixed JWT error handling
* Kotlin 1.2.61, kotlinx.coroutines 0.25.0

# 0.9.3
> Published 26 Jun 2018

* Improved WebSocket API
* Websocket header `Sec-WebSocket-Key` is now optional
* Fixed client cookies rendering to avoid `x-enc`
* Fixed plain text client reader (#392)
* Added EC support in CIO TLS (#394: ECDHE_RSA_AES256_SHA384, ECDHE_RSA_AES128_SHA256)
* Fix client certificate validation
* Introduced optional authentication 
* Added `ApplicationCall` as receiver for auth `validate` functions
* Introduced `call.respondBytes` (#395)
* Improved JWT support: multiple schemes, nullable issuer
* Conversion service enum type diagnostics improved (#403)
* Avoided using apos entity in HTML escaping as IE doesn't support it (#400)
* Converter support for java big numbers
* Ability to add auth methods to existing feature on the fly
* Improved auth header scheme and contents validation (#415)
* Default charset for BasicAuth is now UTF-8 (#420) 
* Added `ByteArrayContent.contentLength` (#421)
* Fixed `headersOf` case insensitive issue (#426)
* Client deserialization improved by using type token
* Ability to disable client default transformers
* Explicit `Accept` header in client request
* Turn on masking in client websockets (#423)
* Fixed inverted `PartialContent.Configuration.maxRangeCount` check (#440)
* Fixed uncaught `UnsupportedMediaTypeException` from `receiveOrNull()` (#442)
* Fix multipart boundary header parsing
* Upgraded jwks/jwt, applied RSA256 by default if unspecified (#434, #435)
* Upgrade coroutine version to 0.23.3
* Upgrade Jetty version to 9.4.11.v20180605
* Add `client-mock-engine` for testing purpose
* Use default available engine by deafult
* Upgrade kotlin to 1.2.50

Move ktor-samples to a separate repository (#340). https://github.com/ktorio/ktor-samples

# 0.9.2
> Published 23 Apr 2018

* New auth DSL, more suspendable functions (such as `verify`/`validate`)
* `RoutingResolveTrace` for introspecting routing resolution process
* HTTP client improvements and bugfixes (DSL, reconnect, redirect, cookies, websockets and more)
* CIO http client pipelining support, chunked and more
* CIO initial TLS support
* Session authentication provider
* OAuth2: introduce ability to generate and verify state field
* OAuth: fix scopes parameter to conform to RFC (#329)
* OAuth2: fix bug with double scopes encoding (#370)
* OAuth2: add ability to intercept redirect URL
* CORS: introduce `allowSameOrigin` option
* Auth: provide application call as receiver for validate functions (#375 and related)
* Test host reworked, `handleRequest` reads the body and redirects the exceptions correctly
* Servlets: fixed `inputStream` acquisition, fixed error handling
* Java 9 compatibility improved (no modules yet)
* Digest auth fixes (#380)
* Log running connectors details for better development experience (#318)
* `Last-Modified` header and related functionality to work in proper GMT time zone (#344)
* `IncomingContent` is deprecated
* `URLBuilder` fixes and improvements
* Documentation improvements
* Performance optimizations (Netty, CIO server backends)
* CIO server improved stability
* Encrypted session support (`SessionTransportTransformerEncrypt`)
* Empty (`null`) model for freemarker (#291)
* `ContentNegotiation` missing `Accept` header support (#317)

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
