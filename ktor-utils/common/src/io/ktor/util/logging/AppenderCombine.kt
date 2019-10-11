/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.logging

internal fun List<Appender>.combine(): Appender? {
    return when (size) {
        0 -> null
        1 -> single()
        else -> reduce { combined, next ->
            when {
                combined is MultiAppender && next is MultiAppender -> {
                    MultiAppender(combined.appenders + next.appenders)
                }
                combined is MultiAppender && next is TeeAppender -> {
                    MultiAppender(combined.appenders + next.first + next.second)
                }
                combined is MultiAppender -> {
                    MultiAppender(combined.appenders + next)
                }
                next is MultiAppender && combined is TeeAppender -> {
                    MultiAppender(next.appenders + combined.first + combined.second)
                }
                next is MultiAppender -> {
                    MultiAppender(next.appenders + combined)
                }
                combined is TeeAppender && next is TeeAppender -> {
                    MultiAppender(
                        arrayOf(
                            combined.first,
                            combined.second,
                            next.first,
                            next.second
                        )
                    )
                }
                combined is TeeAppender -> {
                    MultiAppender(arrayOf(combined.first, combined.second, next))
                }
                next is TeeAppender -> {
                    MultiAppender(arrayOf(combined, next.first, next.second))
                }
                else -> TeeAppender(combined, next)
            }
        }
    }
}
