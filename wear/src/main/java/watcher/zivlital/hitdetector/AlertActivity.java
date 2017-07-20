package watcher.zivlital.hitdetector;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.wearable.view.DismissOverlayView;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Wearable;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * This class shows the alert screen where the user can cancel vibrate and displaying for 10 seconds.
 * After the countdown finishes, the service strat running again.
 * When alert detectes, paired web app will display the new alert
 * The service is instructed to send emergency Email to defined Contacts
 */
public class AlertActivity extends Activity {

    private LinearLayout textLay;
    private RelativeLayout teddyLay;
    private RelativeLayout loginLay;
    //private TextView mTextView;
    //private TextView countDown;
    //private TextView closeInfo;
    private DismissOverlayView mDismissOverlay;
    private GestureDetector mDetector;
    private PowerManager.WakeLock mWakeLock;
    private Vibrator v;
    private static final String TAG = "FDCountdown";
    private GoogleApiClient mGoogleApiClient;
    //private boolean retrying = true;
    //private long delay = 100;    //private String startText = "WOW FIRST START";

    //private String startText = "WOW FIRST START";
    private boolean flag=false;
    //private boolean start=false;
    public static String userID=null;
    private boolean loginClick = true;
    private boolean isHitClick=false;
    public static String hitClick = "false";
    private int countDownSeconds = 10;
    private DatabaseReference mRootRef = FirebaseDatabase.getInstance().getReference();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.buildGoogleApiClient();
        mGoogleApiClient.connect();

        if (mWakeLock == null || !mWakeLock.isHeld()) {
            //wake the screen up
            mWakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "Alert");
            mWakeLock.acquire();
        }

        final Bundle b = this.getIntent().getExtras();
        //inflate layout for round or square screen
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);

        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                textLay = (LinearLayout) stub.findViewById(R.id.text_lay);
                teddyLay = (RelativeLayout) stub.findViewById(R.id.teddy_lay);
                loginLay = (RelativeLayout) stub.findViewById(R.id.Login_lay);
                //closeInfo = (TextView) stub.findViewById(R.id.dismissInfo);
                //mTextView = (TextView) stub.findViewById(R.id.SMStext);
                //countDown = (TextView) stub.findViewById(R.id.countDown);
                mDismissOverlay = (DismissOverlayView) findViewById(R.id.dismiss_overlay);
                mDismissOverlay.showIntroIfNecessary();

                //Check if click on teddyLay should start HITDetectionService
                hitClick = HITDetectionService.getHitClick();
                if(hitClick!=null){
                    isHitClick = hitClick.equals("true");
                }

                //Check which flow to start when clicking on teddyLay
                teddyLay.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(loginClick==false || isHitClick==true){
                            //start HIT Detection Service
                            Log.d("hitDetection", "hit detection is running");
                            onStartService();
                        }
                        else{
                            //start login flow
                            Log.d("login", "login flow is running");
                            onStartLogin();
                        }
                    }
                });

                //Check which screen to display
                boolean firstOpen = true;
                if(b!=null && !b.isEmpty())
                    firstOpen = b.getBoolean("firstOpen");
                setFirstOpen(firstOpen);

            }
        });

        //restart service on long press
        mDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            public void onLongPress(MotionEvent ev) {
                mDismissOverlay.show();
                onStartService();
            }
        });

        v = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);


    }

    //Check which layout to show or disable
    private void setFirstOpen(boolean isFirst)
    {
        if(isFirst)
        {
            //Display teddy screen
            textLay.setVisibility(View.GONE);
            teddyLay.setVisibility(View.VISIBLE);
            loginLay.setVisibility(View.GONE);
        }
        else {
            //Display alert screen
            textLay.setVisibility(View.VISIBLE);
            teddyLay.setVisibility(View.GONE);
            loginLay.setVisibility(View.GONE);

            startVibrate();
            Log.d(TAG, "starting call countdown");
            //start countdown to stop vibrating and restart the service
            new CountDownTimer(countDownSeconds * 1000, 1000) {

                public void onTick(long millisUntilFinished) {
                    //countDown.setText((millisUntilFinished / 1000) + "s");
                    Log.d("ticking call countdown", (millisUntilFinished / 1000) + "s");
                }

                public void onFinish() {
                    //restart service when countdown finished
                    Log.d(TAG, "finish call countdown");
                    mDismissOverlay.show();
                    onStartService();
                }
            }.start();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mDetector.onTouchEvent(ev) || super.onTouchEvent(ev);
    }

    /**
     * This method tries to establish a connection to the Google Data Layer API
     * in order to communicate with the paired Smartphone
     */
    private void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                        // Now we can use the Data Layer API to send messages to the phone
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        Log.d(TAG, "onConnectionSuspended: " + cause);
                        if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
                            //reconnect if we are not connected or connecting
                            mGoogleApiClient.reconnect();
                        }
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        Log.d(TAG, "onConnectionFailed: " + result);
                        //try again
                        buildGoogleApiClient();
                    }
                })
                // Request access only to the Wearable API
                .addApi(Wearable.API)
                .build();
    }

    //Create random PIN, add it to the DB and displayed it to the user in the first login
    public void writeUserIDToDB() {
        DatabaseReference usersRef = mRootRef.child("UserID");
        Map<String, String> userData = new HashMap<String, String>();
        int randomPIN = (int)(Math.random()*9000)+1000;
        userID = String.valueOf(randomPIN);
        userData.put("Pass", userID); //userID
        String key = usersRef.push().getKey();
        usersRef = mRootRef.child("UserID").child(key);
        usersRef.setValue(userData);

        TextView pass = (TextView) findViewById(R.id.userPass);
        pass.setText(userID); //userID
    }

    public static String getPairPass()
    {
        return userID; //userID
    }

    /**
     * Show the login screen, write the userid to DB and set a listener to login button
     * When clicking on the login button - Display teddy screen and start the HIT detection service
     */
    protected void onStartLogin(){
        if (v.hasVibrator()) {
            v.cancel();
        }
        textLay.setVisibility(View.GONE);
        teddyLay.setVisibility(View.GONE);
        loginLay.setVisibility(View.VISIBLE);

        writeUserIDToDB();
        loginClick = false;
        final Button startService;
        startService = (Button) findViewById(R.id.login) ;
        startService.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                onStartService();
            }
        });

        Log.d("", "finish login flow");
    }

    /**
     * Show the teddy screen
     * Stop vibrating
     * Start the HIT detection service
     */
    protected void onStartService(){

        Log.d("", "start HIT detection service");
        textLay.setVisibility(View.GONE);
        teddyLay.setVisibility(View.VISIBLE);
        loginLay.setVisibility(View.GONE);

        if (v.hasVibrator()) {
            v.cancel();
        }

        Intent i = new Intent(this, HITDetectionService.class);
        this.startService(i);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();

        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    public void startVibrate(){
        //vibrate during countdown

        if (v.hasVibrator()) {
            Log.d("", "Vibrating");
            // pause for 1.5 seconds, vibrate for 1.5 seconds, repeat
            long[] pattern = {1500, 1500, 1500, 1500};
            v.vibrate(pattern, 0);
        }
    }
}
