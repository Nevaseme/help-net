package io.github.nevaseme.plandmk1.location

object PositionRecoveryPolicy {
    fun canResume(enabled: Boolean, visible: Boolean, locationAllowed: Boolean, backgroundAllowed: Boolean, sdk: Int) =
        enabled && locationAllowed && (visible || sdk < 29 || backgroundAllowed)
}

/** Retry once in a visit, then offer settings; returning without granting must not trap the user. */
class PermissionRetry {
    enum class Action { REQUEST, SETTINGS, WAIT }
    private val requests = mutableMapOf<String, Int>()
    private val settingsOpened = mutableSetOf<String>()
    fun newVisit() { requests.clear(); settingsOpened.clear() }
    fun next(key: String, requestedBefore: Boolean, canAskAgain: Boolean): Action {
        if (key in settingsOpened) return Action.WAIT
        val attempts = requests[key] ?: 0
        if (attempts < 2 && (!requestedBefore || canAskAgain)) {
            requests[key] = attempts + 1
            return Action.REQUEST
        }
        settingsOpened.add(key)
        return Action.SETTINGS
    }
}
