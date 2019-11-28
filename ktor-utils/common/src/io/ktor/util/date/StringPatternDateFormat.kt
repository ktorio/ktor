/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.date

import io.ktor.util.*

/**
 * Simplified date format, similar to [GMTDateParser] support the same components except for week day and * pattern.
 * Number of pattern character repetitions also makes difference. All other characters than component patterns are
 * treated as static text and copied unchanged except for star character '*' that leads to an exception to avoid
 * accidental pattern usage from [GMTDateParser].
 *
 * | Unit     | Pattern char | Description                                          |
 * | WeekDay  | E, EE, EEE   | Day of week: 3 characters short name (eg Mon)        |
 * | WeekDay  | EEEE         | Day of week: full day name                           |
 * | Any char | *            | Unsupported                                          |
 *
 * @property pattern to be used to convert dates to text
 */
internal data class StringPatternDateFormat(val pattern: String) {
    /**
     * Date format pattern components
     */
    val components: List<Component>

    /**
     * Formatted date string estimated length
     */
    internal val estimate: Int

    init {
        val componentsList = ArrayList<Component>()

        var lastCharacter = ' '
        var count = 0
        val constantPart = StringBuilder()

        for (character in pattern) {
            if (character in "smhHdMyYzE*") {
                if (constantPart.isNotEmpty()) {
                    componentsList.add(Component.Constant(constantPart.toString()))
                    constantPart.clear()
                }

                if (character == lastCharacter) {
                    count++
                } else {
                    if (count > 0) {
                        componentsList.add(Component.lookup(lastCharacter, count))
                    }
                    lastCharacter = character
                    count = 1
                }
            } else {
                if (count > 0) {
                    componentsList.add(Component.lookup(lastCharacter, count))
                    count = 0
                }
                constantPart.append(character)
            }
        }

        if (count > 0) {
            componentsList.add(Component.lookup(lastCharacter, count))
        }

        if (constantPart.isNotEmpty()) {
            componentsList.add(Component.Constant(constantPart.toString()))
        }

        components = componentsList
        estimate = componentsList.sumBy { component ->
            when (component) {
                is Component.DayOfWeek -> when {
                    component.length < 4 -> 3
                    else -> WeekDay.values().maxBy { it.name.length }!!.name.length
                }
                is Component.Month -> when {
                    component.length < 4 -> component.length
                    else -> Month.values().maxBy { it.name.length }!!.name.length
                }
                is Component.Zone -> 3
                else -> component.length
            }
        }
    }

    /**
     * Produces the corresponding regular expression to match date.
     */
    fun toRegex(): Regex {
        val pairs = components.map { component ->
            when (component) {
                is Component.DayOfWeek -> when {
                    component.length < 4 -> "([a-z]{3})"
                    else -> "([a-z]{${WeekDay.MinNameLength},${WeekDay.MaxNameLength}})"
                }
                is Component.Month -> when {
                    component.length < 3 -> "([0-9]{1,2})"
                    component.length == 3 -> "([a-z]{3})"
                    else -> "([a-z]{${Month.MinNameLength},${Month.MaxNameLength}})"
                }
                is Component.Zone -> "(GMT)"
                is Component.Constant -> "(" + Regex.escape(component.text) + ")"
                is Component.Year -> when (component.length) {
                    2 -> "([0-9][0-9])"
                    else -> "([0-9]{4})"
                }
                else -> when (component.length) {
                    1 -> "([0-9]{1,4})"
                    else -> "([0-9]{${component.length}})"
                }
            }
        }

        return pairs.joinToString("").toRegex(RegexOption.IGNORE_CASE)
    }

    /**
     * Parsed pattern components
     *
     * @property character corresponding pattern character
     * @property length number of pattern character repetitions or constant length
     */
    @Suppress("KDocMissingDocumentation")
    sealed class Component(val character: Char, val length: Int) {
        class Seconds(length: Int) : Component('s', length)
        class Minutes(length: Int) : Component('m', length)
        class Hours(length: Int) : Component('h', length)

        class DayOfMonth(length: Int) : Component('d', length)
        class DayOfWeek(length: Int) : Component('E', length)
        class Month(length: Int) : Component('M', length)
        class Year(length: Int) : Component('Y', length)

        class Zone(length: Int) : Component('z', length)

        class Constant(val text: String) : Component('?', text.length)

        companion object {
            internal fun lookup(character: Char, length: Int): Component {
                return when (character) {
                    's' -> Seconds(length)
                    'm' -> Minutes(length)
                    'h', 'H' -> Hours(length)
                    'd' -> DayOfMonth(length)
                    'M' -> Month(length)
                    'Y' -> Year(length)
                    'y' -> Year(length)
                    'z' -> Zone(length)
                    'E' -> DayOfWeek(length)
                    else -> error("Unsupported date format pattern character $character")
                }
            }
        }
    }
}

/**
 * Format an instance of [GMTDate] to a text in the specified [format].
 */
internal fun GMTDate.format(format: StringPatternDateFormat): String = buildString(format.estimate) {
    format.components.forEach { component ->
        when (component) {
            is StringPatternDateFormat.Component.Zone -> append("GMT")
            is StringPatternDateFormat.Component.Seconds -> append(seconds.toString().padStart(component.length, '0'))
            is StringPatternDateFormat.Component.Minutes -> append(minutes.toString().padStart(component.length, '0'))
            is StringPatternDateFormat.Component.Hours -> append(hours.toString().padStart(component.length, '0'))
            is StringPatternDateFormat.Component.DayOfMonth -> append(dayOfMonth.toString().padStart(component.length, '0'))
            is StringPatternDateFormat.Component.DayOfWeek -> append(
                when {
                    component.length < 4 -> dayOfWeek.shortName
                    else -> dayOfWeek.fullName
                }
            )
            is StringPatternDateFormat.Component.Month -> append(
                when (component.length) {
                    1, 2 -> (month.ordinal + 1).toString().padStart(component.length, '0')
                    3 -> month.shortName
                    4 -> month.fullName
                    else -> error("Unsupported month length ${component.length}")
                }
            )
            is StringPatternDateFormat.Component.Year -> when (component.length) {
                2 -> append((year % 100).toString().padStart(2, '0'))
                else -> append(year.toString().padStart(component.length, '0'))
            }
            is StringPatternDateFormat.Component.Constant -> append(component.text)
            else -> error("Unsupported component $component")
        }
    }
}
