package com.arena.aiuibuilder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AiUiBuilderApp() }
    }
}

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

private const val JSON_SYSTEM_PROMPT = """
You are an offline Android UI generator.
Return ONLY one valid JSON object. No markdown. No explanation. No code fences. No <think>.
Allowed schema:
{
  "title": "screen title",
  "elements": [
    {"type":"heading|text|input|button|checkbox|card|spacer", "id":"optional", "text":"optional", "hint":"optional", "checked":false, "children":[]}
  ]
}
Rules:
- Use only the allowed element types.
- Keep the JSON short.
- Make IDs simple lowercase words.
"""

@Composable
fun AiUiBuilderApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8FAFC)) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val engine = remember { AiChat.getInferenceEngine(context.applicationContext) }
            val engineState by engine.state.collectAsState()

            var prompt by remember { mutableStateOf("Make a simple login screen") }
            var generatedJson by remember { mutableStateOf(ExampleSchemas.login) }
            var rawModelOutput by remember { mutableStateOf("") }
            var schema by remember { mutableStateOf(parseUiSchema(generatedJson)) }
            var status by remember { mutableStateOf("Pick a GGUF model from local storage, then load it.") }
            var selectedModelUri by remember { mutableStateOf<Uri?>(null) }
            var selectedModelName by remember { mutableStateOf<String?>(null) }
            var copiedModelPath by remember { mutableStateOf<String?>(null) }
            var isBusy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }

            val modelPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
                onResult = { uri ->
                    if (uri != null) {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Throwable) {
                            // Some file providers do not support persistable permissions. The copy step can still work.
                        }
                        selectedModelUri = uri
                        selectedModelName = context.displayName(uri) ?: "selected-model.gguf"
                        status = "Selected: ${selectedModelName}. Tap Load local model."
                        error = null
                    }
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "AI UI Builder GGUF",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Runs a local GGUF model through llama.cpp, asks it for JSON, then renders the JSON as native Compose UI.",
                    color = Color(0xFF475569)
                )

                StatusCard(
                    status = status,
                    engineState = engineState,
                    modelName = selectedModelName,
                    modelPath = copiedModelPath,
                    isBusy = isBusy,
                    error = error
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(enabled = !isBusy, onClick = {
                                modelPicker.launch(arrayOf("*/*"))
                            }) { Text("Pick GGUF") }

                            Button(
                                enabled = !isBusy && selectedModelUri != null,
                                onClick = {
                                    val uri = selectedModelUri ?: return@Button
                                    scope.launch {
                                        isBusy = true
                                        error = null
                                        try {
                                            status = "Copying GGUF into app storage. This can take time for large models..."
                                            val modelFile = copyModelIntoAppStorage(context, uri)
                                            copiedModelPath = modelFile.absolutePath

                                            status = "Waiting for llama.cpp runtime..."
                                            waitUntilEngineInitialized(engine)

                                            status = "Loading model. This can take 30 seconds to several minutes..."
                                            engine.loadModel(modelFile.absolutePath)

                                            status = "Setting JSON system prompt..."
                                            engine.setSystemPrompt(JSON_SYSTEM_PROMPT.trimIndent())

                                            status = "Model ready. Type a UI request and tap Generate JSON UI."
                                        } catch (t: Throwable) {
                                            error = t.message ?: t.toString()
                                            status = "Failed to load model."
                                        } finally {
                                            isBusy = false
                                        }
                                    }
                                }
                            ) { Text("Load local model") }
                        }

                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Describe the UI you want") },
                            minLines = 2
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                enabled = !isBusy && engineState is InferenceEngine.State.ModelReady,
                                onClick = {
                                    scope.launch {
                                        isBusy = true
                                        error = null
                                        rawModelOutput = ""
                                        status = "Generating JSON from local model..."
                                        try {
                                            val uiPrompt = buildUiJsonPrompt(prompt)
                                            engine.sendUserPrompt(uiPrompt, predictLength = 900).collect { token ->
                                                rawModelOutput += token
                                                status = "Generating... ${rawModelOutput.length} chars"
                                            }
                                            val cleanJson = extractFirstJsonObject(rawModelOutput)
                                            generatedJson = cleanJson
                                            schema = parseUiSchema(cleanJson)
                                            status = "Rendered JSON UI from local model."
                                        } catch (t: Throwable) {
                                            error = t.message ?: t.toString()
                                            status = "Generation or JSON parse failed. See raw model output below."
                                        } finally {
                                            isBusy = false
                                        }
                                    }
                                }
                            ) { Text("Generate JSON UI") }

                            TextButton(enabled = !isBusy, onClick = {
                                generatedJson = ExampleSchemas.todo
                                schema = parseUiSchema(generatedJson)
                                rawModelOutput = ""
                                error = null
                                status = "Loaded built-in todo example."
                            }) { Text("Test renderer") }
                        }
                    }
                }

                Text(
                    text = "Rendered app screen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                DynamicScreen(schema = schema)

                Text(
                    text = "Parsed JSON",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                CodeBox(generatedJson)

                if (rawModelOutput.isNotBlank()) {
                    Text(
                        text = "Raw model output",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    CodeBox(rawModelOutput)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: String,
    engineState: InferenceEngine.State,
    modelName: String?,
    modelPath: String?,
    isBusy: Boolean,
    error: String?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isBusy) CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                Text(text = status, color = Color(0xFF334155))
            }
            Text(text = "Engine: ${engineState.javaClass.simpleName}", color = Color(0xFF64748B))
            modelName?.let { Text(text = "Model: $it", color = Color(0xFF64748B)) }
            modelPath?.let { Text(text = "Internal path: $it", color = Color(0xFF64748B)) }
            error?.let { Text(text = "Error: $it", color = Color(0xFFB91C1C)) }
        }
    }
}

