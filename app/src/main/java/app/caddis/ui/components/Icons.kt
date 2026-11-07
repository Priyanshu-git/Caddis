package app.caddis.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** The prototype's 24×24 stroked icon set, rebuilt as ImageVectors (stroke 1.8, round caps). */
object CIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx},${y}h${w - 2 * rx}a$rx,$rx 0 0,1 $rx,$rx" +
            "v${h - 2 * rx}a$rx,$rx 0 0,1 ${-rx},$rx" +
            "h${-(w - 2 * rx)}a$rx,$rx 0 0,1 ${-rx},${-rx}" +
            "v${-(h - 2 * rx)}a$rx,$rx 0 0,1 $rx,${-rx}z"

    private fun icon(name: String, vararg paths: String, fill: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { p ->
                addPath(
                    pathData = addPathNodes(p),
                    fill = if (fill) SolidColor(Color.Black) else null,
                    stroke = if (fill) null else SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Back = icon("back", "M19 12H5M11 6l-6 6 6 6")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Search = icon("search", circle(11f, 11f, 6.5f), "M16 16l4.5 4.5")
    val House = icon("house", "M4 11l8-6.5L20 11v8a1 1 0 01-1 1h-4v-5H9v5H5a1 1 0 01-1-1z")
    val Library = icon("library", rect(4f, 4f, 4f, 16f, 1f), rect(10f, 4f, 4f, 16f, 1f), "M16.5 5.5l3.2 14")
    val User = icon("user", circle(12f, 9f, 3.5f), "M5 20c1.2-4 4-5.5 7-5.5s5.8 1.5 7 5.5")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val Close = icon("x", "M6 6l12 12M18 6L6 18")
    val Play = icon("play", "M8 5.5v13l11-6.5z")
    val Doc = icon("doc", "M6 4h9l3 3v13H6zM9 11h6M9 15h6")
    val Note = icon("note", "M5 5h14v10l-5 5H5zM14 20v-5h5")
    val More = icon("more", circle(12f, 5.5f, 1.4f), circle(12f, 12f, 1.4f), circle(12f, 18.5f, 1.4f))
    val Chevron = icon("chev", "M9 6l6 6-6 6")
    val Filter = icon("filter", "M4 6h16M7 12h10M10 18h4")
    val Trash = icon("trash", "M5 7h14M10 7V5h4v2M7 7l1 12h8l1-12M10 11v5M14 11v5")
    val Down = icon("down", "M6 9l6 6 6-6")
    val Alert = icon("alert", circle(12f, 12f, 9f), "M12 7.5v5M12 16.2v.1")
    val Mark = icon("mark", circle(9f, 9f, 3f), circle(16f, 11f, 2f), circle(12f, 17f, 3.2f))
}
