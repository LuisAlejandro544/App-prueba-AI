package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.SubagentInfo
import com.example.model.ToolExecution
import com.example.model.ToolIconType
import com.example.model.ToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Folder AI", appName)
  }

  @Test
  fun `verify subagent data model integrity`() {
    val subagent = SubagentInfo(
      role = "El Detective - Debugger",
      goal = "Identificar causa raíz de fallos",
      task = "Analizar trazas de error y sugerir parche",
      relevantFiles = "scripts/main.lua"
    )
    val toolExec = ToolExecution(
      callId = "call-1",
      toolName = "spawn_subagent",
      displayName = "Subagente: El Detective",
      argumentsSummary = "Identificar causa raíz",
      iconType = ToolIconType.SUBAGENT,
      status = ToolStatus.SUCCESS,
      resultOutput = "Análisis completado",
      subagentInfo = subagent
    )

    assertEquals(ToolIconType.SUBAGENT, toolExec.iconType)
    assertEquals(ToolStatus.SUCCESS, toolExec.status)
    assertNotNull(toolExec.subagentInfo)
    assertEquals("El Detective - Debugger", toolExec.subagentInfo?.role)
  }
}
