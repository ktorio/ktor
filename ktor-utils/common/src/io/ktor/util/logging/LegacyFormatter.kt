/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

internal fun legacyFormat(format: String, vararg args: Any?): () -> String {
    return {
        buildString {
            var currentArgumentIndex = 0
            var start = 0

            while (start < format.length) {
                val index = format.indexOf("{}", start)
                if (index == -1) {
                    append(format.substring(start))
                    break
                }
                if (index > 0 && format[index - 1] == '\\') {
                    // probably escaped
                    if (index == 1 || format[index - 2] != '\\') {
                        append(format.substring(start, index - 1))
                        append("{}")
                        start = index + 2
                        continue
                    }

                    // escaped escape
                    append(format.substring(start, index - 2))
                    append("\\")
                    start = index
                    // pass through
                }

                append(format.substring(start, index))
                append(args[currentArgumentIndex++].toString())
                start = index + 2
            }
        }
    }
}
