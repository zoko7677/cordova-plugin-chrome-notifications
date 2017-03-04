package org.chromium;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ChromeNotificationsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      ChromeNotifications.getEventHandler().handleBroadcast(context, intent);          
      Intent LaunchIntent;      
	  LaunchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
	  LaunchIntent.setAction(this.getIntentValueString("ACTION_MAIN"));
	  context.startActivityForResult(LaunchIntent, 1);	       
    }
}