@Composable
private fun CodeBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = text, color = Color(0xFFE2E8F0))
    }
}

private suspend fun waitUntilEngineInitialized(engine: InferenceEngine) {
    repeat(200) {
        when (val state = engine.state.value) {
            is InferenceEngine.State.Initialized,
            is InferenceEngine.State.ModelReady -> return
            is InferenceEngine.State.Error -> throw state.exception
            else -> delay(100)
        }
    }
    error("Timed out waiting for llama.cpp runtime initialization")
}

private suspend fun copyModelIntoAppStorage(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val dir = File(context.filesDir, "models").apply { mkdirs() }
    val name = (context.displayName(uri) ?: "local-model.gguf").sanitizeFileName()
    val target = File(dir, name)
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "Could not open selected model file" }
        target.outputStream().use { output -> input.copyTo(output) }
    }
    target
}

private fun Context.displayName(uri: Uri): String? {
    return runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()
}

private fun String.sanitizeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun buildUiJsonPrompt(userRequest: String): String = """
Create a UI for this request: $userRequest
Return ONLY valid JSON matching the schema. Do not include markdown. Do not explain.
Allowed element types: heading, text, input, button, checkbox, card, spacer.
""".trimIndent()

private fun extractFirstJsonObject(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    require(start >= 0 && end > start) { "No JSON object found in model output" }
    return text.substring(start, end + 1).trim()
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
        "text" -> Text(text = element.text.orEmpty(), color = Color(0xFF334155))
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
        "button" -> Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
            Text(element.text ?: "Button")
        }
        "checkbox" -> {
            val checked = checkboxState[id] ?: element.checked
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = checked, onCheckedChange = { checkboxState[id] = it })
                Text(element.text ?: "Checkbox")
            }
        }
        "card" -> Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                element.text?.let { Text(text = it, fontWeight = FontWeight.SemiBold) }
                element.children.forEachIndexed { index, child ->
                    RenderElement(child, "$id-$index", textState, checkboxState)
                }
            }
        }
        "spacer" -> Spacer(modifier = Modifier.height(10.dp))
        else -> Text(text = "Unsupported element type: ${element.type}", color = Color(0xFFB91C1C))
    }
}

object ExampleSchemas {
    val login = """
        {
          "title": "Simple Login",
          "elements": [
            { "type": "text", "text": "Sign in to continue." },
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
            { "type": "input", "id": "newtask", "hint": "Add a new task" },
            { "type": "button", "text": "Add Task" },
            { "type": "card", "text": "Task list", "children": [
              { "type": "checkbox", "id": "task1", "text": "Study Kotlin", "checked": false },
              { "type": "checkbox", "id": "task2", "text": "Test local model", "checked": true },
              { "type": "checkbox", "id": "task3", "text": "Build APK with GitHub Actions", "checked": false }
            ]}
          ]
        }
    """.trimIndent()
}
