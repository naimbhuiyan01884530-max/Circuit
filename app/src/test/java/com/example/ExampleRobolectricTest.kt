package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CircuitJsonMapper
import com.example.data.PresetCircuits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VoltLab", appName)
  }

  @Test
  fun `json round trip preserves preset schematics`() {
    for (preset in PresetCircuits.allPresets()) {
      val json = CircuitJsonMapper.toJson(preset)
      val decoded = CircuitJsonMapper.fromJson(json)
      assertTrue("Failed to decode ${preset.name}", decoded.isSuccess)
      assertEquals(preset.name, decoded.getOrThrow().name)
      assertEquals(preset.components.size, decoded.getOrThrow().components.size)
      assertEquals(preset.wires.size, decoded.getOrThrow().wires.size)
    }
  }
}
