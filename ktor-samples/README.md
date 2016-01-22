# Running samples in IntelliJ IDEA

* create new Java application configuration
* set main class to
 * For Jetty: org.jetbrains.ktor.jetty.JettyPackage
 * For Netty: org.jetbrains.ktor.netty.NettyPackage
* set classpath ("Use class path of module") to sample module
* Run
* Open http://localhost:8080 in browser

# Running samples in Maven
* run `mvn install` 
* cd to the sample directory 
* run sample
 * For Jetty: `mvn exec:java`
 * For Netty: `mvn exec:java -Pexec-netty`
* Open http://localhost:8080 in browser

After changes made in the sample you can rerun project with `mvn compile exec:java` 
