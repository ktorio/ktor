# 0.3.3
> Pending

* Execution model slightly changed to avoid global executors. ApplicationEnvironment doesn't provide `executor` anymore
* Websockets refactored with channels instead of callback functions
* Fixed bug with compression not preserving status code (thanks Diego Rocha)
* Fixed performance issues with Netty host under heavy load and keep-alive connections
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