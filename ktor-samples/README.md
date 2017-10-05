# Running samples in IntelliJ IDEA

* Create new Java application configuration
* Set main class to
    * For Jetty: `io.ktor.jetty.JettyPackage`
    * For Netty: `io.ktor.netty.NettyPackage`
* Set classpath ("Use class path of module") to sample module
* Run
* Open http://localhost:8080 in browser

# Running samples in Maven
* Run `mvn install` 
* `cd` to the sample directory 
* Run sample
    * For Jetty: `mvn exec:java`
    * For Netty: `mvn exec:java -Pexec-netty`
* Open http://localhost:8080 in browser

After changes made in the sample you can rerun project with `mvn compile exec:java` 
