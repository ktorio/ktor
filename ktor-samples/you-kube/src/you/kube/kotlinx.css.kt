package kotlinx.css.port

import java.text.*
import java.util.*
import kotlin.reflect.*

// ported https://github.com/kotlinx/kotlinx.css

fun renderCSS(body: StyleContainer.() -> Unit): String {
    val style = StyleContainer()
    style.body()
    return style.toString().replace("  a: b;\n", "")
}

open class CssSubProperty() {
    operator fun getValue(parent : CssProperty, property: KProperty<*>) : CssProperty = CssProperty(property.name.replace("_","-"), parent)
}

open class CssProperty(val propertyName: String, val propertyParent: CssProperty? = null) {
    fun fullName() : String = if (propertyParent != null) "${propertyParent.fullName()}-$propertyName" else propertyName
}

data class StyleProperty(val name: String, val value: String) {
}

class StyleContainer {
    var combinator: String = ""

    val styles: MutableList<Style> = ArrayList()
    fun render(builder: StringBuilder, outerSelector: String) {
        for (style in styles) {
            if (combinator.startsWith("@media")) {
                builder.append("$combinator {\n")
                style.render(builder, "$outerSelector")
                builder.append("}\n\n")
            } else if (combinator == "!") {
                style.render(builder, "$outerSelector:not(", ")")
            } else {
                style.render(builder, "$outerSelector$combinator")
            }
        }
    }
    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }

    fun invoke(body: StyleContainer.() -> Unit): StyleContainer {
        body()
        return this
    }


}
class Style {
    var selector: String = "*"

    val properties: MutableList<StyleProperty> = ArrayList()
    val containers: MutableList<StyleContainer> = ArrayList()
    operator fun invoke(body: Style.() -> Unit): Style {
        body()
        return this
    }
    operator fun CssProperty.invoke(value: String) {
        property(fullName(), value)
    }

    override fun toString(): String {
        val builder = StringBuilder()
        render(builder, "")
        return builder.toString()
    }

    fun properties(): String {
        val builder = StringBuilder()
        for ((name, value) in properties) {
            builder.append("  $name: $value;\n")
        }
        return builder.toString()
    }

    fun render(builder: StringBuilder, outerSelector: String, tail : String = "") {
        if (properties.size > 0) {
            builder.append("$outerSelector$selector$tail {\n")
            for ((name, value) in properties) {
                builder.append("  $name: $value;\n")
            }
            builder.append("}\n\n")
        }
        for (container in containers) {
            container.render(builder, "$outerSelector$selector")
        }
    }
}

fun Style.property(name: String, value: String) {
    properties.add(StyleProperty(name, value))
}

val StyleContainer.any: Style get() = tag("*")

fun StyleContainer.tag(name: String, body: Style.() -> Unit): Style = tag(name).invoke(body)
fun StyleContainer.tag(name: String): Style {
    val style = Style()
    styles.add(style)
    style.selector = name
    return style
}
fun StyleContainer.select(state: String): Style {
    val style = Style()
    style.selector = ":" + state
    styles.add(style)
    return style
}

fun StyleContainer.select(state: String, body: Style.() -> Unit): Style = select(state).invoke(body)
fun Style.select(state: String, body: Style.() -> Unit): Style = select(state).invoke(body)
fun Style.select(state: String): Style {
    val container = StyleContainer()
    container.combinator = ""
    containers.add(container)
    val style = Style()
    style.selector = ":" + state
    container.styles.add(style)
    return style
}

enum class AttributeOperation {
    equals,
    contains,
    startsWith,
    endsWith
}

fun Style.attribute(name: String, value: String = "", operation: AttributeOperation = AttributeOperation.equals, body: Style.() -> Unit): Style = attribute(name, value, operation).invoke(body)
fun Style.attribute(name: String, value: String = "", operation: AttributeOperation = AttributeOperation.equals): Style {
    val container = StyleContainer()
    container.combinator = ""
    containers.add(container)
    val style = Style()
    style.selector = if (value == "") {
        "[$name]"
    } else when(operation) {
        AttributeOperation.equals -> {
            "[$name=$value]"
        }
        AttributeOperation.contains -> {
            "[$name*=$value]"
        }
        AttributeOperation.startsWith -> {
            "[$name^=$value]"
        }
        AttributeOperation.endsWith -> {
            "[$name$=$value]"
        }
    }
    container.styles.add(style)
    return style
}
fun StyleContainer.attribute(name: String, body: Style.() -> Unit): Style = attribute(name).invoke(body)
fun StyleContainer.attribute(name: String): Style {
    val style = Style()
    style.selector = "[$name]"
    styles.add(style)
    return style
}

