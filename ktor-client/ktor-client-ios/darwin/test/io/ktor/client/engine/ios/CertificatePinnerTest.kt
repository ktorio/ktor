/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.ios

import io.ktor.client.engine.ios.CertificatePinner.Companion.ecDsaSecp256r1Asn1HeaderInts
import io.ktor.client.engine.ios.CertificatePinner.Companion.ecDsaSecp384r1Asn1HeaderInts
import io.ktor.client.engine.ios.CertificatePinner.Companion.rsa1024Asn1HeaderInts
import io.ktor.client.engine.ios.CertificatePinner.Companion.rsa2048Asn1HeaderInts
import io.ktor.client.engine.ios.CertificatePinner.Companion.rsa3072Asn1HeaderInts
import io.ktor.client.engine.ios.CertificatePinner.Companion.rsa4096Asn1HeaderInts
import platform.Foundation.*
import platform.Security.*
import kotlin.test.*

class CertificatePinnerTest {

    @Test
    fun pinsNoMatches__findMatchingPins__empty() {
        // GIVEN
        val pin1 = CertificatePinner.Pin(
            pattern = "example.com", hashAlgorithm = "sha256/", hash = "AAA="
        )
        val pin2 = CertificatePinner.Pin(
            pattern = "other.co.uk", hashAlgorithm = "sha256/", hash = "BBB="
        )
        val pin3 = CertificatePinner.Pin(
            pattern = "other.co.uk", hashAlgorithm = "sha256/", hash = "CCC="
        )
        val pin4 = CertificatePinner.Pin(
            pattern = "*.other.co.uk", hashAlgorithm = "sha1/", hash = "DDD="
        )
        val pin5 = CertificatePinner.Pin(
            pattern = "**.other.co.uk", hashAlgorithm = "sha256/", hash = "EEE="
        )
        val pins = listOf(pin1, pin2, pin3, pin4, pin5)

        // WHEN
        val certificatePinner = CertificatePinner(pins.toSet(), true)
        val result = certificatePinner.findMatchingPins("no-match.com")

        // THEN
        assertEquals(emptyList(), result)
    }

    @Test
    fun pinsTwoMatchesAndWild__findMatchingPins__pins() {
        // GIVEN
        val pin1 = CertificatePinner.Pin(
            pattern = "example.com", hashAlgorithm = "sha256/", hash = "AAA="
        )
        val pin2 = CertificatePinner.Pin(
            pattern = "other.co.uk", hashAlgorithm = "sha256/", hash = "BBB="
        )
        val pin3 = CertificatePinner.Pin(
            pattern = "other.co.uk", hashAlgorithm = "sha256/", hash = "CCC="
        )
        val pin4 = CertificatePinner.Pin(
            pattern = "*.other.co.uk", hashAlgorithm = "sha1/", hash = "DDD="
        )
        val pin5 = CertificatePinner.Pin(
            pattern = "**.other.co.uk", hashAlgorithm = "sha256/", hash = "EEE="
        )
        val pins = listOf(pin1, pin2, pin3, pin4, pin5)

        // WHEN
        val certificatePinner = CertificatePinner(pins.toSet(), true)
        val result = certificatePinner.findMatchingPins("other.co.uk")

        // THEN
        assertEquals(listOf(pin2, pin3, pin5), result)
    }

    @Test
    fun pinsWildMatches__findMatchingPins__pins() {
        // GIVEN
        val pin1 = CertificatePinner.Pin(
            pattern = "example.com", hashAlgorithm = "sha256/", hash = "AAA="
        )
        val pin2 = CertificatePinner.Pin(
            pattern = "other.co.uk", hashAlgorithm = "sha256/", hash = "BBB="
        )
        val pin3 = CertificatePinner.Pin(
            pattern = "other.co.uk", hashAlgorithm = "sha256/", hash = "CCC="
        )
        val pin4 = CertificatePinner.Pin(
            pattern = "*.other.co.uk", hashAlgorithm = "sha1/", hash = "DDD="
        )
        val pin5 = CertificatePinner.Pin(
            pattern = "**.other.co.uk", hashAlgorithm = "sha256/", hash = "EEE="
        )
        val pins = listOf(pin1, pin2, pin3, pin4, pin5)

        // WHEN
        val certificatePinner = CertificatePinner(pins.toSet(), true)
        val result = certificatePinner.findMatchingPins("wild.other.co.uk")

        // THEN
        assertEquals(listOf(pin4, pin5), result)
    }

    @Test
    fun rsa1024__checkValidKeyType__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val publicKeySize = NSNumber(long = 1024L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.checkValidKeyType(publicKeyType, publicKeySize)

        // THEN
        assertTrue(result)
    }

    @Test
    fun rsa2048__checkValidKeyType__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val publicKeySize = NSNumber(long = 2048L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.checkValidKeyType(publicKeyType, publicKeySize)

        // THEN
        assertTrue(result)
    }

    @Test
    fun rsa3072__checkValidKeyType__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val publicKeySize = NSNumber(long = 3072L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.checkValidKeyType(publicKeyType, publicKeySize)

        // THEN
        assertTrue(result)
    }

    @Test
    fun rsa4096__checkValidKeyType__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val publicKeySize = NSNumber(long = 4096L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.checkValidKeyType(publicKeyType, publicKeySize)

        // THEN
        assertTrue(result)
    }

