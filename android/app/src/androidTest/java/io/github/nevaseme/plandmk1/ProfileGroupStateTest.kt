package io.github.nevaseme.plandmk1

import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.nevaseme.plandmk1.core.Resident
import io.github.nevaseme.plandmk1.ui.PlanApp
import io.github.nevaseme.plandmk1.ui.theme.PlanDMk1Theme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Uses the production UI and Room, but only isolated emulator groups and no cloud requests. */
class ProfileGroupStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingGroupWhileProfileIsHiddenCannotRestoreAnotherGroupsDraft() = runBlocking {
        assertTrue(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
        val app = ApplicationProvider.getApplicationContext<PlanDApplication>()
        val previous = app.installation.group.value
        val suffix = UUID.randomUUID().toString().take(8)
        val first = "profile-a-$suffix"
        val second = "profile-b-$suffix"
        val id = app.installation.localId
        app.store.updateOwn(first, id) { Resident(first, id, 1, 0, "First member", 35.0, 135.0, "1", "First room") }
        app.store.updateOwn(second, id) { Resident(second, id, 1, 0, "Second member", 36.0, 136.0, "2", "Second room") }
        val original = app.store.get(first, id)
        val models = ViewModelStore()
        lateinit var model: PlanViewModel
        val prefs = app.getSharedPreferences("interface-language", 0)
        val language = prefs.getString("language", null)
        prefs.edit().putString("language", "ja").commit()
        fun profile() {
            if(compose.onAllNodesWithContentDescription("戻る").fetchSemanticsNodes().isNotEmpty())compose.onNodeWithContentDescription("戻る").performClick()
            compose.onNodeWithContentDescription("メニューを開く").performClick()
            compose.onNodeWithText("自分の登録情報", substring = false).performScrollTo().performClick()
        }
        fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))
        try {
            compose.runOnUiThread {
                org.maplibre.android.MapLibre.getInstance(app)
                app.installation.setGroup(first)
                model = PlanViewModel(app)
                models.put("profile", model)
            }
            compose.waitUntil(15_000) { model.residents.value.any { it.groupId == first && it.originId == id } }
            compose.setContent { PlanDMk1Theme { PlanApp(model) } }
            profile()
            field("自分の名前").performTextReplacement("Unsent first draft")
            compose.onNodeWithContentDescription("戻る").performClick()
            // Ordinary navigation within the same group must preserve the draft.
            profile()
            field("自分の名前").assert(hasText("Unsent first draft"))
            compose.onNodeWithContentDescription("戻る").performClick()
            // Cloud registration also switches this installation selection while profile is hidden.
            compose.runOnUiThread { app.installation.setGroup(second) }
            compose.waitUntil(15_000) { model.group.value == second && model.residents.value.any { it.groupId == second && it.originId == id } }
            profile()
            compose.onNodeWithText("実験グループ").assertDoesNotExist()
            field("自分の名前").assert(hasText("Second member"))
            field("寝る部屋・場所の説明").performScrollTo().assert(hasText("Second room"))
            field("寝る部屋・場所の説明").performTextReplacement("Updated second room")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText("登録情報を保存").performScrollTo().performClick()
            compose.waitUntil(15_000) { runBlocking { app.store.get(second, id)?.room == "Updated second room" } }
            assertEquals(second, app.installation.group.value)
            assertEquals(original, app.store.get(first, id))
        } finally {
            compose.runOnUiThread { models.clear(); app.installation.setGroup(previous) }
            if (language == null) prefs.edit().remove("language").commit()
            else prefs.edit().putString("language", language).commit()
        }
    }
}
