package com.tan.gratify.expect

expect fun openNotificationSettings(): Boolean

/** Null on platforms without Android notification controls. */
expect fun notificationsEnabled(): Boolean?