fun Style.media(query: String, body: Style.() -> Unit): Style = media(query).invoke(body)
fun Style.media(query: String): Style {
    val container = StyleContainer()
    container.combinator = "@media ($query)"
    containers.add(container)
    val style = Style()
    style.selector = ""
    container.styles.add(style)
    return style
}

val Style.not: StyleContainer
    get() {
        val container = StyleContainer()
        container.combinator = "!"
        containers.add(container)
        return container
    }

val Style.or: StyleContainer
    get() {
        val container = StyleContainer()
        container.combinator = ","
        containers.add(container)
        return container
    }

fun StyleContainer.id(name: String): Style {
    val style = Style().id(name)
    styles.add(style)
    return style
}
fun StyleContainer.id(name: String, body: Style.() -> Unit): Style = id(name).invoke(body)
fun Style.id(name: String, body: Style.() -> Unit): Style = id(name).invoke(body)
fun Style.id(name: String): Style {
    if (selector == "*")
        selector = "#$name"
    else
        selector += "#$name"
    return this

}

fun StyleContainer.style(name: String): Style {
    val style = Style().style(name)
    styles.add(style)
    return style
}
fun StyleContainer.style(name: String, body: Style.() -> Unit): Style = style(name).invoke(body)
fun Style.style(name: String, body: Style.() -> Unit): Style = style(name).invoke(body)
fun Style.style(name: String): Style {
    val container = StyleContainer()
    container.combinator = ""
    val style = Style()
    style.selector = ".$name"
    containers.add(container)
    container.styles.add(style)
    return style
}

fun StyleContainer.immediate(body: StyleContainer.() -> Unit): StyleContainer = next.invoke(body)
val StyleContainer.immediate: StyleContainer
    get() {
        val style = Style()
        styles.add(style)
        val container = StyleContainer()
        container.combinator = ">"
        style.containers.add(container)
        return container
    }

fun Style.immediate(body: StyleContainer.() -> Unit): StyleContainer = immediate.invoke(body)
val Style.immediate: StyleContainer
    get() {
        val container = StyleContainer()
        container.combinator = ">"
        containers.add(container)
        return container
    }

fun StyleContainer.next(body: StyleContainer.() -> Unit): StyleContainer = next.invoke(body)
val StyleContainer.next: StyleContainer
    get() {
        val style = Style()
        styles.add(style)
        val container = StyleContainer()
        container.combinator = "+"
        style.containers.add(container)
        return container
    }

fun Style.next(body: StyleContainer.() -> Unit): StyleContainer = next.invoke(body)
val Style.next: StyleContainer
    get() {
        val container = StyleContainer()
        container.combinator = "+"
        containers.add(container)
        return container
    }

fun Style.after(body: StyleContainer.() -> Unit): StyleContainer = after.invoke(body)
val Style.after: StyleContainer
    get() {
        val container = StyleContainer()
        container.combinator = "~"
        containers.add(container)
        return container
    }

fun Style.plus(body: StyleContainer.() -> Unit): StyleContainer = nested.invoke(body)
fun Style.plus(): StyleContainer = nested

fun Style.nested(body: StyleContainer.() -> Unit): StyleContainer = nested.invoke(body)
val Style.nested: StyleContainer
    get() {
        val container = StyleContainer()
        container.combinator = " "
        containers.add(container)
        return container
    }

class TagProperty() {
    operator fun getValue(container: StyleContainer, property : KProperty<*>) : Style = container.tag(property.name)
}

