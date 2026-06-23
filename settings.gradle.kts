pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "AIUIBuilder"
include(":app")

// GitHub Actions clones llama.cpp here before Gradle runs.
// This official Android library module provides the JNI + llama.cpp native runtime.
include(":llamaLib")
project(":llamaLib").projectDir = file("external/llama.cpp/examples/llama.android/lib")
