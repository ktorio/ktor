package ktor.application

/** Implement ApplicationMonitor to be notified about application creation and destruction
 */
interface ApplicationMonitor {
    fun created(application: Application)
    fun destroyed(application: Application)
}

