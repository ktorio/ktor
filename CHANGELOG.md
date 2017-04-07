# 0.3.2
> Not published

* Fix bug in byte array response that missed Content-Length header
* Fix default encoding in FreeMarker to be UTF-8

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