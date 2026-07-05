package com.dima.kidsvideoplayer.utils

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherHelperTest {

    private val ownPackage = "com.dima.kidsvideoplayer"

    @Test
    fun findExternalLauncherComponent_returnsFirstNonOwnLauncher() {
        val own = resolveInfo("com.dima.kidsvideoplayer", ".MainActivity")
        val honor = resolveInfo("com.hihonor.android.launcher", ".Launcher")
        val google = resolveInfo("com.google.android.apps.nexuslauncher", ".NexusLauncherActivity")

        val result = LauncherHelper.findExternalLauncherComponent(
            listOf(own, honor, google),
            ownPackage
        )

        assertThat(result).isEqualTo(
            ComponentName("com.hihonor.android.launcher", ".Launcher")
        )
    }

    @Test
    fun findExternalLauncherComponent_returnsNullWhenOnlyOwnLauncher() {
        val own = resolveInfo(ownPackage, ".MainActivity")

        val result = LauncherHelper.findExternalLauncherComponent(listOf(own), ownPackage)

        assertThat(result).isNull()
    }

    @Test
    fun isHomeOnlyIntent_detectsHomeWithoutLauncher() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        assertThat(LauncherHelper.isHomeOnlyIntent(homeIntent)).isTrue()
        assertThat(LauncherHelper.isHomeOnlyIntent(launcherIntent)).isFalse()
        assertThat(LauncherHelper.isHomeOnlyIntent(null)).isFalse()
    }

    @Test
    fun findExternalLauncherComponent_skipsOwnPackageEvenIfListedFirst() {
        val honor = resolveInfo("com.hihonor.android.launcher", ".Launcher")
        val own = resolveInfo(ownPackage, ".MainActivity")

        val result = LauncherHelper.findExternalLauncherComponent(
            listOf(own, honor),
            ownPackage
        )

        assertThat(result).isEqualTo(
            ComponentName("com.hihonor.android.launcher", ".Launcher")
        )
    }

    private fun resolveInfo(packageName: String, className: String): ResolveInfo {
        return ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = className
            }
        }
    }
}
