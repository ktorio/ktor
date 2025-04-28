/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc.peer

import org.w3c.dom.mediacapture.MediaDevices

public typealias JsUndefined = JsAny?

public open external class DOMException {
    public var code: JsNumber
    public var message: JsString
    public var name: JsString
    public var ABORT_ERR: JsNumber
    public var DATA_CLONE_ERR: JsNumber
    public var DOMSTRING_SIZE_ERR: JsNumber
    public var HIERARCHY_REQUEST_ERR: JsNumber
    public var INDEX_SIZE_ERR: JsNumber
    public var INUSE_ATTRIBUTE_ERR: JsNumber
    public var INVALID_ACCESS_ERR: JsNumber
    public var INVALID_CHARACTER_ERR: JsNumber
    public var INVALID_MODIFICATION_ERR: JsNumber
    public var INVALID_NODE_TYPE_ERR: JsNumber
    public var INVALID_STATE_ERR: JsNumber
    public var NAMESPACE_ERR: JsNumber
    public var NETWORK_ERR: JsNumber
    public var NOT_FOUND_ERR: JsNumber
    public var NOT_SUPPORTED_ERR: JsNumber
    public var NO_DATA_ALLOWED_ERR: JsNumber
    public var NO_MODIFICATION_ALLOWED_ERR: JsNumber
    public var QUOTA_EXCEEDED_ERR: JsNumber
    public var SECURITY_ERR: JsNumber
    public var SYNTAX_ERR: JsNumber
    public var TIMEOUT_ERR: JsNumber
    public var TYPE_MISMATCH_ERR: JsNumber
    public var URL_MISMATCH_ERR: JsNumber
    public var VALIDATION_ERR: JsNumber
    public var WRONG_DOCUMENT_ERR: JsNumber
}

public external interface ReadonlyMap<K : JsAny> : JsAny {
    public fun entries(): JsAny
    public fun keys(): JsAny
    public fun values(): JsAny
    public fun forEach(
        callbackfn: (value: JsAny, key: K, map: ReadonlyMap<K>) -> Unit,
        thisArg: JsAny = definedExternally
    )

    public fun <V : JsAny> get(key: K): V?
    public fun has(key: K): JsBoolean
    public var size: JsNumber
}

public external interface Navigator {
    public val mediaDevices: MediaDevices
}

public external val navigator: Navigator
