# Install:

```kotlin
install(ServiceDiscovery) {
    registerOnStart = true
    deregisterOnStop = true
    
    consul {
        connection {
            host = "localhost"
            port = 8500
            aclToken = "your-acl-token"
            
            tls {
                keyStorePath = "/path/to/keystore.p12"
                keyStorePassword = "password"
                keyStoreInstanceType = KeyStoreInstanceType.PKCS12
            }
        }

        registration {
            serviceName = "sample-service"
            instanceId = "sample-service-1"
            port = 8080
            tags = listOf("v1")
            metadata = mapOf("version" to "1.0.0")

            healthCheck {
                path = "/health"
                interval = "10s"
                timeout = "5s"
            }
        }

        discovery {
            queryPassingOnly = true
        }
    }
}
```

`connection { ... }` - defines how the server communicates with the Consul agent.

`registration { ... }` - defines how the server registers itself with Consul.

`discovery { ... }` - defines how the server discovers other services.

`tls { ... }` - defines TLS settings for secure communication with Consul.

`registerOnStart` - if true, the server will register itself with Consul on startup.

`deregisterOnStop` - if true, the server will deregister itself from Consul on shutdown.

# Use:
```kotlin
val registry = application.getServiceRegistry<ConsulServiceRegistry>()
val discovery = application.getDiscoveryClient<ConsulDiscoveryClient>()
```
