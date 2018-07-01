Let's encrypt automatic implementation for Ktor:

```kotlin
install(LetsEncrypt) {
    email = "youremail@host.com"
    setProduction()
    addDomainSet("yourdomain.com")
    certFolder = File("./certs")
    sslPort = 443
    sslHost = "0.0.0.0"
    keySize = 4096
}
```

You have to be serving `yourdomain.com` in ktor and be available from the Internet
(for local development you will have to set DNS to point to your public ip,
and configure your router(s) to forward redirect the port 80 to Ktor).

Internally, this feature configures the `/.well-known/acme-challenge/{token}` endpoint,
and performs an ACME/let's encrypt challenge. It generates a Key for the domain,
and gets the certificate chain from let's encrypt, storing them locally.

Then it configures Ktor to use those certificates.

It checks the certificates from time to time to renew the certificate when required.
