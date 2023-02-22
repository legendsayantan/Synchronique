package com.legendsayantan.sync.models

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * @author legendsayantan
 */
class NotificationData(
    var app_id: String?,
    var title: String?,
    var text: String?,
    var subtext: String?,
    var time: Long,
    var key: String?,
    var canReply: Boolean
) {
    constructor(sbn: StatusBarNotification, allowReply: Boolean) : this(
        sbn.packageName,
        sbn.notification.extras.getString(Notification.EXTRA_TITLE),
        sbn.notification.extras.getString(Notification.EXTRA_TEXT).toString(),
        sbn.notification.extras.getString(Notification.EXTRA_SUB_TEXT),
        sbn.postTime,
        if(allowReply)sbn.key else null,
        ((allowReply && (sbn.notification.actions != null) && (sbn.notification.actions.find {
            it.title.toString().lowercase().trim() == "reply"
        } != null)))
    ) {

    }
}