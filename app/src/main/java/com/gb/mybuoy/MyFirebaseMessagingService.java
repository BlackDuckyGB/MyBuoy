/*
 * MyBuoy — outdoor navigation & mark-tracking for boating, hiking, foraging and discovery walks.
 * Copyright (C) 2026 Golden-Block
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * For commercial licensing (use without AGPL obligations), contact:
 * licence@golden-block.fr
 */
package com.gb.mybuoy;
import android.util.Log;

/*
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;


import java.util.UUID;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    //**
    // * There are two scenarios when onNewToken is called:
    // * 1) When a new token is generated on initial app startup
    // * 2) Whenever an existing token is changed
    // * Under #2, there are three scenarios when the existing token is changed:
    // * A) App is restored to a new device
    // * B) User uninstalls/reinstalls the app
    // * C) User clears app data
    // *
    @Override
    public void onNewToken(String token) {
        GBLog.d("MyFirebaseMessSvc", "Refreshed token: " + token);

        String uniqueID = UUID.randomUUID().toString();

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // FCM registration token to your app server.
        //sendRegistrationToServer(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // ...

        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        GBLog.d("MyFirebaseMessSvc", "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            GBLog.d("MyFirebaseMessSvc", "Message data payload: " + remoteMessage.getData());

            if ( true) { // Check if data needs to be processed by long running job
                // For long-running tasks (10 seconds or more) use WorkManager.
                //scheduleJob();
            } else {
                // Handle message within 10 seconds
               // handleNow();
            }

        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            GBLog.d("MyFirebaseMessSvc", "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }
}
*/
