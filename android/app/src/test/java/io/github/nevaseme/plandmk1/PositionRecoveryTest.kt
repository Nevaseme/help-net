package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.location.PositionRecoveryPolicy
import io.github.nevaseme.plandmk1.location.PermissionRetry
import org.junit.Assert.*
import org.junit.Test

class PositionRecoveryTest {
    @Test fun bootRecoveryRequiresSavedIntentAndLocationPermission() {
        assertFalse(PositionRecoveryPolicy.canResume(false, false, true, true, 36))
        assertFalse(PositionRecoveryPolicy.canResume(true, false, false, true, 36))
        assertTrue(PositionRecoveryPolicy.canResume(true, false, true, true, 36))
    }
    @Test fun whileInUsePermissionDoesNotAuthorizeBootLocationButAllowsVisibleResume() {
        assertFalse(PositionRecoveryPolicy.canResume(true, false, true, false, 36))
        assertTrue(PositionRecoveryPolicy.canResume(true, true, true, false, 36))
    }
    @Test fun oldAndroidDoesNotRequireNonexistentBackgroundPermission() {
        assertTrue(PositionRecoveryPolicy.canResume(true, false, true, false, 28))
        assertFalse(PositionRecoveryPolicy.canResume(true, false, true, false, 29))
    }
    @Test fun retriesDenialThenUsesSettingsWithoutAnEndlessPromptLoop() {
        val retry = PermissionRetry()
        assertEquals(PermissionRetry.Action.REQUEST, retry.next("location", requestedBefore = false, canAskAgain = false))
        assertEquals(PermissionRetry.Action.REQUEST, retry.next("location", requestedBefore = true, canAskAgain = true))
        assertEquals(PermissionRetry.Action.SETTINGS, retry.next("location", requestedBefore = true, canAskAgain = false))
        assertEquals(PermissionRetry.Action.WAIT, retry.next("location", requestedBefore = true, canAskAgain = false))
    }
    @Test fun permanentlyDeniedPermissionGoesToSettingsAndNextVisitCanRetry() {
        val retry = PermissionRetry()
        assertEquals(PermissionRetry.Action.SETTINGS, retry.next("notifications", true, false))
        assertEquals(PermissionRetry.Action.WAIT, retry.next("notifications", true, false))
        retry.newVisit()
        assertEquals(PermissionRetry.Action.SETTINGS, retry.next("notifications", true, false))
        assertEquals(PermissionRetry.Action.REQUEST, retry.next("location", false, false))
    }
}
