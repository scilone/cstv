// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.51.1" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

// Installation automatique du Git Hook pre-commit au chargement du projet (léger et transparent)
val gitHooksDir = file("${rootDir}/.git/hooks")
if (gitHooksDir.exists()) {
    val preCommitHook = file("${gitHooksDir.path}/pre-commit")
    preCommitHook.writeText(
        """
        #!/bin/sh
        echo "=== 🛡️ [Git Hook] Vérification pré-commit : Exécution des tests unitaires ==="
        ./gradlew testDebugUnitTest --no-daemon
        RESULT=${"$"}?
        if [ ${"$"}RESULT -ne 0 ]; then
            echo "❌ [Git Hook] Échec des tests unitaires ! Commit annulé."
            exit ${"$"}RESULT
        fi
        echo "✅ [Git Hook] Tous les tests sont passés. Validation du commit en cours..."
        exit 0
        """.trimIndent().trim() + "\n"
    )
    preCommitHook.setExecutable(true)
}
