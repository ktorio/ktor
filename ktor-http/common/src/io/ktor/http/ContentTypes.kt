/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import io.ktor.utils.io.charsets.*

/**
 * Represents a value for a `Content-Type` header.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType)
 *
 * @property contentType represents a type part of the media type.
 * @property contentSubtype represents a subtype part of the media type.
 */
public class ContentType private constructor(
    public val contentType: String,
    public val contentSubtype: String,
    existingContent: String,
    parameters: List<HeaderValueParam> = emptyList()
) : HeaderValueWithParameters(existingContent, parameters) {

    public constructor(
        contentType: String,
        contentSubtype: String,
        parameters: List<HeaderValueParam> = emptyList()
    ) : this(
        contentType,
        contentSubtype,
        "$contentType/$contentSubtype",
        parameters
    )

    /**
     * Creates a copy of `this` type with the added parameter with the [name] and [value].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.withParameter)
     */
    public fun withParameter(name: String, value: String): ContentType {
        if (hasParameter(name, value)) return this

        return ContentType(contentType, contentSubtype, content, parameters + HeaderValueParam(name, value))
    }

    private fun hasParameter(name: String, value: String): Boolean = when (parameters.size) {
        0 -> false
        1 -> parameters[0].let { it.name.equals(name, ignoreCase = true) && it.value.equals(value, ignoreCase = true) }
        else -> parameters.any { it.name.equals(name, ignoreCase = true) && it.value.equals(value, ignoreCase = true) }
    }

    /**
     * Creates a copy of `this` type without any parameters
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.withoutParameters)
     */
    public fun withoutParameters(): ContentType = when {
        parameters.isEmpty() -> this
        else -> ContentType(contentType, contentSubtype)
    }

    /**
     * Checks if `this` type matches a [pattern] type taking into account placeholder symbols `*` and parameters.
     * The `this` type must be a more specific type than the [pattern] type. In other words:
     *
     * ```kotlin
     * ContentType("a", "b").match(ContentType("a", "b").withParameter("foo", "bar")) === false
     * ContentType("a", "b").withParameter("foo", "bar").match(ContentType("a", "b")) === true
     * ContentType("a", "*").match(ContentType("a", "b")) === false
     * ContentType("a", "b").match(ContentType("a", "*")) === true
     * ```
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.match)
     */
    public fun match(pattern: ContentType): Boolean {
        if (pattern.contentType != "*" && !pattern.contentType.equals(contentType, ignoreCase = true)) {
            return false
        }

        if (pattern.contentSubtype != "*" && !pattern.contentSubtype.equals(contentSubtype, ignoreCase = true)) {
            return false
        }

        for ((patternName, patternValue) in pattern.parameters) {
            val matches = when (patternName) {
                "*" -> {
                    when (patternValue) {
                        "*" -> true
                        else -> parameters.any { p -> p.value.equals(patternValue, ignoreCase = true) }
                    }
                }

                else -> {
                    val value = parameter(patternName)
                    when (patternValue) {
                        "*" -> value != null
                        else -> value.equals(patternValue, ignoreCase = true)
                    }
                }
            }

            if (!matches) {
                return false
            }
        }
        return true
    }

    /**
     * Checks if `this` type matches a [pattern] type taking into account placeholder symbols `*` and parameters.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.match)
     */
    public fun match(pattern: String): Boolean = match(parse(pattern))

    override fun equals(other: Any?): Boolean =
        other is ContentType &&
            contentType.equals(other.contentType, ignoreCase = true) &&
            contentSubtype.equals(other.contentSubtype, ignoreCase = true) &&
            parameters == other.parameters

    override fun hashCode(): Int {
        var result = contentType.lowercase().hashCode()
        result += 31 * result + contentSubtype.lowercase().hashCode()
        result += 31 * parameters.hashCode()
        return result
    }

    public companion object {
        /**
         * Parses a string representing a `Content-Type` header into a [ContentType] instance.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Companion.parse)
         */
        public fun parse(value: String): ContentType {
            if (value.isBlank()) return Any

            return parse(value) { parts, parameters ->
                val slash = parts.indexOf('/')

                if (slash == -1) {
                    if (parts.trim() == "*") return Any

                    throw BadContentTypeFormatException(value)
                }

                val type = parts.substring(0, slash).trim()

                if (type.isEmpty()) {
                    throw BadContentTypeFormatException(value)
                }

                val subtype = parts.substring(slash + 1).trim()

                if (type.contains(' ') || subtype.contains(' ')) {
                    throw BadContentTypeFormatException(value)
                }

                if (subtype.isEmpty() || subtype.contains('/')) {
                    throw BadContentTypeFormatException(value)
                }

                ContentType(type, subtype, parameters)
            }
        }

        /**
         * Represents a pattern `* / *` to match any content type.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Companion.Any)
         */
        public val Any: ContentType = ContentType("*", "*")
    }

    /**
     * Provides a list of standard subtypes of an `application` content type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Application)
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Application {
        public const val TYPE: String = "application"

        /**
         * Represents a pattern `application / *` to match any application content type.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Application.Any)
         */
        public val Any: ContentType = ContentType(TYPE, "*")
        public val Atom: ContentType = ContentType(TYPE, "atom+xml")
        public val Cbor: ContentType = ContentType(TYPE, "cbor")
        public val Json: ContentType = ContentType(TYPE, "json")
        public val HalJson: ContentType = ContentType(TYPE, "hal+json")
        public val JavaScript: ContentType = ContentType(TYPE, "javascript")
        public val OctetStream: ContentType = ContentType(TYPE, "octet-stream")
        public val Rss: ContentType = ContentType(TYPE, "rss+xml")
        public val Soap: ContentType = ContentType(TYPE, "soap+xml")
        public val Xml: ContentType = ContentType(TYPE, "xml")
        public val Xml_Dtd: ContentType = ContentType(TYPE, "xml-dtd")
        public val Yaml: ContentType = ContentType(TYPE, "yaml")
        public val Zip: ContentType = ContentType(TYPE, "zip")
        public val GZip: ContentType = ContentType(TYPE, "gzip")
        public val FormUrlEncoded: ContentType = ContentType(TYPE, "x-www-form-urlencoded")
        public val Pdf: ContentType = ContentType(TYPE, "pdf")
        public val Xlsx: ContentType = ContentType(TYPE, "vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        public val Docx: ContentType = ContentType(TYPE, "vnd.openxmlformats-officedocument.wordprocessingml.document")
        public val Pptx: ContentType =
            ContentType(TYPE, "vnd.openxmlformats-officedocument.presentationml.presentation")
        public val ProtoBuf: ContentType = ContentType(TYPE, "protobuf")
        public val Wasm: ContentType = ContentType(TYPE, "wasm")
        public val ProblemJson: ContentType = ContentType(TYPE, "problem+json")
        public val ProblemXml: ContentType = ContentType(TYPE, "problem+xml")

        public val Excel: ContentType = ContentType("application", "vnd.ms-excel")
        public val Word: ContentType = ContentType("application", "msword")
        public val ExcelX: ContentType = ContentType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        public val WordX: ContentType = ContentType("application", "vnd.openxmlformats-officedocument.wordprocessingml.document")
        public val ApiJson: ContentType = ContentType("application", "vnd.api+json")
        public val Sh: ContentType = ContentType("application", "x-sh")
        public val Rtf: ContentType = ContentType("application", "rtf")
        public val Kml: ContentType = ContentType("application", "vnd.google-earth.kml+xml")
        public val PowerPoint: ContentType = ContentType("application", "vnd.ms-powerpoint")
        public val Tar: ContentType = ContentType("application", "x-tar")
        public val Odt: ContentType = ContentType("application", "vnd.oasis.opendocument.text")
        public val Ods: ContentType = ContentType("application", "vnd.oasis.opendocument.spreadsheet")
        public val Odp: ContentType = ContentType("application", "vnd.oasis.opendocument.presentation")
        public val Jpeg: ContentType = ContentType("application", "jpeg")
        public val Png: ContentType = ContentType("application", "png")
        public val Svg: ContentType = ContentType("application", "svg+xml")
        public val Tiff: ContentType = ContentType("application", "tiff")
        public val Webp: ContentType = ContentType("application", "webp")
        public val H264: ContentType = ContentType("application", "x-h264")
        public val Mpeg: ContentType = ContentType("application", "mpeg")
        public val Ogg: ContentType = ContentType("application", "ogg")
        public val Wav: ContentType = ContentType("application", "wav")
        public val Mp3: ContentType = ContentType("application", "mp3")
        public val Midi: ContentType = ContentType("application", "x-midi")
        public val Flac: ContentType = ContentType("application", "x-flac")
        public val Mp4: ContentType = ContentType("application", "mp4")
        public val Mkv: ContentType = ContentType("application", "x-matroska")
        public val Avi: ContentType = ContentType("application", "x-msvideo")
        public val Iso: ContentType = ContentType("application", "x-iso9660-image")
        public val Bzip2: ContentType = ContentType("application", "x-bzip2")
        public val TarGz: ContentType = ContentType("application", "x-tar+gzip")
        public val Xlsm: ContentType = ContentType("application", "vnd.ms-excel.sheet.macroenabled.12")
        public val Mobi: ContentType = ContentType("application", "x-mobipocket-ebook")
        public val Epub: ContentType = ContentType("application", "epub+zip")
        public val JsonLd: ContentType = ContentType("application", "ld+json")
        public val Xul: ContentType = ContentType("application", "xul+xml")
        public val VndMsPowerPoint: ContentType = ContentType("application", "vnd.ms-powerpoint")
        public val VndMsWord: ContentType = ContentType("application", "msword")
        public val VndOpenXmlWord: ContentType = ContentType("application", "vnd.openxmlformats-officedocument.wordprocessingml.document")
        public val VndOpenXmlExcel: ContentType = ContentType("application", "vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        public val VndOpenXmlPowerPoint: ContentType = ContentType("application", "vnd.openxmlformats-officedocument.presentationml.presentation")
        public val VndOasisOdt: ContentType = ContentType("application", "vnd.oasis.opendocument.text")
        public val VndOasisOds: ContentType = ContentType("application", "vnd.oasis.opendocument.spreadsheet")
        public val VndOasisOdp: ContentType = ContentType("application", "vnd.oasis.opendocument.presentation")
        public val VndMsExcel: ContentType = ContentType("application", "vnd.ms-excel")
        public val VndMsAccess: ContentType = ContentType("application", "vnd.ms-access")
        public val VndOpenXmlPackage: ContentType = ContentType("application", "vnd.openxmlformats-officedocument.package")
        public val VndMsOutlook: ContentType = ContentType("application", "vnd.ms-outlook")
        public val VndJson: ContentType = ContentType("application", "vnd.json")
        public val VndFormData: ContentType = ContentType("application", "x-www-form-urlencoded")
        public val VndProtoBuf: ContentType = ContentType("application", "protobuf")
        public val VndTar: ContentType = ContentType("application", "x-tar")
        public val VndPlain: ContentType = ContentType("application", "plain")
        public val VndCsv: ContentType = ContentType("application", "csv")
        public val VndEpub: ContentType = ContentType("application", "epub+zip")
        public val VndExcelXlsm: ContentType = ContentType("application", "vnd.ms-excel.sheet.macroenabled.12")
        public val VndFlac: ContentType = ContentType("application", "x-flac")
        public val VndText: ContentType = ContentType("application", "text")
        public val VndTextPlain: ContentType = ContentType("application", "plain")
        public val VndScript: ContentType = ContentType("application", "x-script")
        public val VndTiff: ContentType = ContentType("application", "tiff")
        public val VndTextHtml: ContentType = ContentType("application", "html")
        public val VndXhtml: ContentType = ContentType("application", "xhtml+xml")
        public val VndJsonP: ContentType = ContentType("application", "jsonp")
        public val VndWav: ContentType = ContentType("application", "wav")

        /** Checks that the given [contentType] has type `application/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `application/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of an `audio` content type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Audio)
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Audio {
        public const val TYPE: String = "audio"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Aac: ContentType = ContentType(TYPE, "aac")
        public val Ac3: ContentType = ContentType(TYPE, "ac3")
        public val Amr: ContentType = ContentType(TYPE, "amr")
        public val AmrWb: ContentType = ContentType(TYPE, "amr-wb")
        public val Aptx: ContentType = ContentType(TYPE, "aptx")
        public val Atrac3: ContentType = ContentType(TYPE, "ATRAC3")
        public val AtracAdvancedLossless: ContentType = ContentType(TYPE, "ATRAC-ADVANCED-LOSSLESS")
        public val AtracX: ContentType = ContentType(TYPE, "ATRAC-X")
        public val Basic: ContentType = ContentType(TYPE, "basic")
        public val BV16: ContentType = ContentType(TYPE, "BV16")
        public val BV32: ContentType = ContentType(TYPE, "BV32")
        public val Clearmode: ContentType = ContentType(TYPE, "clearmode")
        public val CN: ContentType = ContentType(TYPE, "CN")
        public val DAT12: ContentType = ContentType(TYPE, "DAT12")
        public val Dls: ContentType = ContentType(TYPE, "dls")
        public val Dsr_es201108: ContentType = ContentType(TYPE, "dsr-es201108")
        public val Dsr_es202050: ContentType = ContentType(TYPE, "dsr-es202050")
        public val Dsr_es202211: ContentType = ContentType(TYPE, "dsr-es202211")
        public val Dsr_es202212: ContentType = ContentType(TYPE, "dsr-es202212")
        public val DV: ContentType = ContentType(TYPE, "DV")
        public val DVI4: ContentType = ContentType(TYPE, "DVI4")
        public val Eac3: ContentType = ContentType(TYPE, "eac3")
        public val Encaprtp: ContentType = ContentType(TYPE, "encaprtp")
        public val EVRC0: ContentType = ContentType(TYPE, "EVRC0")
        public val EVRC1: ContentType = ContentType(TYPE, "EVRC1")
        public val EVRC: ContentType = ContentType(TYPE, "EVRC")
        public val EVRC_QCP: ContentType = ContentType(TYPE, "EVRC-QCP")
        public val EVRCB0: ContentType = ContentType(TYPE, "EVRCB0")
        public val EVRCB1: ContentType = ContentType(TYPE, "EVRCB1")
        public val EVRCB: ContentType = ContentType(TYPE, "EVRCB")
        public val EVRCNW0: ContentType = ContentType(TYPE, "EVRCNW0")
        public val EVRCNW1: ContentType = ContentType(TYPE, "EVRCNW1")
        public val EVRCNW: ContentType = ContentType(TYPE, "EVRCNW")
        public val EVRCWB0: ContentType = ContentType(TYPE, "EVRCWB0")
        public val EVRCWB1: ContentType = ContentType(TYPE, "EVRCWB1")
        public val EVRCWB: ContentType = ContentType(TYPE, "EVRCWB")
        public val EVS: ContentType = ContentType(TYPE, "EVS")
        public val Example: ContentType = ContentType(TYPE, "example")
        public val Flac: ContentType = ContentType(TYPE, "flac")
        public val Flexfec: ContentType = ContentType(TYPE, "flexfec")
        public val Fwdred: ContentType = ContentType(TYPE, "fwdred")
        public val GSM: ContentType = ContentType(TYPE, "GSM")
        public val GSM_EFR: ContentType = ContentType(TYPE, "GSM-EFR")
        public val GSM_HR_08: ContentType = ContentType(TYPE, "GSM-HR-08")
        public val ILBC: ContentType = ContentType(TYPE, "iLBC")
        public val L16: ContentType = ContentType(TYPE, "L16")
        public val L20: ContentType = ContentType(TYPE, "L20")
        public val L24: ContentType = ContentType(TYPE, "L24")
        public val L8: ContentType = ContentType(TYPE, "L8")
        public val LPC: ContentType = ContentType(TYPE, "LPC")
        public val Matroska: ContentType = ContentType(TYPE, "matroska")
        public val MELP1200: ContentType = ContentType(TYPE, "MELP1200")
        public val MELP2400: ContentType = ContentType(TYPE, "MELP2400")
        public val MELP600: ContentType = ContentType(TYPE, "MELP600")
        public val MELP: ContentType = ContentType(TYPE, "MELP")
        public val Mhas: ContentType = ContentType(TYPE, "mhas")
        public val Midi_clip: ContentType = ContentType(TYPE, "midi-clip")
        public val Mobile_xmf: ContentType = ContentType(TYPE, "mobile-xmf")
        public val MP4: ContentType = ContentType(TYPE, "mp4")
        public val MP4A_LATM: ContentType = ContentType(TYPE, "MP4A-LATM")
        public val MPA: ContentType = ContentType(TYPE, "MPA")
        public val Mpa_robust: ContentType = ContentType(TYPE, "mpa-robust")
        public val Mpeg4_generic: ContentType = ContentType(TYPE, "mpeg4-generic")
        public val MPEG: ContentType = ContentType(TYPE, "mpeg")
        public val OGG: ContentType = ContentType(TYPE, "ogg")
        public val Opus: ContentType = ContentType(TYPE, "opus")
        public val Parityfec: ContentType = ContentType(TYPE, "parityfec")
        public val PCMA: ContentType = ContentType(TYPE, "PCMA")
        public val PCMA_WB: ContentType = ContentType(TYPE, "PCMA-WB")
        public val PCMU: ContentType = ContentType(TYPE, "PCMU")
        public val PCMU_WB: ContentType = ContentType(TYPE, "PCMU-WB")
        public val Prs_sid: ContentType = ContentType(TYPE, "prs.sid")
        public val QCELP: ContentType = ContentType(TYPE, "QCELP")
        public val Raptorfec: ContentType = ContentType(TYPE, "raptorfec")
        public val RED: ContentType = ContentType(TYPE, "RED")
        public val Rtp_enc_aescm128: ContentType = ContentType(TYPE, "rtp-enc-aescm128")
        public val Rtp_midi: ContentType = ContentType(TYPE, "rtp-midi")
        public val Rtploopback: ContentType = ContentType(TYPE, "rtploopback")
        public val Rtx: ContentType = ContentType(TYPE, "rtx")
        public val Scip: ContentType = ContentType(TYPE, "scip")
        public val SMV0: ContentType = ContentType(TYPE, "SMV0")
        public val SMV: ContentType = ContentType(TYPE, "SMV")
        public val SMV_QCP: ContentType = ContentType(TYPE, "SMV-QCP")
        public val Sofa: ContentType = ContentType(TYPE, "sofa")
        public val Sp_midi: ContentType = ContentType(TYPE, "sp-midi")
        public val Speex: ContentType = ContentType(TYPE, "speex")
        public val T140c: ContentType = ContentType(TYPE, "t140c")
        public val T38: ContentType = ContentType(TYPE, "t38")
        public val Telephone_event: ContentType = ContentType(TYPE, "telephone-event")

        /** Checks that the given [contentType] has type `audio/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `audio/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of an `image` content type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Image)
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Image {
        public const val TYPE: String = "image"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Aces: ContentType = ContentType(TYPE, "aces")
        public val Apng: ContentType = ContentType(TYPE, "apng")
        public val Avci: ContentType = ContentType(TYPE, "avci")
        public val Avcs: ContentType = ContentType(TYPE, "avcs")
        public val Avif: ContentType = ContentType(TYPE, "avif")
        public val Bmp: ContentType = ContentType(TYPE, "bmp")
        public val Cgm: ContentType = ContentType(TYPE, "cgm")
        public val DicomRle: ContentType = ContentType(TYPE, "dicom-rle")
        public val Dpx: ContentType = ContentType(TYPE, "dpx")
        public val Emf: ContentType = ContentType(TYPE, "emf")
        public val Fits: ContentType = ContentType(TYPE, "fits")
        public val G3fax: ContentType = ContentType(TYPE, "g3fax")
        public val GIF: ContentType = ContentType(TYPE, "gif")
        public val Heic: ContentType = ContentType(TYPE, "heic")
        public val HeicSequence: ContentType = ContentType(TYPE, "heic-sequence")
        public val Heif: ContentType = ContentType(TYPE, "heif")
        public val HeifSequence: ContentType = ContentType(TYPE, "heif-sequence")
        public val J2C: ContentType = ContentType(TYPE, "j2c")
        public val Jaii: ContentType = ContentType(TYPE, "jaii")
        public val Jais: ContentType = ContentType(TYPE, "jais")
        public val Jls: ContentType = ContentType(TYPE, "jls")
        public val Jp2: ContentType = ContentType(TYPE, "jp2")
        public val JPEG: ContentType = ContentType(TYPE, "jpeg")
        public val Jph: ContentType = ContentType(TYPE, "jph")
        public val Jphc: ContentType = ContentType(TYPE, "jphc")
        public val Jpm: ContentType = ContentType(TYPE, "jpm")
        public val Jpx: ContentType = ContentType(TYPE, "jpx")
        public val Jxl: ContentType = ContentType(TYPE, "jxl")
        public val Jxr: ContentType = ContentType(TYPE, "jxr")
        public val JxrA: ContentType = ContentType(TYPE, "jxrA")
        public val JxrS: ContentType = ContentType(TYPE, "jxrS")
        public val Jxs: ContentType = ContentType(TYPE, "jxs")
        public val Jxsc: ContentType = ContentType(TYPE, "jxsc")
        public val Jxsi: ContentType = ContentType(TYPE, "jxsi")
        public val Jxss: ContentType = ContentType(TYPE, "jxss")
        public val Ktx2: ContentType = ContentType(TYPE, "ktx2")
        public val Ktx: ContentType = ContentType(TYPE, "ktx")
        public val Naplps: ContentType = ContentType(TYPE, "naplps")
        public val PNG: ContentType = ContentType(TYPE, "png")
        public val PrsBtif: ContentType = ContentType(TYPE, "prs.btif")
        public val PrsPti: ContentType = ContentType(TYPE, "prs.pti")
        public val PwgRaster: ContentType = ContentType(TYPE, "pwg-raster")
        public val SVG: ContentType = ContentType(TYPE, "svg+xml")
        public val T38: ContentType = ContentType(TYPE, "t38")
        public val Tiff: ContentType = ContentType(TYPE, "tiff")
        public val TiffFx: ContentType = ContentType(TYPE, "tiff-fx")
        public val VndAdobePhotoshop: ContentType = ContentType(TYPE, "vnd.adobe.photoshop")
        public val VndAirzipAcceleratorAzv: ContentType = ContentType(TYPE, "vnd.airzip.accelerator.azv")
        public val VndCnsInf2: ContentType = ContentType(TYPE, "vnd.cns.inf2")
        public val VndDeceGraphic: ContentType = ContentType(TYPE, "vnd.dece.graphic")
        public val VndDjvu: ContentType = ContentType(TYPE, "vnd.djvu")
        public val VndDvbSubtitle: ContentType = ContentType(TYPE, "vnd.dvb.subtitle")
        public val VndDwg: ContentType = ContentType(TYPE, "vnd.dwg")
        public val VndDxf: ContentType = ContentType(TYPE, "vnd.dxf")
        public val VndFastbidsheet: ContentType = ContentType(TYPE, "vnd.fastbidsheet")
        public val VndFpx: ContentType = ContentType(TYPE, "vnd.fpx")
        public val VndFst: ContentType = ContentType(TYPE, "vnd.fst")
        public val VndFujixeroxEdmicsMmr: ContentType = ContentType(TYPE, "vnd.fujixerox.edmics-mmr")
        public val VndFujixeroxEdmicsRlc: ContentType = ContentType(TYPE, "vnd.fujixerox.edmics-rlc")
        public val VndGlobalgraphicsPgb: ContentType = ContentType(TYPE, "vnd.globalgraphics.pgb")
        public val VndMicrosoftIcon: ContentType = ContentType(TYPE, "vnd.microsoft.icon")
        public val VndMix: ContentType = ContentType(TYPE, "vnd.mix")
        public val VndMozillaApng: ContentType = ContentType(TYPE, "vnd.mozilla.apng")
        public val VndMsModi: ContentType = ContentType(TYPE, "vnd.ms-modi")
        public val VndNetFpx: ContentType = ContentType(TYPE, "vnd.net-fpx")
        public val VndPcoB16: ContentType = ContentType(TYPE, "vnd.pco.b16")
        public val VndRadiance: ContentType = ContentType(TYPE, "vnd.radiance")
        public val VndSealedmediaSoftsealGif: ContentType = ContentType(TYPE, "vnd.sealedmedia.softseal.gif")
        public val VndSealedmediaSoftsealJpg: ContentType = ContentType(TYPE, "vnd.sealedmedia.softseal.jpg")
        public val VndSealedPng: ContentType = ContentType(TYPE, "vnd.sealed.png")
        public val VndSwf: ContentType = ContentType(TYPE, "vnd.svf")
        public val VndTencentTap: ContentType = ContentType(TYPE, "vnd.tencent.tap")
        public val VndValveSourceTexture: ContentType = ContentType(TYPE, "vnd.valve.source.texture")
        public val VndWapWbmp: ContentType = ContentType(TYPE, "vnd.wap.wbmp")
        public val VndXiff: ContentType = ContentType(TYPE, "vnd.xiff")
        public val VndZbrushPcx: ContentType = ContentType(TYPE, "vnd.zbrush.pcx")
        public val Webp: ContentType = ContentType(TYPE, "webp")
        public val Wmf: ContentType = ContentType(TYPE, "wmf")
        public val XIcon: ContentType = ContentType(TYPE, "x-icon")

        /** Checks that the given [contentType] has type `image/`. */
        public operator fun contains(contentSubtype: String): Boolean =
            contentSubtype.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `image/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `message` content type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Message)
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Message {
        public const val TYPE: String = "message"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Http: ContentType = ContentType(TYPE, "http")

        /** Checks that the given [contentType] has type `message/`. */
        public operator fun contains(contentSubtype: String): Boolean =
            contentSubtype.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `message/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `multipart` content type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.MultiPart)
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object MultiPart {
        public const val TYPE: String = "multipart"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Alternative: ContentType = ContentType(TYPE, "alternative")
        public val ByteRanges: ContentType = ContentType(TYPE, "byteranges")
        public val Digest: ContentType = ContentType(TYPE, "digest")
        public val Encrypted: ContentType = ContentType(TYPE, "encrypted")
        public val Example: ContentType = ContentType(TYPE, "example")
        public val FormData: ContentType = ContentType(TYPE, "form-data")
        public val HeaderSet: ContentType = ContentType(TYPE, "header-set")
        public val Mixed: ContentType = ContentType(TYPE, "mixed")
        public val Multilingual: ContentType = ContentType(TYPE, "multilingual")
        public val Parallel: ContentType = ContentType(TYPE, "parallel")
        public val Related: ContentType = ContentType(TYPE, "related")
        public val Report: ContentType = ContentType(TYPE, "report")
        public val Signed: ContentType = ContentType(TYPE, "signed")
        public val VndBintMedPlus: ContentType = ContentType(TYPE, "vnd.bint.med-plus")
        public val VoiceMessage: ContentType = ContentType(TYPE, "voice-message")
        public val XMixedReplace: ContentType = ContentType(TYPE, "x-mixed-replace")

        /** Checks that the given [contentType] has type `multipart/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `multipart/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `text` content type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Text)
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Text {
        public const val TYPE: String = "text"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val CacheManifest: ContentType = ContentType(TYPE, "cache-manifest")
        public val Calendar: ContentType = ContentType(TYPE, "calendar")
        public val CSS: ContentType = ContentType(TYPE, "css")
        public val CSV: ContentType = ContentType(TYPE, "csv")
        public val EventStream: ContentType = ContentType(TYPE, "event-stream")
        public val Graphviz: ContentType = ContentType(TYPE, "vnd.graphviz")
        public val Html: ContentType = ContentType(TYPE, "html")
        public val JavaScript: ContentType = ContentType(TYPE, "javascript")
        public val Markdown: ContentType = ContentType(TYPE, "markdown")
        public val Plain: ContentType = ContentType(TYPE, "plain")
        public val Rtf: ContentType = ContentType(TYPE, "rtf")
        public val TabSeparatedValues: ContentType = ContentType(TYPE, "tab-separated-values")
        public val Troff: ContentType = ContentType(TYPE, "troff")
        public val TrolltechLinguist: ContentType = ContentType(TYPE, "vnd.trolltech.linguist")
        public val Turtle: ContentType = ContentType(TYPE, "turtle")
        public val VCard: ContentType = ContentType(TYPE, "vcard")
        public val Vtt: ContentType = ContentType(TYPE, "vtt")
        public val Xml: ContentType = ContentType(TYPE, "xml")

        /** Checks that the given [contentType] has type `text/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `text/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `video` content type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Video)
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Video {
        public const val TYPE: String = "video"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Bink: ContentType = ContentType(TYPE, "vnd.radgamettools.bink")
        public val DeceMP4: ContentType = ContentType(TYPE, "vnd.dece.mp4")
        public val DVBFile: ContentType = ContentType(TYPE, "vnd.dvb.file")
        public val GPP2: ContentType = ContentType(TYPE, "3gpp2")
        public val GPP: ContentType = ContentType(TYPE, "3gpp")
        public val Matroska3D: ContentType = ContentType(TYPE, "matroska-3d")
        public val Matroska: ContentType = ContentType(TYPE, "matroska")
        public val MJ2: ContentType = ContentType(TYPE, "mj2")
        public val MP4: ContentType = ContentType(TYPE, "mp4")
        public val MPEG: ContentType = ContentType(TYPE, "mpeg")
        public val MPEGURL: ContentType = ContentType(TYPE, "vnd.mpegurl")
        public val MSPlayReadyPYV: ContentType = ContentType(TYPE, "vnd.ms-playready.media.pyv")
        public val NokiaMP4VR: ContentType = ContentType(TYPE, "vnd.nokia.mp4vr")
        public val OGG: ContentType = ContentType(TYPE, "ogg")
        public val QuickTime: ContentType = ContentType(TYPE, "quicktime")
        public val SealedMOV: ContentType = ContentType(TYPE, "vnd.sealedmedia.softseal.mov")
        public val SealedMPEG1: ContentType = ContentType(TYPE, "vnd.sealed.mpeg1")
        public val SealedMPEG4: ContentType = ContentType(TYPE, "vnd.sealed.mpeg4")
        public val SealedSWF: ContentType = ContentType(TYPE, "vnd.sealed.swf")
        public val Smacker: ContentType = ContentType(TYPE, "vnd.radgamettools.smacker")
        public val UVVUMP4: ContentType = ContentType(TYPE, "vnd.uvvu.mp4")

        /** Checks that the given [contentType] has type `video/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `video/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }

    /**
     * Provides a list of standard subtypes of a `font` content type.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.ContentType.Font)
     */
    @Suppress("KDocMissingDocumentation", "unused")
    public object Font {
        public const val TYPE: String = "font"

        public val Any: ContentType = ContentType(TYPE, "*")
        public val Collection: ContentType = ContentType(TYPE, "collection")
        public val Otf: ContentType = ContentType(TYPE, "otf")
        public val Sfnt: ContentType = ContentType(TYPE, "sfnt")
        public val Ttf: ContentType = ContentType(TYPE, "ttf")
        public val Woff: ContentType = ContentType(TYPE, "woff")
        public val Woff2: ContentType = ContentType(TYPE, "woff2")

        /** Checks that the given [contentType] has type `font/`. */
        public operator fun contains(contentType: CharSequence): Boolean =
            contentType.startsWith("$TYPE/", ignoreCase = true)

        /** Checks that the given [contentType] has type `font/`. */
        public operator fun contains(contentType: ContentType): Boolean = contentType.match(Any)
    }
}

/**
 * Exception thrown when a content type string is malformed.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.BadContentTypeFormatException)
 */
public class BadContentTypeFormatException(value: String) : Exception("Bad Content-Type format: $value")

/**
 * Creates a copy of `this` type with the added charset parameter with [charset] value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.withCharset)
 */
public fun ContentType.withCharset(charset: Charset): ContentType =
    withParameter("charset", charset.name)

/**
 * Creates a copy of `this` type with the added charset parameter with [charset] value
 * if [ContentType] is not ignored
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.withCharsetIfNeeded)
 */
public fun ContentType.withCharsetIfNeeded(charset: Charset): ContentType =
    if (contentType.lowercase() != "text") {
        this
    } else {
        withParameter("charset", charset.name)
    }

/**
 * Extracts a [Charset] value from the given `Content-Type`, `Content-Disposition` or similar header value.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.charset)
 */
public fun HeaderValueWithParameters.charset(): Charset? = parameter("charset")?.let {
    try {
        Charsets.forName(it)
    } catch (exception: IllegalArgumentException) {
        null
    }
}