    @Test
    fun ecsecPrimeRandom256__checkValidKeyType__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeECSECPrimeRandom) as NSString
        val publicKeySize = NSNumber(long = 256L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.checkValidKeyType(publicKeyType, publicKeySize)

        // THEN
        assertTrue(result)
    }

    @Test
    fun ecsecPrimeRandom384__checkValidKeyType__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeECSECPrimeRandom) as NSString
        val publicKeySize = NSNumber(long = 384L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.checkValidKeyType(publicKeyType, publicKeySize)

        // THEN
        assertTrue(result)
    }

    @Test
    fun other__checkValidKeyType__false() {
        // GIVEN
        // https://kotlinlang.org/docs/reference/native/objc_interop
        // .html#casting-between-mapped-types
        @Suppress("CAST_NEVER_SUCCEEDS")
        val publicKeyType = "" as NSString
        val publicKeySize = NSNumber(long = 123L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.checkValidKeyType(publicKeyType, publicKeySize)

        // THEN
        assertFalse(result)
    }

    @Test
    fun rsa1024__getAsn1HeaderBytes__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val publicKeySize = NSNumber(long = 1024L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.getAsn1HeaderBytes(publicKeyType, publicKeySize)

        // THEN
        assertEquals(rsa1024Asn1HeaderInts, result)
    }

    @Test
    fun rsa2048__getAsn1HeaderBytes__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val publicKeySize = NSNumber(long = 2048L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.getAsn1HeaderBytes(publicKeyType, publicKeySize)

        // THEN
        assertEquals(rsa2048Asn1HeaderInts, result)
    }

    @Test
    fun rsa3072__getAsn1HeaderBytes__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val publicKeySize = NSNumber(long = 3072L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.getAsn1HeaderBytes(publicKeyType, publicKeySize)

        // THEN
        assertEquals(rsa3072Asn1HeaderInts, result)
    }

    @Test
    fun rsa4096__getAsn1HeaderBytes__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeRSA) as NSString
        val publicKeySize = NSNumber(long = 4096L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.getAsn1HeaderBytes(publicKeyType, publicKeySize)

        // THEN
        assertEquals(rsa4096Asn1HeaderInts, result)
    }

    @Test
    fun ecsecPrimeRandom256__getAsn1HeaderBytes__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeECSECPrimeRandom) as NSString
        val publicKeySize = NSNumber(long = 256L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.getAsn1HeaderBytes(publicKeyType, publicKeySize)

        // THEN
        assertEquals(ecDsaSecp256r1Asn1HeaderInts, result)
    }

    @Test
    fun ecsecPrimeRandom384__getAsn1HeaderBytes__true() {
        // GIVEN
        val publicKeyType = CFBridgingRelease(kSecAttrKeyTypeECSECPrimeRandom) as NSString
        val publicKeySize = NSNumber(long = 384L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.getAsn1HeaderBytes(publicKeyType, publicKeySize)

        // THEN
        assertEquals(ecDsaSecp384r1Asn1HeaderInts, result)
    }

    @Test
    fun other__getAsn1HeaderBytes__false() {
        // GIVEN
        // https://kotlinlang.org/docs/reference/native/objc_interop
        // .html#casting-between-mapped-types
        @Suppress("CAST_NEVER_SUCCEEDS")
        val publicKeyType = "" as NSString
        val publicKeySize = NSNumber(long = 123L)

        // WHEN
        val certificatePinner = CertificatePinner(emptySet(), true)
        val result = certificatePinner.getAsn1HeaderBytes(publicKeyType, publicKeySize)

        // THEN
        assertTrue(result.isEmpty())
    }

    @Test
    fun pin_hostNoMatch__matches__false() {
        // GIVEN
        val pin = CertificatePinner.Pin(
            pattern = "example.com", hashAlgorithm = "sha256/", hash = "AAA="
        )
        val host = "other.com"

        // WHEN
        val result = pin.matches(host)

        // THEN
        assertFalse(result)
    }

    @Test
    fun pin_hostMatch__matches__true() {
        // GIVEN
        val pin = CertificatePinner.Pin(
            pattern = "example.com", hashAlgorithm = "sha256/", hash = "AAA="
        )
        val host = "example.com"

        // WHEN
        val result = pin.matches(host)

        // THEN
        assertTrue(result)
    }

    @Test
    fun pinWild_hostMatchWithPrefix__matches__true() {
        // GIVEN
        val pin = CertificatePinner.Pin(
            pattern = "*.example.com", hashAlgorithm = "sha256/", hash = "AAA="
        )
        val host = "something.example.com"

        // WHEN
        val result = pin.matches(host)

        // THEN
        assertTrue(result)
    }

    @Test
    fun pinWild_hostMatchNoPrefix__matches__false() {
        // GIVEN
        val pin = CertificatePinner.Pin(
            pattern = "*.example.com", hashAlgorithm = "sha256/", hash = "AAA="
        )
        val host = "example.com"

        // WHEN
        val result = pin.matches(host)

        // THEN
        assertFalse(result)
    }

    @Test
    fun pinWildDouble_hostMatchWithPrefix__matches__true() {
        // GIVEN
        val pin = CertificatePinner.Pin(
            pattern = "**.example.com", hashAlgorithm = "sha256/", hash = "AAA="
        )
        val host = "something.example.com"

        // WHEN
        val result = pin.matches(host)

        // THEN
        assertTrue(result)
    }

    @Test
    fun pinWildDouble_hostMatchNoPrefix__matches__false() {
        // GIVEN
        val pin = CertificatePinner.Pin(
            pattern = "**.example.com", hashAlgorithm = "sha256/", hash = "AAA="
        )
        val host = "example.com"

        // WHEN
        val result = pin.matches(host)

        // THEN
        assertTrue(result)
    }
}