val StyleContainer.a: Style by TagProperty()
val StyleContainer.abbr: Style by TagProperty()
val StyleContainer.acronym: Style by TagProperty()
val StyleContainer.address: Style by TagProperty()
val StyleContainer.applet: Style by TagProperty()
val StyleContainer.area: Style by TagProperty()
val StyleContainer.article: Style by TagProperty()
val StyleContainer.aside: Style by TagProperty()
val StyleContainer.audio: Style by TagProperty()
val StyleContainer.b: Style by TagProperty()
val StyleContainer.base: Style by TagProperty()
val StyleContainer.basefont: Style by TagProperty()
val StyleContainer.bdi: Style by TagProperty()
val StyleContainer.bdo: Style by TagProperty()
val StyleContainer.big: Style by TagProperty()
val StyleContainer.blockquote: Style by TagProperty()
val StyleContainer.body: Style by TagProperty()
val StyleContainer.br: Style by TagProperty()
val StyleContainer.button: Style by TagProperty()
val StyleContainer.canvas: Style by TagProperty()
val StyleContainer.caption: Style by TagProperty()
val StyleContainer.center: Style by TagProperty()
val StyleContainer.cite: Style by TagProperty()
val StyleContainer.code: Style by TagProperty()
val StyleContainer.col: Style by TagProperty()
val StyleContainer.colgroup: Style by TagProperty()
val StyleContainer.command: Style by TagProperty()
val StyleContainer.datalist: Style by TagProperty()
val StyleContainer.dd: Style by TagProperty()
val StyleContainer.del: Style by TagProperty()
val StyleContainer.details: Style by TagProperty()
val StyleContainer.dfn: Style by TagProperty()
val StyleContainer.dialog: Style by TagProperty()
val StyleContainer.dir: Style by TagProperty()
val StyleContainer.div: Style by TagProperty()
val StyleContainer.dl: Style by TagProperty()
val StyleContainer.dt: Style by TagProperty()
val StyleContainer.em: Style by TagProperty()
val StyleContainer.embed: Style by TagProperty()
val StyleContainer.fieldset: Style by TagProperty()
val StyleContainer.figcaption: Style by TagProperty()
val StyleContainer.figure: Style by TagProperty()
val StyleContainer.font: Style by TagProperty()
val StyleContainer.footer: Style by TagProperty()
val StyleContainer.form: Style by TagProperty()
val StyleContainer.frame: Style by TagProperty()
val StyleContainer.frameset: Style by TagProperty()
val StyleContainer.head: Style by TagProperty()
val StyleContainer.header: Style by TagProperty()
val StyleContainer.h1: Style by TagProperty()
val StyleContainer.h2: Style by TagProperty()
val StyleContainer.h3: Style by TagProperty()
val StyleContainer.h4: Style by TagProperty()
val StyleContainer.h5: Style by TagProperty()
val StyleContainer.h6: Style by TagProperty()
val StyleContainer.hr: Style by TagProperty()
val StyleContainer.html: Style by TagProperty()
val StyleContainer.i: Style by TagProperty()
val StyleContainer.iframe: Style by TagProperty()
val StyleContainer.img: Style by TagProperty()
val StyleContainer.input: Style by TagProperty()
val StyleContainer.ins: Style by TagProperty()
val StyleContainer.kbd: Style by TagProperty()
val StyleContainer.keygen: Style by TagProperty()
val StyleContainer.label: Style by TagProperty()
val StyleContainer.legend: Style by TagProperty()
val StyleContainer.li: Style by TagProperty()
val StyleContainer.link: Style by TagProperty()
val StyleContainer.map: Style by TagProperty()
val StyleContainer.mark: Style by TagProperty()
val StyleContainer.menu: Style by TagProperty()
val StyleContainer.meta: Style by TagProperty()
val StyleContainer.meter: Style by TagProperty()
val StyleContainer.nav: Style by TagProperty()
val StyleContainer.noframes: Style by TagProperty()
val StyleContainer.noscript: Style by TagProperty()
val StyleContainer.ol: Style by TagProperty()
val StyleContainer.optgroup: Style by TagProperty()
val StyleContainer.option: Style by TagProperty()
val StyleContainer.output: Style by TagProperty()
val StyleContainer.p: Style by TagProperty()
val StyleContainer.param: Style by TagProperty()
val StyleContainer.pre: Style by TagProperty()
val StyleContainer.progress: Style by TagProperty()
val StyleContainer.q: Style by TagProperty()
val StyleContainer.rp: Style by TagProperty()
val StyleContainer.rt: Style by TagProperty()
val StyleContainer.ruby: Style by TagProperty()
val StyleContainer.s: Style by TagProperty()
val StyleContainer.samp: Style by TagProperty()
val StyleContainer.script: Style by TagProperty()
val StyleContainer.section: Style by TagProperty()
val StyleContainer.select: Style by TagProperty()
val StyleContainer.small: Style by TagProperty()
val StyleContainer.source: Style by TagProperty()
val StyleContainer.span: Style by TagProperty()
val StyleContainer.strike: Style by TagProperty()
val StyleContainer.strong: Style by TagProperty()
val StyleContainer.style: Style by TagProperty()
val StyleContainer.sub: Style by TagProperty()
val StyleContainer.summary: Style by TagProperty()
val StyleContainer.sup: Style by TagProperty()
val StyleContainer.table: Style by TagProperty()
val StyleContainer.tbody: Style by TagProperty()
val StyleContainer.td: Style by TagProperty()
val StyleContainer.textarea: Style by TagProperty()
val StyleContainer.tfoot: Style by TagProperty()
val StyleContainer.th: Style by TagProperty()
val StyleContainer.thead: Style by TagProperty()
val StyleContainer.time: Style by TagProperty()
val StyleContainer.title: Style by TagProperty()
val StyleContainer.tr: Style by TagProperty()
val StyleContainer.track: Style by TagProperty()
val StyleContainer.tt: Style by TagProperty()
val StyleContainer.u: Style by TagProperty()
val StyleContainer.ul: Style by TagProperty()
val StyleContainer.video: Style by TagProperty()
val StyleContainer.wbr: Style by TagProperty()

