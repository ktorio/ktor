# 1.4.0
> Published 18 Aug 2020

* Upgrade to kotlin 1.4.0
* Add native platform support for CIO client (#2021)
* Prevent access Tomcat servletRequest after recycling
* Fix verbose IO exception logging
* Fix client cookies remove
* Fix suspend tests for digest provider
* Add deprecation to BasicAuth feature
* Add client.get operator for features
* Add client websocket feature config
* iOS Certificate Pinning (#1750)
* Add originHost support in browser
* Fix client logging issues with POST body
* Prevent CURL multi-handle double close
* Add content-type header to default transformers
* Fix report for multiple failed engines in native
* Use window.location.origin as default host in URLBuilder
* Prevent Empty Cookie addition (#2008)
* Fix executor service termination in okhttp (#1860)
* Verify sending Content-Type and custom object body via POST (#1897)
* Fix ByteBufferPool recycle (#2016)
* Update jetty version
* Fix CIO exception logged twice
* Change exception type for long strings in readUtf8Line
* Fix uri field in digest auth header to include query params (#1992)
* Fix empty multipart post
* Move the default test server to CIO
* Fix webpack warning about ktor-client-core critical dependency
* Fix missing qop in DigestAuthProvider (Issue #1974)
* Handle " in different position cases
* Fix parsing of quoted header parameter value
* Fix saved call early completion
* Fix tests with empty json check
* Fix sending blank ContentType in Apache engine
* Parse blank content type to Any
* Fixed serialization of empty body (#1952)
* JsonFeature: Fixed header behavior and made it more flexible (#1927)
* Fix max-age header to use '=' instead f ':'. (#1769)
* Add contextual serialization support
* Introduce non-suspend api for writing
* fix memory alignment check (#1742)
* JetBrains Toolbox icon (#1805)
* Apache should use existing approach when merging headers (#1919)
* Fix conditional headers behaviour (Fix #1849).
* Change IosHttpRequestException parent to improve usability
* OAuth2: Added option to pass params in URL (#1847)
* Fix doubling host
* Enhanced handling of statusCode for AndroidEngine (#1852)
* Fix deserialization issue in client (Fix #1800).
* GitHub issue/pr links in IDEA Git log (#1806)
* Fix log channel is not closed for ByteArrayContent (#1808)
* Use comma to divide headers (Fix #1765).
* HTTP Client tracing using Stetho Android library.
* Fix static content resolution for directories inside Jar (#1777).
* Improve WebSocket routing API (Fix #1075).
* Implemented cookies encoding with their own encoding and added test cases for this
* Fix URI support (#1755)
* Fix OkHttp WebSocket close reason completion (#1363).
* Fix package directive; Add unimported cfnetwork constants
* Fix client.join to wait engine close
* Verify receive from response in exception
* Update okhttp version to 4.4.0
* Cleanup iOS proxy configuration
* Fix WebSocketTest.testMaxSize
* Fix UTF8 parsing (#1718).

# 1.3.2
> Published 12 Mar 2020

* Introduced iOS streaming response support
* Provided challenge handler in iOS client configuration
* Improved JsonFeature configuration DSL (#1472)
* Simplified server kotlinx.serialization config
* Recovered `HttpRequestBuilder.takeFrom` function (#1626)
* Allowed to configure default cookies asynchronously
* Exposed server `LocationInfo` and added location resolve functions (#1657)
* Introduced function for async writing to server response channel (#1703)
* Added an option to filter logged calls for ktor-client-logging
* Fixed iOS client timeouts
* Fixed iOS crash
* Fixed 100% CPU Apache Ktor Http Client #1018 (#1689)
* Fixed missing client response logging
* Fixed CIO server local address detection (#1663)
* Fix server request origin to provide header's host and port when available
* Fixed random missing feature errors caused by concurrency at startup (#1694)
* Fixed `Set-Cookie` header parser in iOS and JS
* Fixed client multiple redirects with relative path (#1704)
* Fixed unwrapping cancellation exceptions in client (#1482)
* Fixed missed preconfigured `OkHttpClient` in `OkHttpEngine` (#1646)
* Fixed websocket to complete closeReason on disconnection (#1275).
* Fixed websocket sending CloseReason(1009) when frame is too big
* Fixed websocket pinger logging
* Fixed maxFrameSize for chunked frames
* Fixed URL scheme parser (#1614)
* Fixed platform detection in `PlatformUtils.IS_NODE` and `PlatformUtils.IS_BROWSER` (#1675)
* Fixed `Short.highByte` 
* Fixed consumeEachBufferRange (#1693)
* Fixed httpclient gzip decoding failure (#1189)
* Fixed `InputStream` wrapper for `Input`
* Bumped versions:
    - Kotlin 1.3.70
    - kotlinx.coroutines 1.3.4
    - kotlinx.serialization 0.20.0
    - kotlinx.html 0.7.1
    - dropwizard 4.1.2
    - slf4j 1.7.30
    - mustache 0.9.6
    - pebble 3.1.2
    - webjars 0.43
    - jackson 2.10.2

# 1.3.1
> Published 5 Feb 2020

* Introduced experimental client timeout feature
* Fixed OkHttp leaking threads
* Fix decoding UTF8 lines (#1323)
* Fixed websocket close sequence (#1262, #1571, #1427)
* Reduced number of redundant exceptions logged in jetty server
* Fixed text decoder in js client under nodejs
* Fixed NSUrlSession memory leak (#1420)
* Make server feature throw `BadContentTypeFormatException` to get status 400 
* Reverted accidentally removed client `LocalFileContent`
* Removed User-Agent header in browser (to avoid CORS-related issues)
* Fixed request body with `Input`
* Improved native clients performance
* Fixed native and js client cancellation issues
* Fixed file descriptor leak in Jetty server (#1589)
* Fixed server sessions and cache related iseues (#1591)
* Upgraded JWT/JWKS versions
* Upgraded Netty version
* Fixed multiple server jwt auth providers processing (#1586)
* Fixed Auth retry logic (#1051)
* Fixed ApplicationRequest.remoteHost to not report "unknown"
* Fixed corrupted headers in CIO client and server on Android
* Improved server cancellation handling

# 1.3.0
> 14 Jan 2020

* ktor client proxy support
* Introduced `HttpStatement` and deprecated potentially dangerous resource-leaking client API
* Eliminated kotlinx.io dependency 
* Fixed server identity compression handling: keep original content length
* Fixed handling GET requests with body (#1302)
* Fixed curl request with empty body
* Added iOS url session configuration
* Fixed CIO engine no longer sends port in "Host" header (#1295)
* Add INTRINSIC value to TLS signature algorithms
* Introduced ability to send string in request body
* Improved client and server typeOf support with kotlinx.serialization
* Gradle 5.4.1+ with newer metadata (metadata 1.0)
* Improved exceptions handling in client and server on Android
* Added missing TLS parameters and relaxed TLS parsing to ignore unsupported features
* Improved session diagnostics (#1368)
* Fixed `hookRequests` in test engine (#1300)
* Deprecate java.time related API and related cleanup (for future kotlin.time support)
* Restricted CIO HTTP headers parser
* Introduced header name and value validation
* Fixed must-revalidate on the request side in ktor client (#1406)
* Fixed OkHttp client resource cleanup on close
* Added watchos/tvos native targets
* Fixed content truncation at native and JS targets
* Fixed server's `If-Range` header parsing to avoid crash at date parsing (#1377) 
* Fixed server's conditional headers processing
* Reduced required JDK version for `DefaultHeaders` server feature
* Fixed client hanging due to exception in response pipeline
* Replaced HttpClientJvmEngine to HttpClientEngineBase that is now common for all platforms (affects only custom client engines)
* Fixed hierarchy of execution and call contexts in clients that allows to properly handle request lifetime using execution context.
* Optimize JS module import time (#1464)
* Upgraded versions of Netty, Jetty and Tomcat implementations
* Added Pebble template engine (#1374)
* Introduced localPort route that is always tied to actual socket port (#1392)
* Fixed cookie expiration date parsing (#1390)
* Server authentication feature's phases are now public (#1160)
* Fixed auth header resending after redirect (#1467)
* TCP half-close made optional for CIO client engine and disabled by default.
* Apache client random timeouts fixed
* Fixed locale-dependant code (#1491)
* Fixed unclosed websocket channels if cancelled too early
* TCP half-close made optional for CIO client engine and disabled by default. (#1456)
* Improved ktor-client-mock engine to be thread safe (#1505)
* Fixed client cookies logging (#1506)
* Fixed multiple application stop events in test engine (#1498)
* Fixed CIO ActorSelectorManager to not spin due to cancelled keys (affects both CIO client and server)
* Made default auth validate functions fail to force users to implement them
* Introduced test client instance in the test server
* Fixed various server and client engines to return `null` for missing headers rather than empty list
* Introduced support for json structures in client and server (#1519)
* Introduced ktor-server-core binary compatibility tracking
* kotlinx.coroutines 1.3.3

# 1.2.6
> 25 Nov 2019

* Kotlin 1.3.60
* Restricted CIO HTTP headers parser
* Introduced header name and value validation

# 1.2.5
> 27 Sep 2019

* Fixed `ClosedSendChannel` exceptions in client and server
* Fixed Android crash on client and server exceptions
* Fixed server identity compression handling: keep original content length
* Fixed partial content without the passed range doesn't have content length
* Fixed curl request with empty body
* Fixed empty client form data (#1297)
* Fixed CIO engine no longer sends port in "Host" header (#1295)
* Fixed potential deadlock in Android engine
* Gradle Metadata 1.0 (Gradle 5.6.2)
* kotlinx.coroutines 1.3.2
* kotlinx.serialization 0.13.0
* Dropwizard 4.1.0
* jackson 2.9.9.3

# 1.2.4
> Published 2 Sep 2019

* Fixed multipart form header entity separator
* Fixed crypto in IE11 (#1283)
* Marked response transient in the client exception (#1256)
* Fixed network on main thread in okhttp engine close
* Fixed follow redirect iOS (#1000)
* Kotlin 1.3.50
* kotlinx.coroutines 1.3.0

# 1.2.3
> Published 1 Aug 2019

* JS websocket bugs fixes and improvements
* Eliminated Java9's Base64 implementation (useful for older JDK and Android)
* Fixed bug of adding unexpected trailing slash (#1201) (#1206)
* Improved apache and okhttp client engines performance
* Fixed client response body cancellation
* Added client response streaming on nodejs
* Deprecated old client `BasicAuth`
* Introduced a flag to send auth without negotiation
* Added server kotlinx.serialization initial support (`SerializationConverter`)
* Client TLS implementation fixes: cancellation and error handling.
* Added web assembly content type.
* Prohibited server double request content `call.receive`.
    * Introduce `DoubleReceive` feature that makes it work.
* Server CORS support fixed and improved
* Added initial kotlinx.serialization support for server including receiving generic collections.
* Introduced `ktor-bom` for better dependency management.
* Improved jetty server engine configuration to enable manual connectors setup. 
* Fixed client memory leak (#1223).
* Upgraded Jetty, Netty and utility libraries.
* Kotlin 1.3.41


Breaking changes/Migration steps:
* CORS doesn't allow non-simple request body content types anymore by default
  * to allow extra content types such as json, enable `allowNonSimpleContentTypes`
* At least Kotlin 1.3.41 IS REQUIRED

# 1.2.2
> Published 20 June 2019

* Upgraded to Kotlin 1.3.40.
* Netty server engine uses native transports when available (#1122).
* Upgraded to Netty 4.1.36 (#1190).
* Added JVM shutdown hooks in server engines (#1111, #1164).
* Introduced challenge builder functions in server auth providers (#366, #921, #1130, #798).
* Segmentation fault is fixed in native clients (#1135).
* Improved gracefull shutdown in ktor client engines Jetty and Apache.
* Removed kotlin-reflect from ktor jvm clients (#1162).
* Client threads daemonized (#1170).
* Relaxed client cookie value restrictions (#1069).
* Fixed empty client requests with okhttp engine (#1175).
* API cleanup, deprecations.
* kotlinx.coroutines 1.2.2, kotlinx.serialization 0.11.1.

# 1.2.1
>  Published 27 May 2019

* Fixed module function lookup (regress, #1132)
* Fixed SessionTransportTransformers application order (#1147)
* Fixed double content length header in requests on older Androids (#1060)
* Fixed receiving a byte channel crash on Android (#1140)
* Fixed websocket sockets lifecyle on Jetty engine
* Downgraded Gradle to reduce gradle metadata version

# 1.2.0
> 14 May 2019

* Introduced multiplatform websockets: jvm, js.
* Added client certificates support.
* Fixed updating session in directory storage (#963).
* Added optional contentType to formDsl (#910).
* `MockEngine` version which favors execution order and returns processed requests (#924).
* Fixed `Unit` body serialization.
* Allowed using preconfigured OkHttp client instance.
* Defined the client default user agent.
* Improved curl error diagnostics and resource management.
* Fixed LogLevel.NONE with body bytes.
* Added CIO endpoint config builder.
* Fixed status code check in client Auth feature.
* Fixed client close issue.
* Fixed: `GMTDate.toJvmDate()` uses current date instead of given date. (#986)
* Moved client auth to common.
* Use `UTF-8` as default charset in BasicAuth.
* Introduced client content encoding feature.
* Introduced client call validator feature.
* Old API deprecations and removals.
* Fixed gzip/deflate on JDK11
* Introduced JWT auth header retrieval configuration (#1048)
* `CallLogging` message format customization (#1047)
* Fixed logging error with no call-related MDC (#1033)
* Avoided using constant hash salt in `UserHashedTableAuth`
* Added LDAP auth provider proper characters escaping
* Minimized jetty core pool size and made it configurable (#1083)
* Made servlet engine use servlet config instead of context (#1063)
* Introduced accepted content types contributor in ContentNegotiation (#357)
* Introduced `ApplicationEnvironment.rootPath` with servlet engine support (context path) (#738)
* Support for `rootPath` in routing by default (#738)
* Introduce ability to serve web resources from WAR
* Added micrometer metrics (#1037)
* Added Thymeleaf templating feature (#988)
* Cookie session use `/` path by default (#1043)
* Add hot reload experimental support for JDK9+ (VM option required)
* `HttpStatusCode` equals check is amended
* Added client `AcceptCharset` header support
* `KotlinxSerializer` moved to a separate artifact
* Client engine API simplified
* Introduced client cache support
* Server authenticator config reworked
* Server digest auth updated to use UTF-8
* Added experimental android client line-wrapping logger
* Fixed webjars parameters handling and several minor fixes
* Introduced JWTVerifier configure block when using JWKProvider
* Added client json feature custom content types support 
* Fixed incorrect url encoding for some characters (#1094)
* Fixed hanging jetty server engine 
* Introduced CIO client engine request timeout config
* Added client multipart content length support
* Jetty upgraded to 9.4.15.v20190215
* okhttp client upgraded to 3.14.0
* Fixed CIO client CPU utilization issue
* Kotlin 1.3.31
* kotlinx.coroutines 1.2.1

# 1.1.5
> Published 24 Apr 2019

* Minimized jetty core pool size and make it configurable (#1083)
* Servlet engine fixed to use servlet config instead of context (#1063)

# 1.1.4
> Published 13 Apr 2019

* Upgrade to Kotlin 1.3.30
* Upgrade coroutines to 1.2.0

# 1.1.3
> Published 21 Feb 2019

* Fixed NoSuchMethodError on Android (#927)
* Fixed upload file error on JS (#956)
* Fixed several encodings issues caused corrupted jsons and receive pipeline erros (#920, #787, #866)
* Fixed curl connection errors reporting
* Updated jackson dependency (#952)

# 1.1.2
> Published 24 Jan 2019

* Introduced native curl client engine (#479)
* Added iosArm32 target (except curl) 
* Host and port route builders (#825)
* Fixed `host()` and `port()` functions to respect proxy (#834)
* Fixed classloading issue affecting hot-reload (#825)
* Fixed hanging CIO client (#800, #806)
* Added CIO client CBC support (#623, #560, #394)
* Upgraded JWKS/JWT (#856)
* Fixed server `MessageDigest` concurrent issues
* Introduced `NonceManager`, deprecated `OAuth2StateProvider`
* Prohibited setting session at server after responding the call (#864)
* Fixed loosing errors in `StatusPages` if there was already a response sent
* Introduced `application` property on `ApplicationEngine` interface
* Introduced experimental ktor server exceptions 
  * `BadRequestException`
  * `NotFoundException`
  * `MissingRequestParameterException`
  * `ParameterConversionException`
  * supported in locations out of the box (including #767)
  * experimental parameters delegation support
* Added routing tailcard prefix support (#876, #526)
* Fixed registering content converters with custom content type pattern (#872)
* Improved GSON error diagnostics (#811)
* Exclude several content types in Compression feature by default: audio, video, event-stream (#817)
* Fixed hanging handleWebSocketConversation
* Fixed cookie session max-age value to be bumped to Int.MAX_VALUE (#892)
* Fixed CIO headers parsing: allowed headers with no values
* Fixed client websocket nonce size (#861)
* Fixed client scheme parsing
* Supported client relative redirects by making #takeFrom() resolve relative urls. (#849)
* Fixed network on main thread in CIO (#764)
* Changed the default algorithm to SHA-256 from SHA-1 for signed server cookies
* Fixed conflicting `WebSockets` and `StatusPages` (#889)
* Update gradle to 4.10
* Kotlin 1.3.20, kotlinx.coroutines 1.1.1, kotlinx.serialization 0.10.0

# 1.1.1
> Published 26 Dec 2018

* Fixed broken pom files 

# 1.1.0
> Published 24 Dec 2018

* Reduced JDK7/8 dependencies (including #762)
* Discarded deprecated API
* Coroutines debug agent support 
  * see Kotlin/kotlinx.coroutines/core/kotlinx-coroutines-debug/README.md
* Fixed `IndexOutOfBounds` during main module function search
* okhttp-client: pass `contentLength` for multipart form data
* Improved auto-reloading feature in corner-cases (#783)
* Fixed HTTP server upgrade to delay socket close (#674)
* Added Mustache templating feature (#713)
* Added ability to configure `Logger` instance for `CallLogging`
* Fixed session HMAC to do a constant time comparison
* Added Node.js suport for js http client (#791)
* `SessionTransportTransformerDigest` is deprecated (#792)
  * also switched to SHA-384 by default that is not vulnerable
* Fixed jetty server thread names (#756)
* Fix conditional headers zoned dates (#476)
* `NettyChannelInitializer` made public (#286)
* Fixed slf4j dependency (#808)
* Fixed client logging freeze
* Added client `DigestAuth` feature
* Fixed apache client response reading freeze
* Fixed apache client engine errors handling
* Kotlin 1.3.11, kotlinx.coroutines 1.1.0

# 1.0.1
> Published 4 Dec 2018

* Client logging feature (`Logging` in `ktor-client-logging`)
* Client user agent feature (see `UserAgent`)
* Client top-level list serialization support (#739)
* JS client body handling fix (#746)
* Client redirect edge cases fixed
* Client close/cancel handling improved
* CIO client bugfixes
* Unicode filenames support in multipart fileupload (#757, #687, #596)
* `ContentNegotiation` and `WebSocket` server features compatibility (#745)
* Server session cookie duration is optional (#736)
* Server autreload support fixed (#736)
* `CachingOptions` resolution ambiguity fixed (#741)

# 1.0.0
> Published 19 Nov 2018

* Improved documentation
* Improved performance
* Kotlin 1.3.10
* kotlinx.coroutines 1.0.1 and structured concurrency support:
    * Coroutine scopes introduced per pipeline, call, application and web socket session
* Fixed client response cancelation via `receive<Unit>()` and `response.cancel()`
* Disabled client pipelining by default to provide safe behavior by default
* `GMTDate` improvements on js and native
* Added client response observer for response logging purpose
* Test client and mock engine improvements
* Cookies dates, domains and dupicate parameters processing fixed
* Server pipeline fixed to discard request body if not used (#609, #574)
* Websocket session lifecycle fixed during close sequence
* Several `Url` and `UrlBuilder` fixes and improvements
* Introduced `ExpectSuccess` client feature
* Fixed `StatusPages` to handle child job failures (#646)
* Compression bugfixes (including #708, #685)
* Fixed timeouts in websockets with Jetty
* Renamed `DevelopmentEngine` to `EngineMain`
* Restricted `@Location` annotation targets, allow on a typealias (#539)
* Removed default connector on port 80 (#670)
* Several JWT and OAuth bugfixes and error habdling improvements (#664, #676,  #550)
* Improved serialization client feature


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
