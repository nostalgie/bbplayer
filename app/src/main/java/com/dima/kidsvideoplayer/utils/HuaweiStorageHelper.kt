package com.dima.kidsvideoplayer.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import java.io.File
import java.net.URI

/**
 * Helper class for handling Huawei-specific storage access restrictions.
 * Huawei devices often require additional permissions and settings to access SD cards.
 */
object HuaweiStorageHelper {
    
    private const val TAG = "HuaweiStorageHelper"
    
    /**
     * Check if the device is a Huawei device.
     */
    fun isHuaweiDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return manufacturer.contains("huawei")
    }
    
    /**
     * Check if SD card access is available on Huawei devices.
     */
    fun isSdCardAvailable(context: Context): Boolean {
        if (!isHuaweiDevice()) {
            return true // Not Huawei, assume SD card is available
        }
        
        try {
            // Method 1: Check if SD card is mounted
            val sdCardPath = getExternalStorageDirectory()
            if (sdCardPath != null && File(sdCardPath).exists()) {
                Log.d(TAG, "SD card path found: $sdCardPath")
                return true
            }
            
            // Method 2: Check Huawei-specific settings
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
            if (storageManager != null) {
                try {
                    val volumes = storageManager.storageVolumes
                    for (volume in volumes) {
                        if (volume.isRemovable) {
                            Log.d(TAG, "Found removable storage volume")
                            return true
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking storage volumes: ${e.message}")
                }
            }
            
            // Method 3: Check if app has all files access permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val hasAllFilesAccess = Environment.isExternalStorageManager()
                Log.d(TAG, "Has all files access: $hasAllFilesAccess")
                
                if (!hasAllFilesAccess) {
                    // Try to check if SD card can be accessed through other means
                    val sdCardPath2 = getSecondaryStoragePath()
                    if (sdCardPath2 != null && File(sdCardPath2).exists()) {
                        Log.d(TAG, "SD card accessible via secondary path: $sdCardPath2")
                        return true
                    }
                }
            }
            
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking SD card availability: ${e.message}")
            return false
        }
    }
    
    /**
     * Get the path to external storage directory (SD card).
     */
    fun getExternalStorageDirectory(): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val context = android.app.Application().applicationContext
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
                storageManager?.getPrimaryStorageVolume()?.let { volume ->
                    volume.directory?.absolutePath
                } ?: Environment.getExternalStorageDirectory()?.absolutePath
            } else {
                Environment.getExternalStorageDirectory()?.absolutePath
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting external storage directory: ${e.message}")
            null
        }
    }
    
    /**
     * Get secondary storage path (alternative method for SD card).
     */
    fun getSecondaryStoragePath(): String? {
        return try {
            val paths = System.getenv("SECONDARY_STORAGE")
            if (!TextUtils.isEmpty(paths)) {
                paths?.split(":")?.firstOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting secondary storage path: ${e.message}")
            null
        }
    }
    
    /**
     * Check if the app needs additional Huawei-specific permissions.
     */
    fun needsHuaweiPermissions(context: Context): Boolean {
        if (!isHuaweiDevice()) {
            return false
        }
        
        // Check if we have all files access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                return true
            }
        }
        
        // Check for Huawei-specific permissions
        try {
            val huaweiPermissionSettings = Settings.System.getString(
                context.contentResolver,
                "huawei_permission_settings"
            )
            
            if (huaweiPermissionSettings != null) {
                Log.d(TAG, "Huawei permission settings found: $huaweiPermissionSettings")
                // Additional Huawei-specific permission checks can be added here
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Huawei permissions: ${e.message}")
        }
        
        return false
    }
    
    /**
     * Get available storage volumes with Huawei-specific handling.
     */
    fun getAvailableStorageVolumes(context: Context): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()
        
        try {
            // Internal storage
            val internalPath = Environment.getExternalStorageDirectory()?.absolutePath
            if (internalPath != null) {
                volumes.add(StorageVolumeInfo(
                    name = "Внутренняя память",
                    path = internalPath,
                    isRemovable = false,
                    isAccessible = File(internalPath).canRead()
                ))
            }
            
            // SD card (with Huawei-specific handling)
            if (isHuaweiDevice()) {
                // Try multiple methods to find SD card on Huawei devices
                val sdCardPaths = listOfNotNull(
                    getExternalStorageDirectory(),
                    getSecondaryStoragePath(),
                    "/storage/sdcard1",
                    "/storage/sdcard0",
                    "/storage/999F-16F3" // Common SD card pattern
                )
                
                for (path in sdCardPaths.distinct()) {
                    if (path != internalPath) {
                        val file = File(path)
                        if (file.exists() && file.canRead() && file.isDirectory) {
                            // Check if this is actually an SD card
                            val isRemovable = isSdCardPath(path)
                            volumes.add(StorageVolumeInfo(
                                name = "SD-карта",
                                path = path,
                                isRemovable = isRemovable,
                                isAccessible = true
                            ))
                            break // Found the SD card, stop searching
                        }
                    }
                }
            } else {
                // Standard Android SD card detection
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? android.os.storage.StorageManager
                if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val storageVolumes = storageManager.storageVolumes
                        for (volume in storageVolumes) {
                            if (volume.isRemovable) {
                                val path = volume.directory?.absolutePath
                                if (path != null) {
                                    volumes.add(StorageVolumeInfo(
                                        name = "SD-карта",
                                        path = path,
                                        isRemovable = true,
                                        isAccessible = File(path).canRead()
                                    ))
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
    
    /**
     * Check if a path corresponds to an SD card.
     */
    private fun isSdCardPath(path: String): Boolean {
        return path.contains("sdcard", ignoreCase = true) ||
               path.matches(Regex("/storage/[A-F0-9]{4}-[A-F0-9]{4}")) ||
               path == "/storage/extSdCard" ||
               path == "/storage/sdcard1"
    }
    
    /**
     * Information about a storage volume.
     */
    data class StorageVolumeInfo(
        val name: String,
        val path: String,
        val isRemovable: Boolean,
        val isAccessible: Boolean
    )
}