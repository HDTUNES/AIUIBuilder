# AI UI Builder Android Prototype

A Kotlin + Jetpack Compose Android prototype that turns a prompt into a simple dynamic UI using a safe JSON schema.

This first version uses a **fake local model** so the APK can build quickly. The code is structured so a real offline LLM runtime like `llama.cpp` + a quantized GGUF model can be added later.

## What it does now

- Lets you type a prompt.
- A local placeholder model returns JSON UI instructions.
- The app parses the JSON safely.
- Jetpack Compose renders native Android UI elements.

Supported elements:

- Text
- Input fields
- Buttons
- Cards
- Checkboxes
- Spacers

## How to build APK from only your phone using GitHub

1. Create a new GitHub repository.
2. Upload all files from this `AIUIBuilder` folder to the repository root.
3. Open the repository on GitHub.
4. Go to **Actions**.
5. Choose **Build Android APK**.
6. Tap **Run workflow**.
7. Wait until the build finishes.
8. Open the finished workflow run.
9. Download artifact named **AIUIBuilder-debug-apk**.
10. Extract the ZIP and install `app-debug.apk` on your phone.

You may need to enable **Install unknown apps** for your browser/files app.

## Real offline DeepSeek plan

The correct safe architecture is:

```text
Prompt
  -> Local LLM runtime
  -> JSON UI schema
  -> Kotlin parser
  -> Compose renderer
```

Do **not** let the model execute code directly. Let it output only JSON from a limited schema.

Recommended later integration:

- Convert/download `DeepSeek-R1-Distill-Qwen-1.5B` as a 4-bit GGUF model.
- Add `llama.cpp` Android native library.
- Replace `FakeLocalModel` with a `LlamaCppLocalModel` implementation.
- Keep the same JSON schema.

The model should usually be downloaded separately or placed by the user in storage because it can be around 700 MB to 1.5 GB.
