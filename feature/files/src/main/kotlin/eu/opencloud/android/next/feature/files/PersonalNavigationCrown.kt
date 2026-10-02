package eu.opencloud.android.next.feature.files

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions

/** A pill-shaped extension echoes the selected navigation indicator around the raised icon. */
internal fun Modifier.personalNavigationCrown(color: Color): Modifier =
    drawBehind {
        val width = OpenCloudDimensions.PersonalCrownWidth.toPx()
        val height = OpenCloudDimensions.TouchTarget.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset((size.width - width) / 2f, -OpenCloudDimensions.SpacingSm.toPx()),
            size = Size(width, height),
            cornerRadius = CornerRadius(height / 2f),
        )
    }
