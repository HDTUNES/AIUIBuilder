package com.arena.aiuibuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AiUiBuilderApp()
        }
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

@Composable
fun AiUiBuilderApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8FAFC)) {
            var prompt by remember { mutableStateOf("Make a simple login screen") }
            var generatedJson by remember { mutableStateOf(FakeLocalModel.generate(prompt)) }
            var schema by remember { mutableStateOf(parseUiSchema(generatedJson)) }
            var error by remember { mutableStateOf<String?>(null) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "AI UI Builder",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Offline-local AI prototype. This version uses a fake local model, but the renderer is ready for JSON returned by a real model.",
                    color = Color(0xFF475569)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Describe the UI you want") },
                            minLines = 2
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                generatedJson = FakeLocalModel.generate(prompt)
                                try {
                                    schema = parseUiSchema(generatedJson)
                                    error = null
                                } catch (t: Throwable) {
                                    error = t.message
                                }
                            }) {
                                Text("Generate UI")
                            }
                            TextButton(onClick = {
                                generatedJson = ExampleSchemas.todo
                                schema = parseUiSchema(generatedJson)
                                error = null
                            }) {
                                Text("Try todo")
                            }
                        }
                    }
                }

                error?.let {
                    Text(text = "JSON error: $it", color = Color(0xFFB91C1C))
                }

                Text(
                    text = "Rendered app screen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                DynamicScreen(schema = schema)

                Text(
                    text = "Model JSON",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(text = generatedJson, color = Color(0xFFE2E8F0))
                }
            }
        }
    }
}

private fun parseUiSchema(raw: String): UiSchema = json.decodeFromString<UiSchema>(raw)

@Serializable
data class UiSchema(
    val title: String = "Untitled",
    val elements: List<UiElement> = emptyList()
)

@Serializable
data class UiElement(
    val type: String,
    val id: String? = null,
    val text: String? = null,
    val hint: String? = null,
    val checked: Boolean = false,
    val children: List<UiElement> = emptyList()
)

@Composable
fun DynamicScreen(schema: UiSchema) {
    val textState = remember { mutableStateMapOf<String, String>() }
    val checkboxState = remember { mutableStateMapOf<String, Boolean>() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = schema.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            schema.elements.forEachIndexed { index, element ->
                RenderElement(
                    element = element,
                    fallbackId = "el_$index",
                    textState = textState,
                    checkboxState = checkboxState
                )
            }
        }
    }
}

@Composable
fun RenderElement(
    element: UiElement,
    fallbackId: String,
    textState: MutableMap<String, String>,
    checkboxState: MutableMap<String, Boolean>
) {
    val id = element.id ?: fallbackId
    when (element.type.lowercase()) {
        "text" -> Text(
            text = element.text.orEmpty(),
            color = Color(0xFF334155)
        )

        "heading" -> Text(
            text = element.text.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        "input" -> {
            val value = textState[id].orEmpty()
            OutlinedTextField(
                value = value,
                onValueChange = { textState[id] = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(element.hint ?: element.text ?: "Input") }
            )
        }

        "button" -> Button(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(element.text ?: "Button")
        }

        "checkbox" -> {
            val checked = checkboxState[id] ?: element.checked
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checkboxState[id] = it }
                )
                Text(element.text ?: "Checkbox")
            }
        }

        "card" -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                element.text?.let {
                    Text(text = it, fontWeight = FontWeight.SemiBold)
                }
                element.children.forEachIndexed { index, child ->
                    RenderElement(child, "$id-$index", textState, checkboxState)
                }
            }
        }

        "spacer" -> Spacer(modifier = Modifier.height(10.dp))

        else -> Text(
            text = "Unsupported element type: ${element.type}",
            color = Color(0xFFB91C1C)
        )
    }
}

object FakeLocalModel {
    fun generate(prompt: String): String {
        val p = prompt.lowercase()
        return when {
            "todo" in p || "task" in p -> ExampleSchemas.todo
            "calculator" in p || "calc" in p -> ExampleSchemas.calculator
            "profile" in p -> ExampleSchemas.profile
            else -> ExampleSchemas.login
        }
    }
}

object ExampleSchemas {
    val login = """
        {
          "title": "Simple Login",
          "elements": [
            { "type": "text", "text": "Sign in to continue. This UI was generated from a safe JSON schema." },
            { "type": "input", "id": "email", "hint": "Email address" },
            { "type": "input", "id": "password", "hint": "Password" },
            { "type": "checkbox", "id": "remember", "text": "Remember me", "checked": true },
            { "type": "button", "text": "Login" }
          ]
        }
    """.trimIndent()

    val todo = """
        {
          "title": "Today Tasks",
          "elements": [
            { "type": "input", "id": "newTask", "hint": "Add a new task" },
            { "type": "button", "text": "Add Task" },
            { "type": "card", "text": "Task list", "children": [
              { "type": "checkbox", "id": "task1", "text": "Study Kotlin", "checked": false },
              { "type": "checkbox", "id": "task2", "text": "Test local model", "checked": true },
              { "type": "checkbox", "id": "task3", "text": "Build APK with GitHub Actions", "checked": false }
            ]}
          ]
        }
    """.trimIndent()

    val calculator = """
        {
          "title": "Simple Calculator",
          "elements": [
            { "type": "input", "id": "first", "hint": "First number" },
            { "type": "input", "id": "second", "hint": "Second number" },
            { "type": "button", "text": "Calculate" },
            { "type": "text", "text": "This prototype renders the button but does not attach generated business logic yet." }
          ]
        }
    """.trimIndent()

    val profile = """
        {
          "title": "Profile Card",
          "elements": [
            { "type": "card", "text": "User", "children": [
              { "type": "heading", "text": "Alex Developer" },
              { "type": "text", "text": "Building offline AI apps on Android." },
              { "type": "button", "text": "Edit profile" }
            ]}
          ]
        }
    """.trimIndent()
}
