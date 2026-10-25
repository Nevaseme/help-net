package io.github.nevaseme.plandmk1.location

import android.content.Context

class PositionPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("position-recording", Context.MODE_PRIVATE)
    val group: String? get() = prefs.getString("group", null)
    fun enable(group: String) {
        if (this.group == group) return
        check(prefs.edit().putString("group", group).commit()) { "位置記録の設定を保存できません" }
    }
    fun wasRequested(permission: String) = prefs.getBoolean("requested:$permission", false)
    fun requested(permission: String) { check(prefs.edit().putBoolean("requested:$permission", true).commit()) }
}