fun Style.border(width: LinearDimension) = border("$width solid")

fun Style.box_sizing(boxModel: String) {
    property("box-sizing", boxModel)
    property("-webkit-box-sizing", boxModel)
    property("-moz-box-sizing", boxModel)
}


enum class LinearUnits(val value: String) {
    percent("%"),
    em("em"),
    px("px"),
    auto("auto");
    override fun toString(): String {
        return value
    }
}

/** Represents a single linear dimension.
 */
class LinearDimension(var value: Double, var units: LinearUnits) {
    companion object {
        /** Creates a linear dimension from a string literal */
        fun fromString(s: String): LinearDimension {
            if (s.endsWith("em"))
                return LinearDimension(s.substring(0, s.length - 2).toDouble(), LinearUnits.em)
            if (s.endsWith("px"))
                return LinearDimension(s.substring(0, s.length - 2).toDouble(), LinearUnits.px)
            if (s.endsWith("%"))
                return LinearDimension(s.substring(0, s.length - 1).toDouble(), LinearUnits.percent)
            throw Exception("Invalid linear dimension: $s")
        }
    }

    override fun toString(): String {
        if (units == LinearUnits.auto)
            return "auto"
        return "${DecimalFormat("#").format(value)}$units"
    }
}

/** Use this instance to specify the auto keyword for linear dimensions. */
val auto: LinearDimension = LinearDimension(0.0, LinearUnits.auto)

/** Returns true if the given string represents a valid linear dimension */
fun isLinearDimension(s: String): Boolean {
    return s.endsWith("px") || s.endsWith("em") || s.endsWith("%")
}

/** Extension property to convert a double to a LinearDimension with units em. */
val Double.em: LinearDimension
    get() {
        return LinearDimension(this, LinearUnits.em)
    }

/** Extension property to convert an int to a LinearDimension with units em. */
val Int.em: LinearDimension
    get() {
        return LinearDimension(this.toDouble(), LinearUnits.em)
    }

/** Extension property to convert a double to a LinearDimension with units px. */
val Double.px: LinearDimension
    get() {
        return LinearDimension(this, LinearUnits.px)
    }

/** Extension property to convert an int to a LinearDimension with units px. */
val Int.px: LinearDimension
    get() {
        return LinearDimension(this.toDouble(), LinearUnits.px)
    }

