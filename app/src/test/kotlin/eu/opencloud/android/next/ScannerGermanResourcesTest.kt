package eu.opencloud.android.next

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-rDE")
class ScannerGermanResourcesTest {
    @Test
    @Config(qualifiers = "en")
    fun scannerEnglishDefaultsRemainAvailableFromLibrary() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Take photo", context.getString(dev.offlinescan.ui.R.string.action_shutter))
        assertEquals(
            "2 of 5 pages",
            context.resources.getQuantityString(dev.offlinescan.ui.R.plurals.save_page_position, 5, 2, 5),
        )
    }

    @Test fun scannerLibraryUsesGermanHostResourcesAndKeepsFormatArguments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Foto aufnehmen", context.getString(dev.offlinescan.ui.R.string.action_shutter))
        assertEquals("Seite 2 von 5", context.getString(dev.offlinescan.ui.R.string.review_page_position, 2, 5))
        assertEquals("Speicherort", context.getString(dev.offlinescan.ui.R.string.save_location_label))
        assertEquals(
            "2 von 5 Seiten",
            context.resources.getQuantityString(dev.offlinescan.ui.R.plurals.save_page_position, 5, 2, 5),
        )
    }
}
