package com.tokopad.app

internal fun backupReminderDue(lastBackupMillis: Long?, nowMillis: Long): Boolean =
    lastBackupMillis == null || nowMillis < lastBackupMillis || nowMillis - lastBackupMillis >= 7L * 24 * 60 * 60 * 1000
