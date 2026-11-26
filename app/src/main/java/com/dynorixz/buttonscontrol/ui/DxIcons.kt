package com.dynorixz.buttonscontrol.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small vector set shipped with Dx instead of the entire extended icon catalogue. */
object DxIcons {
    private val paths = mapOf(
        "info" to "M21,12A9,9 0,1 1,3 12A9,9 0,1 1,21 12 M12,11V17 M12,7V7.1",
        "send" to "M3,10L21,3L16,21L11,13Z M11,13L21,3",
        "code" to "M8,6L2,12L8,18 M16,6L22,12L16,18 M14,3L10,21",
        "heart" to "M12,21L3,12Q-1,3 7,3Q10,3 12,7Q14,3 17,3Q25,3 21,12Z",
        "apps" to "M3,3H9V9H3Z M15,3H21V9H15Z M3,15H9V21H3Z M15,15H21V21H15Z",
        "wifi" to "M3,8Q12,1 21,8 M6,12Q12,7 18,12 M9,16Q12,13 15,16 M12,20L12,20.1",
        "bluetooth" to "M7,6L17,16L12,21V3L17,8L7,18",
        "sun" to "M16,12A4,4 0,1 1,8 12A4,4 0,1 1,16 12 M12,2V4 M12,20V22 M2,12H4 M20,12H22 M5,5L6.5,6.5 M17.5,17.5L19,19 M5,19L6.5,17.5 M17.5,6.5L19,5",
        "moon" to "M20,14A8,8 0,1 1,10 4Q7,10 11,14Q15,18 20,14Z",
        "volume" to "M3,9H7L12,5V19L7,15H3Z M16,8Q20,12 16,16 M19,5Q25,12 19,19",
        "timer" to "M10,2H14 M12,2V5 M19,5L21,7 M19,13A7,7 0,1 1,5 13A7,7 0,1 1,19 13 M12,9V13L15,15",
        "alarm" to "M19,13A7,7 0,1 1,5 13A7,7 0,1 1,19 13 M12,9V13L15,15 M3,5L6,2 M18,2L21,5 M7,19L5,22 M17,19L19,22",
        "calendar" to "M4,5H20V21H4Z M4,10H20 M8,3V7 M16,3V7 M8,14H10 M14,14H16 M8,17H10 M14,17H16",
        "mic" to "M9,5Q9,2 12,2Q15,2 15,5V11Q15,14 12,14Q9,14 9,11Z M6,10V11Q6,17 12,17Q18,17 18,11V10 M12,17V21 M9,21H15",
        "phone" to "M8,2H16Q18,2 18,4V20Q18,22 16,22H8Q6,22 6,20V4Q6,2 8,2Z M10,5H14 M11,19H13",
        "next" to "M5,5L15,12L5,19Z M19,5V19",
        "previous" to "M19,5L9,12L19,19Z M5,5V19",
        "block" to "M21,12A9,9 0,1 1,3 12A9,9 0,1 1,21 12 M6,6L18,18",
        "vibration" to "M8,5H16V19H8Z M4,6L2,9L4,12L2,15L4,18 M20,6L22,9L20,12L22,15L20,18",
        "bolt" to "M13,2L4,14H11L10,22L20,10H13Z"
    )
    private val icons = paths.mapValues { (name, data) ->
        ImageVector.Builder(name = "Dx_$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(pathData = PathParser().parsePathString(data).toNodes(), fill = null, stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
    }
    fun get(name: String) = icons[name] ?: icons.getValue("bolt")
}
