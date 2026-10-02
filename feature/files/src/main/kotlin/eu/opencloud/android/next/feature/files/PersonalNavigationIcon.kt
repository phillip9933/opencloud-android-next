package eu.opencloud.android.next.feature.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions

/** Keep labels aligned while preserving the standard indicator's padding around the larger icon. */
@Composable
internal fun PersonalNavigationIcon(
    icon: ImageVector,
    selected: Boolean,
) {
    val indicator = MaterialTheme.colorScheme.secondaryContainer
    Box(
        Modifier.size(OpenCloudDimensions.IconMedium).drawBehind {
            if (selected) {
                val width = OpenCloudDimensions.PersonalIndicatorWidth.toPx()
                val height = OpenCloudDimensions.PersonalIndicatorHeight.toPx()
                drawRoundRect(
                    color = indicator,
                    topLeft =
                        Offset(
                            (size.width - width) / 2f,
                            (size.height - height) / 2f - OpenCloudDimensions.SpacingXxs.toPx(),
                        ),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(height / 2f),
                )
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            Modifier
                .requiredSize(
                    OpenCloudDimensions.PersonalNavigationIconSize,
                ).offset(y = -OpenCloudDimensions.SpacingXxs),
        )
    }
}
