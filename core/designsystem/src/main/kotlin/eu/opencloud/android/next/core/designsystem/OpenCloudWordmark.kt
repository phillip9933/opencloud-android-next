package eu.opencloud.android.next.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import eu.opencloud.android.next.core.designsystem.theme.OpenCloudDimensions

@Composable
fun OpenCloudWordmark(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Image(
        painterResource(if (dark) R.drawable.opencloud_wordmark_dark else R.drawable.opencloud_wordmark_light),
        "OpenCloud",
        modifier.size(OpenCloudDimensions.WordmarkWidth, OpenCloudDimensions.WordmarkHeight),
    )
}
