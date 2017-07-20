package watcher.zivlital.hitdetector;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.wearable.view.DismissOverlayView;
import android.util.Log;
import android.view.GestureDetector;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/*
this class responsible for detecting unusual alerts and send emails to the relevant contacts in the DB,
 and write the event data to the DB.
 */

public class HITDetectionService extends Service implements SensorEventListener {

    private final static int NOT_ID = 201;
    private double[] last_gravity = new double[3];
    private double[] gravity = new double[3];
    private boolean firstChange = true;
    private boolean flag=false;
    private double changeAmount = 0;
    private LinearLayout textLay;
    private RelativeLayout teddyLay;
    private DismissOverlayView mDismissOverlay;
    private GestureDetector mDetector;
    private PowerManager.WakeLock mWakeLock;
    private Vibrator v;
    private static final String TAG = "FDCountdown";
    private GoogleApiClient mGoogleApiClient;
    private boolean retrying = true;
    private long delay = 100;
    private SensorManager sensorManager;
    private Sensor sensor;
    public static String[] toArr;
    public  static String[] lookAfterID;
    public  static String[] lookAfterName;
    public static String pairPass = "";
    public boolean hasContact = false;
    private static String checkMinute="";
    private static long currentTimestamp;
    public static String hitClick=null;
    private DatabaseReference mRootRef = FirebaseDatabase.getInstance().getReference();//create a connection with firebase DB

