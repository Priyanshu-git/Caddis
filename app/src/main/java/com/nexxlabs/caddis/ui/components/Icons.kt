package com.nexxlabs.caddis.ui.components

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
    // Settings rows.
    val Tag = icon("tag", "M3.5 12.2V4.5a1 1 0 011-1h7.7l8 8a1 1 0 010 1.4l-7.3 7.3a1 1 0 01-1.4 0z", circle(8f, 8f, 1.2f))
    val Bell = icon("bell", "M6 17 L6 11 A6 6 0 0 1 18 11 L18 17 L19.5 19 L4.5 19 Z", "M10 21 L14 21")
    val CalendarWeek = icon("calendar-week", rect(4f, 5f, 16f, 15f, 2f), "M4 10h16M8 3v4M16 3v4")
    val Battery = icon("battery", rect(3f, 8f, 16f, 8f, 2f), "M21 11v2M6.5 11v2")
    val Download = icon("download", "M12 4v11M7.5 11l4.5 4.5 4.5-4.5M5 20h14")
    val Refresh = icon("refresh", "M4 12 A8 8 0 0 1 18 6.7 L20 9 M20 4.5 L20 9 L15.5 9 M20 12 A8 8 0 0 1 6 17.3 L4 15 M4 19.5 L4 15 L8.5 15")
    val Star = icon("star", "M12 3.8l2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z")
    val Mail = icon("mail", rect(3f, 5f, 18f, 14f, 2f), "M4 7l8 6 8-6")
    val Shield = icon("shield", "M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6z", "M9 12l2 2 4-4")
    val ExternalLink = icon("external-link", "M14 4 L20 4 L20 10 M20 4 L11 13 M18 14 L18 19 A1 1 0 0 1 17 20 L5 20 A1 1 0 0 1 4 19 L4 7 A1 1 0 0 1 5 6 L10 6")
    val Phone = icon("phone", rect(7f, 3f, 10f, 18f, 2f), "M11 18h2")
    val Info = icon("info", circle(12f, 12f, 9f), "M12 11v5M12 7.8v.1")
    val Up = icon("up", "M6 15l6-6 6 6")
    val BellOff = icon("bell-off", "M6 17 L6 11 A6 6 0 0 1 7.5 7 M18 11 L18 17 L19.5 19 L9 19 M10 21 L14 21 M4 4 L20 20")
    /** A gear, for the Settings tab (Feather's "settings" outline, MIT). */
    val Gear = icon(
        "gear", circle(12f, 12f, 3f),
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z",
    )
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
    val Edit = icon("edit", "M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4")
    /** Three linked nodes, like the Android share sheet. */
    val Share = icon("share", circle(18f, 5.5f, 2.5f), circle(6f, 12f, 2.5f), circle(18f, 18.5f, 2.5f), "M8.2 10.8l7.6-4.1M8.2 13.2l7.6 4.1")
    /** A clock running back: "bring it back later". */
    val BringBack = icon("bring-back", "M3.5 12a8.5 8.5 0 1 0 2.6-6.1L3.5 8.5", "M3.5 3.5v5h5", "M12 7.5V12l3 2")
    val Mark = icon("mark", circle(9f, 9f, 3f), circle(16f, 11f, 2f), circle(12f, 17f, 3.2f))

    // Note editor toolbar.
    val Bold = icon("bold", "M7.5 5 H13 A3.5 3.5 0 0 1 13 12 H7.5 Z M7.5 12 H14 A3.5 3.5 0 0 1 14 19 H7.5 Z")
    val Italic = icon("italic", "M10 5h8M6 19h8M14.5 5l-5 14")
    val BulletList = icon("bullet-list", "M10 7h10M10 12h10M10 17h10", circle(5f, 7f, 0.5f), circle(5f, 12f, 0.5f), circle(5f, 17f, 0.5f))
    val NumberList = icon("number-list", "M11 7h9M11 12h9M11 17h9", "M4.5 5.5l1.5-1v5", "M4 14.6 A1.4 1.4 0 0 1 6.6 15.2 C6.6 16.3 4 17.4 4 18.5 H6.8")
    val Code = icon("code", "M8 8l-4 4 4 4M16 8l4 4-4 4M13.5 5.5l-3 13")
    val Link = icon("link", "M10 14 A4 4 0 0 0 15.7 14 L18.7 11 A4 4 0 0 0 13 5.3 L12 6.3", "M14 10 A4 4 0 0 0 8.3 10 L5.3 13 A4 4 0 0 0 11 18.7 L12 17.7")
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9 H14.5 A5.5 5.5 0 0 1 14.5 20 H11")
}
