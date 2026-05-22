package com.example

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("BLE Debugger", appName)
  }

  @Test
  fun `instantiate BleViewModel`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = BleViewModel(app)
    assertNotNull(viewModel)
  }

  @Test
  fun `compose BleDebuggerScreen successfully`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = BleViewModel(app)
    
    composeTestRule.setContent {
      MyApplicationTheme {
        BleDebuggerScreen(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
    
    // If we reach here, composition succeeded without throwing any runtime crashes!
    composeTestRule.waitForIdle()
  }
}


