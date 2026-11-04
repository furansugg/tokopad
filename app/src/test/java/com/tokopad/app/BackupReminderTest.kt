package com.tokopad.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {
    private val week = 7L * 24 * 60 * 60 * 1000

    @Test fun neverBackedUpIsDue() {
        assertTrue(backupReminderDue(null, 0))
    }

    @Test fun reminderIsDueAfterSevenDays() {
        assertFalse(backupReminderDue(1000, 1000 + week - 1))
        assertTrue(backupReminderDue(1000, 1000 + week))
        assertTrue(backupReminderDue(1000, 1000 + week + 1))
    }

    @Test fun futureBackupTriggersReminder() {
        assertTrue(backupReminderDue(1000, 999))
    }
}
