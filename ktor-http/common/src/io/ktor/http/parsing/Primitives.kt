/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.parsing

internal val lowAlpha get() = 'a' to 'z'
internal val alpha get() = ('a' to 'z') or ('A' to 'Z')
internal val digit get() = RawGrammar("\\d")
internal val hex get() = digit or ('A' to 'F') or ('a' to 'f')

internal val alphaDigit get() = alpha or digit
internal val alphas get() = atLeastOne(alpha)
internal val digits get() = atLeastOne(digit)
