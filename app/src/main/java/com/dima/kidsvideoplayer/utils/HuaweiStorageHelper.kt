package com.dima.kidsvideoplayer.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File

object HuaweiStorageHelper {

    private const val TAG = "HuaweiStorageHelper"

    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("huawei") || manufacturer.contains("honor")
    }

    fun getAvailableStorageVolumes(context: Context): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()

        try {
            val internalPath = Environment.getExternalStorageDirectory()?.absolutePath
            if (internalPath != null) {
                volumes.add(
                    StorageVolumeInfo(
                        name = "Внутренняя память",
                        path = internalPath,
                        isRemovable = false,
                        isAccessible = File(internalPath).canRead()
                    )
                )
            }

            if (isHuaweiDevice()) {
                val sdCardPaths = listOfNotNull(
                    "/storage/sdcard1",
                    "/storage/sdcard0",
                    "/storage/999F-16F3"
                )

                for (path in sdCardPaths.distinct()) {
                    if (path != internalPath) {
                        val file = File(path)
                        if (file.exists() && file.canRead() && file.isDirectory && isSdCardPath(path)) {
                            volumes.add(
                                StorageVolumeInfo(
                                    name = "SD-карта",
                                    path = path,
                                    isRemovable = true,
                                    isAccessible = true
                                )
                            )
                            break
                        }
                    }
                }
            } else {
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE)
                    as? android.os.storage.StorageManager
                if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        for (volume in storageManager.storageVolumes) {
                            if (volume.isRemovable) {
                                val path = volume.directory?.absolutePath
                                if (path != null) {
                                    volumes.add(
                                        StorageVolumeInfo(
                                            name = "SD-карта",
                                            path = path,
                                            isRemovable = true,
                                            isAccessible = File(path).canRead()
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error getting storage volumes: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage volumes: ${e.message}")
        }

        return volumes
    }

    data class StorageVolumeInfo(
        val name: String,
        val path: String,
        val isRemovable: Boolean,
        val isAccessible: Boolean
    )
}
