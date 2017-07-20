package watcher.zivlital.hitdetector;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * this class starts the fall detection service after the watch has finished booting
 */
public class Autostart extends BroadcastReceiver {
    public Autostart() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, HITDetectionService.class);
        context.startService(i);
        Log.i("autostart", "started Hit detection service");
    }
}