/** Extension property to convert a double to a LinearDimension with units percent. */
val Double.percent: LinearDimension
    get() {
        return LinearDimension(this, LinearUnits.percent)
    }

/** Extension property to convert an int to a LinearDimension with units percent. */
val Int.percent: LinearDimension
    get() {
        return LinearDimension(this.toDouble(), LinearUnits.percent)
    }

/** Stores 4 linear dimensions that describe a box, like padding and margin.
 */
class BoxDimensions(var top: LinearDimension, var right: LinearDimension, var bottom: LinearDimension, var left: LinearDimension) {

    override fun toString(): String {
        return "$top $right $bottom $left"
    }
}

/** Convenience function for making a BoxDimensions with all dimensions the same. */
fun box(all: LinearDimension): BoxDimensions {
    return BoxDimensions(all, all, all, all)
}

/** Convenience function for making a BoxDimensions with top/bottom and left/right values. */
fun box(topBottom: LinearDimension, leftRight: LinearDimension): BoxDimensions {
    return BoxDimensions(topBottom, leftRight, topBottom, leftRight)
}

/** Convenience function for making a BoxDimensions with all four dimensions. */
fun box(top: LinearDimension, right: LinearDimension, bottom: LinearDimension, left: LinearDimension): BoxDimensions {
    return BoxDimensions(top, right, bottom, left)
}

val animation = CssAnimation()
class CssAnimation() : CssProperty("animation") {
    val delay by CssSubProperty()
    val direction by CssSubProperty()
    val duration by CssSubProperty()
    val fill_mode by CssSubProperty()
    val iteration_count by CssSubProperty()
    val name by CssSubProperty()
    val play_state by CssSubProperty()
    val timing_function by CssSubProperty()
}

fun Style.appearance(value: String) = property("appearance", value)
fun Style.backface_visibility(value: String) = property("backface-visibility", value)

val background = CssBackground()
class CssBackground() : CssProperty("background") {
    val color by CssSubProperty()
    val image by CssSubProperty()
    val attachment by CssSubProperty()
    val clip by CssSubProperty()
    val origin by CssSubProperty()
    val position by CssSubProperty()
    val repeat by CssSubProperty()
    val size by CssSubProperty()
}

val padding = CssSides("padding")
val margin = CssSides("margin")

class CssSides(name : String) : CssProperty(name) {
    val left by CssSubProperty()
    val right by CssSubProperty()
    val top by CssSubProperty()
    val bottom by CssSubProperty()
}

open class CssBorderStyle(name : String, parent : CssProperty? = null) : CssProperty(name, parent) {
    val width by CssSubProperty()
    val style by CssSubProperty()
    val color by CssSubProperty()
}

val border = CssBorder()
class CssBorder() : CssBorderStyle("border") {
    val left = CssBorderStyle("left", this)
    val right = CssBorderStyle("right", this)
    val top = CssBorderStyle("top", this)
    val bottom = CssBorderStyle("bottom", this)

    val collapse by CssSubProperty()
    val image by CssSubProperty()
    val radius by CssSubProperty()
    val spacing by CssSubProperty()
}

fun Style.bottom(value: String) = property("bottom", value)
fun Style.box_shadow(value: String) = property("box-shadow", value)
fun Style.caption_side(value: String) = property("caption-side", value)
fun Style.clear(value: String) = property("clear", value)
fun Style.clip(value: String) = property("clip", value)
fun Style.color(value: String) = property("color", value)
fun Style.column(value: String) = property("column", value)
fun Style.content(value: String) = property("content", value)
fun Style.counter_increment(value: String) = property("counter-increment", value)
fun Style.counter_reset(value: String) = property("counter-reset", value)
fun Style.cursor(value: String) = property("cursor", value)
fun Style.direction(value: String) = property("direction", value)
fun Style.display(value: String) = property("display", value)
fun Style.empty_cells(value: String) = property("empty-cells", value)
fun Style.float(value: String) = property("float", value)

val font = CssFont()
class CssFont() : CssProperty("font") {
    val family by CssSubProperty()
    val size by CssSubProperty()
    val size_adjust by CssSubProperty()
    val stretch by CssSubProperty()
    val style by CssSubProperty()
    val variant by CssSubProperty()
    val weight by CssSubProperty()
}