    @Override
    //this method calls to accelerometer sensor, and sensor manager
    public void onCreate() {

        Notification not = new NotificationCompat.Builder(this).setContentTitle("HIT Detector").setSmallIcon(R.drawable.generic_confirmation_00163).setContentText("Service is running").build();
        startForeground(NOT_ID, not);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, sensor, 13333);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Binding not supported");
    }

    /*this method is checking if there is unusual alert(hit detected),
    and make sure that in period of 1 minute will be in the db only 1 event
    */
    @Override
    public void onSensorChanged(SensorEvent event) {
        //calculate |n|
        last_gravity[0] = gravity[0];
        last_gravity[1] = gravity[1];
        last_gravity[2] = gravity[2];

        gravity[0] = event.values[0];
        gravity[1] = event.values[1];
        gravity[2] = event.values[2];

        changeAmount = Math.pow((gravity[0] - last_gravity[0]), 2) +
                Math.pow((gravity[1] - last_gravity[1]), 2) +
                Math.pow((gravity[2] - last_gravity[2]), 2);


        if (!firstChange && changeAmount >= 800) {
            Log.d("TEST", "FOUND !!!! changeAmount - " + changeAmount);
            if(checkMinute=="") {
                currentTimestamp = System.currentTimeMillis();
                sendNotification();
                Intent alertIntent = new Intent(this, AlertActivity.class);
                Bundle b = new Bundle();
                b.putBoolean("firstOpen", false);
                hitClick ="true";
                alertIntent.putExtra("Hotel Bundle", b);
                alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                this.startActivity(alertIntent);

                stopSelf();
                checkMinute = "not empty";
            }
            else{

                long searchTimestamp = System.currentTimeMillis();
                long difference=Math.abs(currentTimestamp - searchTimestamp);
                if(difference < 1 * 60 * 1000 ) {
                    Log.d("do nothing!!", "!!!");
                }
                else
                {
                    sendNotification();
                    Intent alertIntent = new Intent(this, AlertActivity.class);
                    Bundle b = new Bundle();
                    b.putBoolean("firstOpen", false);
                    hitClick ="true";
                    alertIntent.putExtra("Hotel Bundle", b);
                    alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    this.startActivity(alertIntent);
                    Log.d("more then 1 minute", "!!!");
                    currentTimestamp = System.currentTimeMillis();
                    stopSelf();
                }

            }

        }
        firstChange = false;

    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        sensorManager.unregisterListener(this, sensor);

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    // this method responsible for invoke the methods read/write data to and from the DB
    private void sendNotification() {


        Log.d("", "set data to DB");
        String Time = "";
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        String Date = dateFormat.format(new Date());
        Calendar startingTime = Calendar.getInstance(TimeZone.getDefault());
        SimpleDateFormat HourFormat = new SimpleDateFormat("HH:mm:ss");
        Time = HourFormat.format(new Date());
        pairPass = AlertActivity.getPairPass();

        writeDataToDB(Time, Date, pairPass);
        Log.d("before Send email", " before Send email");
        readDataFromDB(Date,Time, pairPass);

    }
    //this method responsible for write date,password and current time to event table in the DB
    public void writeDataToDB(String Time, String Date, String Pass) {

        DatabaseReference usersRef = mRootRef.child("Event");
        Map<String, String> userData = new HashMap<String, String>();

        userData.put("Time", Time);
        userData.put("Date", Date);
        userData.put("PairPassword", Pass);
        String key = usersRef.push().getKey();

        usersRef = mRootRef.child("Event").child(key);
        usersRef.setValue(userData);

    }
    // this method getting list of contact and send email to the list emails.
    protected void sendEmail(String Date, String Time,String[] lookAfterName,String[] lookAfterID,String[] toArr) {
        Log.d("Send email", "Send email");
        Mail m = new Mail("watcherTakingCare@gmail.com", "zivlital0806");
        m.setTo(toArr);
        m.setFrom("watcherTakingCare@gmail.com");
        m.setSubject("ALERT DETECTED!!!");
        m.setBody("Dear Caregiver, we have detected some unusual alert in " + Date + " " + Time +". Your relative "+lookAfterName[0]+" might be under risk. Please check on as soon as possible. Sincerely yours, Watcher team ");

        try {
            if(m.send()) {
                Log.e("MailApp", "Email was sent successfully.");
            } else {
                Log.e("MailApp", "Email was not sent.");
            }

        } catch (Exception e) {
            Log.e("MailApp", "Could not send email", e);
        }
    }
    //this method read from the DB the contact details and forward the details to sendEmail function above
    //this method forward details to sendEmail function only if there is userID that fit to the userID in the memory.
    public void readDataFromDB(String Date, String Time, final String Pass) {

        final String DateTemp=Date;
        final String TimeTemp=Time;
        final String PassTemp=Pass;

        DatabaseReference ref = mRootRef.child("contacts");
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                String email="";
                String lookAfter_ID="";
                String lookAfter_Name="";
                String dbPairPass= "";

                int i=0;
                int j=0;
                boolean displayContact = false;
                for (DataSnapshot messageSnapshot: dataSnapshot.getChildren()) {
                    dbPairPass =(String) messageSnapshot.child("pairPassword").getValue();
                    displayContact = dbPairPass.equals(Pass);
                    if(displayContact==true) {
                        i++;
                        hasContact=true;
                    }

                }

                String[] emails=new String[i];
                lookAfterID=new String[i];
                toArr=new String[i];
                lookAfterName=new String[i];
                for (DataSnapshot messageSnapshot: dataSnapshot.getChildren()) {
                    dbPairPass =(String) messageSnapshot.child("pairPassword").getValue();
                    displayContact = dbPairPass.equals(Pass);
                    if(displayContact==true) {
                        email = (String) messageSnapshot.child("email").getValue();
                        lookAfter_ID = (String) messageSnapshot.child("lookAfter_ID").getValue();
                        lookAfter_Name = (String) messageSnapshot.child("lookAfter_Name").getValue();

                        emails[j] = email;
                        lookAfterID[j] = lookAfter_ID;
                        toArr[j] = emails[j];
                        lookAfterName[j] = lookAfter_Name;
                        j++;
                    }

                }


                if(hasContact==true){
                    Log.d("send email:","sending email" );
                    sendEmail(DateTemp,TimeTemp,lookAfterName,lookAfterID,toArr);
                }
                else{
                    Log.d("notMatch", "notMatch pass");
                }


            }


            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });

    }
    public static String getHitClick()
    {
        return hitClick;
    }


}



