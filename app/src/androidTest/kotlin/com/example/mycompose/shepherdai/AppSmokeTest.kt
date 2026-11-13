package com.example.mycompose.shepherdai

import android.Manifest
import android.graphics.Bitmap
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/** Launches the real app on the emulator and saves a screenshot of what a user would see. */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Test
    fun appLaunchesAndShowsHome() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val out = File(inst.targetContext.getExternalFilesDir(null), "report").apply { mkdirs() }
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            Thread.sleep(4000)
            val shot = inst.uiAutomation.takeScreenshot()
            if (shot != null) FileOutputStream(File(out, "app_home.png")).use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertEquals(Lifecycle.State.RESUMED, sc.state)
        }
    }
}