fun Style.grid_columns(value: String) = property("grid-columns", value)
fun Style.grid_rows(value: String) = property("grid-rows", value)
fun Style.hanging_punctuation(value: String) = property("hanging-punctuation", value)
fun Style.height(value: String) = property("height", value)
fun Style.icon(value: String) = property("icon", value)
fun Style.left(value: String) = property("left", value)
fun Style.letter_spacing(value: String) = property("letter-spacing", value)
fun Style.line_height(value: String) = property("line-height", value)
fun Style.list_style(value: String) = property("list-style", value)
fun Style.max_height(value: String) = property("max-height", value)
fun Style.max_width(value: String) = property("max-width", value)
fun Style.min_height(value: String) = property("min-height", value)
fun Style.min_width(value: String) = property("min-width", value)
fun Style.nav(value: String) = property("nav", value)
fun Style.opacity(value: String) = property("opacity", value)
fun Style.outline(value: String) = property("outline", value)
fun Style.overflow(value: String) = property("overflow", value)
fun Style.overflow_x(value: String) = property("overflow-x", value)
fun Style.overflow_y(value: String) = property("overflow-y", value)
fun Style.page_break(value: String) = property("page-break", value)
fun Style.perspective(value: String) = property("perspective", value)
fun Style.perspective_origin(value: String) = property("perspective-origin", value)
fun Style.position(value: String) = property("position", value)
fun Style.punctuation_trim(value: String) = property("punctuation-trim", value)
fun Style.quotes(value: String) = property("quotes", value)
fun Style.resize(value: String) = property("resize", value)
fun Style.right(value: String) = property("right", value)
fun Style.rotation(value: String) = property("rotation", value)
fun Style.rotation_point(value: String) = property("rotation-point", value)
fun Style.table_layout(value: String) = property("table-layout", value)
fun Style.target(value: String) = property("target", value)

fun Style.text(value: String) = property("text", value)
fun Style.text_decoration(value: String) = property("text-decoration", value)
fun Style.text_align(value: String) = property("text-align", value)

fun Style.top(value: String) = property("top", value)
fun Style.transform(value: String) = property("transform", value)
fun Style.transition(value: String) = property("transition", value)
fun Style.unicode_bidi(value: String) = property("unicode-bidi", value)
fun Style.vertical_align(value: String) = property("vertical-align", value)
fun Style.visibility(value: String) = property("visibility", value)
fun Style.width(value: String) = property("width", value)
fun Style.white_space(value: String) = property("white-space", value)
fun Style.word_spacing(value: String) = property("word-spacing", value)
fun Style.word_break(value: String) = property("word-break", value)
fun Style.word_wrap(value: String) = property("word-wrap", value)
fun Style.z_index(value: String) = property("z-index", value)

fun Style.gradientVertical(color1: String, color2: String): Unit {
    background("$color1")
    background("-moz-linear-gradient(top,  $color1 0%, $color2 100%); /* FF3.6+ */")
    background("-webkit-gradient(linear, left top, left bottom, color-stop(0%,$color1), color-stop(100%,$color2)); /* Chrome,Safari4+ */")
    background("-webkit-linear-gradient(top,  $color1 0%,$color2 100%); /* Chrome10+,Safari5.1+ */")
    background("-o-linear-gradient(top,  $color1 0%,$color2 100%); /* Opera 11.10+ */")
    background("-ms-linear-gradient(top,  $color1 0%,$color2 100%); /* IE10+ */")
    background("linear-gradient(to bottom,  $color1 0%,$color2 100%); /* W3C */")
}

fun Style.roundBorder(width: LinearDimension, color: Color, radius: LinearDimension): Unit {
    border("${width} solid ${color}")
    border_radius(radius)
}

fun Style.border_radius(radius: LinearDimension): Unit {
    property("border-radius", radius.toString())
    property("-webkit-border-radius", radius.toString())
    property("-moz-border-radius", radius.toString())
}

fun Style.shadow(vararg shadows: String): Unit {
    val value = shadows.joinToString(", ")
    property("box-shadow", value)
    property("-moz-box-shadow", value)
    property("-webkit-box-shadow", value)
}

