package eu.opencloud.android.next.core.sync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import eu.opencloud.android.next.core.network.OpenCloudError
import eu.opencloud.android.next.core.network.OpenCloudException
import java.io.InputStream

fun isSystemMediaSource(uri: Uri): Boolean =
    uri.scheme == "content" &&
        uri.authority in
        setOf(
            MediaStore.AUTHORITY,
            "com.android.providers.media.documents",
            "com.android.externalstorage.documents",
        )

/** True only for image collection URIs where the app can request MediaStore's original bytes. */
fun isEligibleOriginalMediaSource(
    context: Context,
    uri: Uri,
): Boolean =
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || uri.scheme != "content" -> false
        uri.authority == MediaStore.AUTHORITY -> isMediaCollectionItem(uri) && isImage(context, uri)
        // Keep the picker-granted document URI as the selected source. MediaDocumentsProvider
        // chooses the representation it exposes; converting it to MediaStore would change the
        // URI and cannot guarantee original, unredacted bytes.
        uri.authority == "com.android.providers.media.documents" -> false
        else -> false
    }

private fun isImage(
    context: Context,
    uri: Uri,
): Boolean =
    if (uri.pathSegments.getOrNull(1) == "images") {
        true
    } else {
        try {
            context.contentResolver.getType(uri)?.startsWith("image/", ignoreCase = true) == true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

private fun isMediaCollectionItem(uri: Uri): Boolean {
    val parts = uri.pathSegments
    return (
        parts.size == 4 &&
            parts[1] == "images" &&
            parts[2] == "media" ||
            parts.size == 3 &&
            parts[1] == "file"
    ) &&
        parts.last().toLongOrNull() != null
}

/** True when the upload source will request MediaStore's unredacted media representation. */
internal fun requestsOriginalMedia(
    context: Context,
    uri: Uri,
): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        isEligibleOriginalMediaSource(context, uri) &&
        context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Provider metadata can describe the redacted representation while reads return the original. */
internal fun uploadStagingExpectedLength(
    context: Context,
    uri: Uri,
    declaredLength: Long,
): Long = if (requestsOriginalMedia(context, uri)) -1 else declaredLength

/** Request original bytes only for eligible MediaStore images; never retry a failed original read. */
fun openUploadSource(
    context: Context,
    uri: Uri,
): InputStream {
    val original =
        if (requestsOriginalMedia(context, uri)) {
            MediaStore.setRequireOriginal(uri)
        } else {
            uri
        }
    return context.contentResolver.openInputStream(original)
        ?: throw OpenCloudException(OpenCloudError.SourceUnavailable)
}
