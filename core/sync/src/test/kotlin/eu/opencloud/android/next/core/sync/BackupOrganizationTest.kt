package eu.opencloud.android.next.core.sync

import android.net.Uri
import eu.opencloud.android.next.core.database.FolderBackupEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BackupOrganizationTest {
    private val backup =
        FolderBackupEntity(
            "pair",
            "account",
            "space",
            "content://source/tree/root",
            destinationPath = "/Photos",
            mediaType = "ALL",
            wifiOnly = false,
            chargingOnly = false,
            deleteAfterUpload = false,
        )
    private val document =
        BackupDocument(
            Uri.parse("content://source/file"),
            "picture.jpg",
            "Camera",
            "image/jpeg",
            Instant.parse("2026-09-29T23:59:59Z").toEpochMilli(),
            10,
        )

    @Test fun `date organization preserves subfolders and handles absent timestamps explicitly`() {
        assertEquals("/Photos/Camera", backupDestination(backup, document))
        val dated = backup.copy(dateOrganization = "YEAR_MONTH")
        assertEquals("/Photos/2026/09/Camera", backupDestination(dated, document))
        assertEquals("/Photos/Undated/Camera", backupDestination(dated, document.copy(modified = 0)))
    }

    @Test fun `changing destination or organization does not reuse receipts from the old target`() {
        val key = backupReceiptKey(backup, document)
        assertNotEquals(key, backupReceiptKey(backup.copy(destinationPath = "/Other"), document))
        assertNotEquals(key, backupReceiptKey(backup.copy(dateOrganization = "YEAR_MONTH"), document))
        assertNotEquals(key, backupReceiptKey(backup, document.copy(relativeParent = "Edited")))
    }
}