fun Style.outset(color: Color, hshift: Int = 0, vshift: Int = 1, blur: Int = 0, strength: Int = 0): String {
    return "${hshift}px ${vshift}px ${blur}px ${strength}px $color"
}

fun Style.inset(color: Color, hshift: Int = 0, vshift: Int = 1, blur: Int = 0, strength: Int = 0): String {
    return "${hshift}px ${vshift}px ${blur}px ${strength}px $color inset"
}


/** Container class for HSL values */
class HslValues(var hue: Double, var saturation: Double, var lightness: Double) {

    /** Sets the lightness value while ensuring it stays within range. */
    fun safeSetLightness(l: Double) {
        lightness = Math.min(1.0, Math.max(0.0, l))
    }

    /** Sets the saturation value while ensuring it stays within range. */
    fun safeSetSaturation(l: Double) {
        saturation = Math.min(1.0, Math.max(0.0, l))
    }
}


/** A general color class that stores colors as floating point RGBA values.
 */
class Color(var red: Double, var green: Double, var blue: Double, var alpha: Double = 1.0) {
    companion object {

        /** Creates a color from integer RGBA values between 0 and 255. */
        fun fromRgb(red: Int, green: Int, blue: Int, alpha: Int = 255): Color {
            return Color(red.toDouble() / 255, green.toDouble() / 255.0, blue.toDouble() / 255.0, alpha.toDouble() / 255.0)
        }

        /** Makes a color from a hex string (hash included). */
        fun fromHex(s : String) : Color {
            // TODO 4/8-digit are actually not supported by CSS spec, make framework parse rgb/rgba() format instead

            if (s.length == 4 && s[0] == '#') {
                val r = Integer.parseInt(s.substring(1, 2), 16)
                val g = Integer.parseInt(s.substring(2, 3), 16)
                val b = Integer.parseInt(s.substring(3, 4), 16)
                return Color.fromRgb(r * 16 + r, g * 16 + g, b * 16 + b)
            }
            if (s.length == 5 && s[0] == '#') {
                val r = Integer.parseInt(s.substring(1, 2), 16)
                val g = Integer.parseInt(s.substring(2, 3), 16)
                val b = Integer.parseInt(s.substring(3, 4), 16)
                val a = Integer.parseInt(s.substring(4, 5), 16)
                return Color.fromRgb(r * 16 + r, g * 16 + g, b * 16 + b, a * 16 + a)
            }
            if (s.length == 7 && s[0] == '#') {
                val r = Integer.parseInt(s.substring(1, 3), 16)
                val g = Integer.parseInt(s.substring(3, 5), 16)
                val b = Integer.parseInt(s.substring(5, 7), 16)
                return Color.fromRgb(r, g, b)
            }
            if (s.length == 9 && s[0] == '#') {
                val r = Integer.parseInt(s.substring(1, 3), 16)
                val g = Integer.parseInt(s.substring(3, 5), 16)
                val b = Integer.parseInt(s.substring(5, 7), 16)
                val a = Integer.parseInt(s.substring(7, 9), 16)
                return Color.fromRgb(r, g, b, a)
            }

            throw Exception("Invalid color hex string: $s");
        }

        /** Creates a color from a set of HSL values. */
        fun fromHsl(hsl: HslValues): Color {
            val color = Color(0.0, 0.0, 0.0)
            color.setHsl(hsl)
            return color
        }

    }

    /** Creates a copy of the color. */
    fun copy(): Color {
        return Color(red, green, blue, alpha)
    }

    var redInt: Int
        get() = (red * 255.0).toInt()
        set(value) { red = value.toDouble() / 255.0 }

    var greenInt: Int
        get() = (green * 255.0).toInt()
        set(value) { green = value.toDouble() / 255.0 }

    var blueInt: Int
        get() = (blue * 255.0).toInt()
        set(value) { blue = value.toDouble() / 255.0 }

    var alphaInt: Int
        get() = (alpha * 255.0).toInt()
        set(value) { alpha = value.toDouble() / 255.0}

    private fun Int.twoDigitHex(): String = Integer.toHexString(this).padStart(2, '0')

