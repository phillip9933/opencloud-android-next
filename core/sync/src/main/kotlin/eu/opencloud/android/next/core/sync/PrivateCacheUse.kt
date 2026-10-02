package eu.opencloud.android.next.core.sync

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Process-local ownership for ordinary download staging and publication versus orphan cleanup. */
internal object PrivateCacheUse {
    private val gate = Mutex()
    private val active = mutableMapOf<String, Int>()

    suspend fun <T> hold(
        files: List<File>,
        action: suspend () -> T,
    ): T {
        val lease = acquire(files)
        try {
            return action()
        } finally {
            lease.close()
        }
    }

    suspend fun acquire(files: List<File>): LocalCopyLease {
        val keys =
            files
                .map {
                    it.absoluteFile
                        .toPath()
                        .normalize()
                        .toString()
                }.distinct()
        gate.withLock { keys.forEach { active[it] = (active[it] ?: 0) + 1 } }
        return LocalCopyLease {
            withContext(NonCancellable) {
                gate.withLock {
                    keys.forEach { key ->
                        val count = requireNotNull(active[key]) - 1
                        if (count == 0) active.remove(key) else active[key] = count
                    }
                }
            }
        }
    }

    suspend fun removeIfUnused(
        file: File,
        eligible: suspend () -> Boolean,
    ): Boolean = ifUnused(file) { eligible() && file.delete() }

    suspend fun ifUnused(
        file: File,
        action: suspend () -> Boolean,
    ): Boolean =
        gate.withLock {
            val key =
                file.absoluteFile
                    .toPath()
                    .normalize()
                    .toString()
            if (key in active) return@withLock false
            action()
        }
}
