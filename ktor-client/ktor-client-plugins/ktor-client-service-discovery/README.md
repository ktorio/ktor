# Install

```kotlin
install(ServiceDiscovery) {
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

        discovery {
            queryPassingOnly = true
        }
    }
}
```

`connection { ... }` - defines how the server communicates with the Consul agent.
`discovery { ... }` - defines how the server discovers other services.
`tls { ... }` - defines TLS settings for secure communication with Consul.

# Use:

```kotlin
val client = HttpClient {
    install(ServiceDiscovery) {
        consul {
            connection {
                host = "localhost"
                port = 8500
                aclToken = "your-acl-token"
            }
        }
    }
}

val discovery = client.getDiscoveryClient<ConsulDiscoveryClient>()
val services = discovery.getInstances("sample-service")

val response = client.get("service://sample-service/api/items")
```
