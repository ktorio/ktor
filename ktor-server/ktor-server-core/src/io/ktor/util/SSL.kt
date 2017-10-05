package io.ktor.util

import sun.security.x509.*
import java.io.*
import java.math.*
import java.security.*
import java.time.*
import java.util.*


/**
 * Generates simple self-signed certificate with [keyAlias] name, private key is encrypted with [keyPassword],
 * and a JKS keystore to hold it in [file] with [jksPassword].
 *
 * Only for testing purposes: NEVER use it for production!
 *
 * A generated certificate will have 3 days validity period and 1024-bits key strength.
 * Only localhost and 127.0.0.1 domains are valid with the certificate.
 */
fun generateCertificate(file: File, algorithm: String = "SHA1withRSA", keyAlias: String = "mykey", keyPassword: String = "changeit", jksPassword: String = keyPassword): KeyStore {
    val daysValid: Long = 3
    val jks = KeyStore.getInstance("JKS")!!
    jks.load(null, null)

    val keyPairGenerator = KeyPairGenerator.getInstance("RSA")!!
    keyPairGenerator.initialize(1024)
    val keyPair = keyPairGenerator.genKeyPair()!!

    val certInfo = X509CertInfo()
    val from = Date()
    val to = LocalDateTime.now().plusDays(daysValid).atZone(ZoneId.systemDefault())
    val certValidity = CertificateValidity(from, Date.from(to.toInstant()))

    val sn = BigInteger(64, SecureRandom())

    val owner = X500Name("cn=localhost, ou=Kotlin, o=JetBrains, c=RU")

    certInfo.set(X509CertInfo.VALIDITY, certValidity)
    certInfo.set(X509CertInfo.SERIAL_NUMBER, CertificateSerialNumber(sn))
    certInfo.set(X509CertInfo.SUBJECT, owner)
    certInfo.set(X509CertInfo.ISSUER, owner)
    certInfo.set(X509CertInfo.KEY, CertificateX509Key(keyPair.public))
    certInfo.set(X509CertInfo.VERSION, CertificateVersion(CertificateVersion.V3))
    certInfo.set(X509CertInfo.EXTENSIONS, CertificateExtensions().apply {
        set(SubjectAlternativeNameExtension.NAME, SubjectAlternativeNameExtension(GeneralNames().apply {
            add(GeneralName(DNSName("localhost")))
            add(GeneralName(IPAddressName("127.0.0.1")))
        }))
    })

    var algo = AlgorithmId(AlgorithmId.sha1WithRSAEncryption_oid)
    certInfo.set(X509CertInfo.ALGORITHM_ID, CertificateAlgorithmId(algo))

    var cert = X509CertImpl(certInfo)
    cert.sign(keyPair.private, algorithm)

    algo = cert.get(X509CertImpl.SIG_ALG) as AlgorithmId
    certInfo.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo)
    certInfo.set("version", CertificateVersion(2))

    cert = X509CertImpl(certInfo)
    cert.sign(keyPair.private, algorithm)

    jks.setCertificateEntry(keyAlias, cert)
    jks.setKeyEntry(keyAlias, keyPair.private, keyPassword.toCharArray(), arrayOf(cert))

    file.parentFile.mkdirs()
    file.outputStream().use {
        jks.store(it, jksPassword.toCharArray())
    }

    return jks
}
