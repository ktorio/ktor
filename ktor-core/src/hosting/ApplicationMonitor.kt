package ktor.application

/** Implement ApplicationMonitor to be notified about application creation and destruction
 */
trait ApplicationMonitor {
    fun created(application: Application)
    fun destroyed(application: Application)
}

