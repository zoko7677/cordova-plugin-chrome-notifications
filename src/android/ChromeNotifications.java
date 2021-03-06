// Copyright (c) 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium;

import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Log;

import java.io.InputStream;
import java.util.Iterator;
import android.media.RingtoneManager;
import android.app.TaskStackBuilder;

public class ChromeNotifications extends CordovaPlugin {
    private static final String LOG_TAG = "ChromeNotifications";
    private static final String INTENT_PREFIX = "ChromeNotifications.";
    private static final String NOTIFICATION_CLICKED_ACTION = INTENT_PREFIX + "Click";
    private static final String NOTIFICATION_CLOSED_ACTION = INTENT_PREFIX + "Close";
    private static final String NOTIFICATION_BUTTON_CLICKED_ACTION = INTENT_PREFIX + "ButtonClick";
    private static final String DATA_NOTIFICATION_ID = "NotificationId";
    private static final String DATA_BUTTON_INDEX = "ButtonIndex";

    private NotificationManager notificationManager;
    private static BackgroundEventHandler<ChromeNotifications> eventHandler;

    public static BackgroundEventHandler<ChromeNotifications> getEventHandler() {
        if (eventHandler == null) {
            eventHandler = createEventHandler();
        }
        return eventHandler;
    }

    private static BackgroundEventHandler<ChromeNotifications> createEventHandler() {

        return new BackgroundEventHandler<ChromeNotifications>() {

            @Override
            public BackgroundEventInfo mapBroadcast(Context context, Intent intent) {
                String[] strings = intent.getAction().split("\\|", 3);
                int buttonIndex = strings.length >= 3 ? Integer.parseInt(strings[2]) : -1;

                BackgroundEventInfo event = new BackgroundEventInfo(strings[0]);
                event.getData().putString(DATA_NOTIFICATION_ID, strings[1]);
                event.getData().putInt(DATA_BUTTON_INDEX, buttonIndex);

                return event;
            }

            @Override
            public void mapEventToMessage(BackgroundEventInfo event, JSONObject message) throws JSONException {
                message.put("action", event.action.substring(INTENT_PREFIX.length()));
                message.put("id", event.getData().getString(DATA_NOTIFICATION_ID));
                if (NOTIFICATION_BUTTON_CLICKED_ACTION.equals(event.action)) {
                    message.put("buttonIndex", event.getData().getInt(DATA_BUTTON_INDEX));
                }
            }
        };
    }

    @Override
    public void pluginInitialize() {
        getEventHandler().pluginInitialize(this);
        notificationManager = (NotificationManager) cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public boolean execute(String action, CordovaArgs args, final CallbackContext callbackContext) throws JSONException {
        if ("create".equals(action)) {
            create(args, callbackContext);
            return true;
        } else if ("update".equals(action)) {
            update(args, callbackContext);
            return true;
        } else if ("clear".equals(action)) {
            clear(args, callbackContext);
            return true;
        }

        if (getEventHandler().pluginExecute(this, action, args, callbackContext)) {
            return true;
        }

        return false;
    }

    private PendingIntent getExistingNotification(String notificationId) {
        return makePendingIntent(NOTIFICATION_CLICKED_ACTION, notificationId, -1, PendingIntent.FLAG_NO_CREATE);
    }

    private Bitmap makeBitmap(String imageUrl, int scaledWidth, int scaledHeight) {
        InputStream largeIconStream;
        try {
            Uri uri = Uri.parse(imageUrl);
            CordovaResourceApi resourceApi = webView.getResourceApi();
            uri = resourceApi.remapUri(uri);
            largeIconStream = resourceApi.openForRead(uri).inputStream;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to open image file " + imageUrl + ": " + e);
            return null;
        }
        Bitmap unscaledBitmap = BitmapFactory.decodeStream(largeIconStream);
        try {
            largeIconStream.close();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to close image file");
        }
        if (scaledWidth != 0 && scaledHeight != 0) {
            return Bitmap.createScaledBitmap(unscaledBitmap, scaledWidth, scaledHeight, false);
        } else {
            return unscaledBitmap;
        }
    }

    public PendingIntent makePendingIntent(String action, String notificationId, int buttonIndex, int flags) {
        Intent intent = new Intent(cordova.getActivity(), ChromeNotificationsReceiver.class);
        String fullAction = action + "|" + notificationId;
        if (buttonIndex >= 0) {
            fullAction += "|" + buttonIndex;
        }       
        intent.setAction(fullAction);	
	intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	//intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);	
        getEventHandler().makeBackgroundEventIntent(intent);
        return PendingIntent.getBroadcast(cordova.getActivity(), 0, intent, flags);
    }

    private void makeNotification(String notificationId, JSONObject options) throws JSONException {
        
        Context context = cordova.getActivity();
        String pkgName = context.getPackageName();      	    
	    
        Notification.Builder mBuilder = new Notification.Builder(context)
	    .setSmallIcon(context.getApplicationInfo().icon)
            .setContentTitle(options.getString("title"))
            .setContentText(options.getString("message"))
            .setPriority(1)	    
            .setContentIntent(makePendingIntent(NOTIFICATION_CLICKED_ACTION, notificationId, -1, PendingIntent.FLAG_CANCEL_CURRENT))
	    .setDeleteIntent(makePendingIntent(NOTIFICATION_CLOSED_ACTION, notificationId, -1, PendingIntent.FLAG_CANCEL_CURRENT));
	   		    
        Notification notifibuild = mBuilder.build();    
        notifibuild.sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);       
	NotificationManager mNotificationManager = (NotificationManager) cordova.getActivity().getSystemService(Context.NOTIFICATION_SERVICE);			
	mNotificationManager.notify(notificationId.hashCode(), notifibuild);
    }

    private void updateNotification(String notificationId, JSONObject updateOptions, JSONObject originalOptions) throws JSONException {
        JSONObject mergedOptions;

        if (originalOptions == null) {
            mergedOptions = updateOptions;
        } else {
            // Merge the update options with those previously used to create the notification
            mergedOptions = originalOptions;
            Iterator iterator = updateOptions.keys();
            while (iterator.hasNext()) {
                String key = (String) iterator.next();
                mergedOptions.put(key, updateOptions.get(key));
            }
        }

        makeNotification(notificationId, mergedOptions);
    }

    private void create(final CordovaArgs args, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    makeNotification(args.getString(0), args.getJSONObject(1));
                    callbackContext.success();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Could not create notification", e);
                    callbackContext.error("Could not create notification");
                }
            }
        });
    }

    private void update(final CordovaArgs args, final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String notificationId = args.getString(0);
                    PendingIntent existingNotification = getExistingNotification(notificationId);

                    if (existingNotification != null) {
                        updateNotification(notificationId, args.getJSONObject(1), args.optJSONObject(2));
                        callbackContext.success(1);
                    } else {
                        callbackContext.success(0);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Could not update notification", e);
                    callbackContext.error("Could not update notification");
                }
            }
        });
    }

    private void clear(final CordovaArgs args, final CallbackContext callbackContext) {
        try {
            String notificationId = args.getString(0);
            PendingIntent pendingIntent = getExistingNotification(notificationId);

            if (pendingIntent != null) {
                Log.w(LOG_TAG, "Cancel notification: " + notificationId);
                notificationManager.cancel(notificationId.hashCode());
                pendingIntent.cancel();
                callbackContext.success(1);
            } else {
                Log.w(LOG_TAG, "Cancel notification does not exist: " + notificationId);
                callbackContext.success(0);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not clear notification", e);
            callbackContext.error("Could not clear notification");
        }
    }
}