    val hexString: String
        get() = "#${redInt.twoDigitHex()}${greenInt.twoDigitHex()}${blueInt.twoDigitHex()}"

    override fun toString(): String {
        if (alpha < 1.0) {
            return "rgba($redInt, $greenInt, $blueInt, ${java.lang.String.format(Locale.ENGLISH, "%.3f", alpha)})"
        }
        else {
            return hexString
        }
    }


    /** Generate HSL values based the current RGB values. */
    fun toHsl(): HslValues {
        val max = Math.max(Math.max(red, green), blue)
        val min = Math.min(Math.min(red, green), blue)
        val avg = (max + min) / 2
        val hsl = HslValues(avg, avg, avg)

        if (max == min) {
            // achromatic
            hsl.hue = 0.0
            hsl.saturation = 0.0
        } else {
            val d = max - min
            if (hsl.lightness > 0.5)
                hsl.saturation = d / (2 - max - min)
            else
                hsl.saturation = d / (max + min)
            when (max) {
                red -> {
                    hsl.hue = (green - blue) / d
                    if (green < blue)
                        hsl.hue += 6.0
                }
                green -> hsl.hue = (blue - red) / d + 2
                blue -> hsl.hue = (red - green) / d + 4
                else -> {
                }
            }
            hsl.hue /= 6.0
        }
        return hsl
    }

    fun setHsl(hsl: HslValues) {
        if (hsl.saturation == 0.0) {
            // achromatic
            red = hsl.lightness
            green = hsl.lightness
            blue = hsl.lightness
        } else {
            fun hue2rgb(p: Double, q: Double, _t: Double): Double {
                var t = _t
                if(t < 0.0)
                    t += 1.0
                if(t > 1.0)
                    t -= 1.0
                if(t < 1.0 / 6.0)
                    return p + (q - p) * 6.0 * t;
                if(t < 0.5)
                    return q;
                if(t < 2.0 / 3.0)
                    return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
                return p;
            }
            val q: Double
            if (hsl.lightness < 0.5)
                q = hsl.lightness * (1 + hsl.saturation)
            else
                q = hsl.lightness + hsl.saturation - hsl.lightness * hsl.saturation
            val p = 2.0 * hsl.lightness - q;
            red = hue2rgb(p, q, hsl.hue + 1.0 / 3.0);
            green = hue2rgb(p, q, hsl.hue);
            blue = hue2rgb(p, q, hsl.hue - 1.0 / 3.0);
        }
    }


    /** Increases the lightness of the color by the given amount (should be between 0 and 1). */
    fun lighten(dl: Double): Color {
        val hsl = toHsl()
        hsl.safeSetLightness(hsl.lightness + dl)
        setHsl(hsl)
        return this
    }

    /** Decreases the lightness of the color by the given amount (should be between 0 and 1). */
    fun darken(dl: Double): Color {
        val hsl = toHsl()
        hsl.safeSetLightness(hsl.lightness - dl)
        setHsl(hsl)
        return this
    }

    /** Increases the saturation of the color by the given amount (should be between 0 and 1). */
    fun saturate(dl: Double): Color {
        val hsl = toHsl()
        hsl.safeSetSaturation(hsl.lightness + dl)
        setHsl(hsl)
        return this
    }

    /** Decreases the saturation of the color by the given amount (should be between 0 and 1). */
    fun desaturate(dl: Double): Color {
        val hsl = toHsl()
        hsl.safeSetSaturation(hsl.lightness - dl)
        setHsl(hsl)
        return this
    }

}

/** Smart helper function that creates a color from a string.
 * Currently, s can be:
 *  - a 3 digit hex string for RGB: #F9B
 *  - a 4 digit hex string for RGBA: #F9B8
 *  - a 6 digit hex string for RGB: #FE395A
 *  - a 8 digit hex string for RGBA: #FE395A88
 */

fun color(s: String): Color {
    if (s.startsWith("#"))
        return Color.fromHex(s)
    throw Exception("Invalid color string: ${s}");
}

/** Returns true if the string is a valid color literal. */
fun isColor(s: String): Boolean {
    return s.startsWith("#") || s.startsWith("rgb")
}