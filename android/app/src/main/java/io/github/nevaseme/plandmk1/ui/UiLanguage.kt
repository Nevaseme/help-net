package io.github.nevaseme.plandmk1.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

private val LocalUiResources = staticCompositionLocalOf<Resources?> { null }
val LocalUiLanguage = staticCompositionLocalOf { "ja" }
val LocalSetUiLanguage = staticCompositionLocalOf<(String) -> Unit> { {} }

/** Translate only named interface resources. User-entered text never passes through this function. */
@Composable fun uiText(key: String, vararg arguments: Any): String {
    val context = LocalContext.current
    val resources = LocalUiResources.current ?: context.resources
    val id = resources.getIdentifier("approved_$key", "string", context.packageName)
    check(id != 0) { "Missing UI resource: $key" }
    return resources.getString(id, *arguments)
}

@Composable fun PlanLocalization(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val preferences = remember(context) { context.getSharedPreferences("interface-language", Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(preferences.getString("language", "ja").takeIf { it == "en" } ?: "ja") }
    val resources = remember(context, configuration, language) {
        context.createConfigurationContext(Configuration(configuration).apply { setLocale(Locale.forLanguageTag(language)) }).resources
    }
    CompositionLocalProvider(LocalUiResources provides resources, LocalUiLanguage provides language,
        LocalSetUiLanguage provides { next ->
            require(next in listOf("ja", "en"))
            if (preferences.edit().putString("language", next).commit()) language = next
        }, content = content)
}
