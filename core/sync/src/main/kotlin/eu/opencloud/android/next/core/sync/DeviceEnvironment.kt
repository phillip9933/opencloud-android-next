package eu.opencloud.android.next.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs

fun interface NetworkStatus {
    fun isConnected(): Boolean
}

fun interface StorageSpaceProvider {
    fun availableBytes(): Long
}

class AndroidNetworkStatus(
    context: Context,
) : NetworkStatus {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun isConnected(): Boolean =
        connectivity
            .getNetworkCapabilities(connectivity.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}

class AndroidStorageSpaceProvider(
    context: Context,
) : StorageSpaceProvider {
    private val directory = context.applicationContext.filesDir

    override fun availableBytes(): Long = StatFs(directory.absolutePath).availableBytes
}
