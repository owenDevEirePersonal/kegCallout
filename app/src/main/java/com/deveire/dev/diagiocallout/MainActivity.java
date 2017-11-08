package com.deveire.dev.diagiocallout;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.ResultReceiver;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.TimeFormatException;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends FragmentActivity implements com.google.android.gms.location.LocationListener, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, RecognitionListener
{

    private TextView kegText;
    private Button demoHeadsetButton;

    private Calendar aCalendar;
    private SimpleDateFormat aDateFormat;

    double currentLat;
    double currentLon;


    //[Text To Speech Variables]
    private TextToSpeech toSpeech;
    private String speechInText;
    private HashMap<String, String> endOfSpeakIndentifier;

    private final String textToSpeechID_Clarification = "Clarification";
    //[/Text To Speech Variables]

    private SpeechRecognizer recog;
    private Intent recogIntent;
    private int pingingRecogFor;
    private int previousPingingRecogFor;
    private final int pingingRecogFor_KegNumber = 1;
    private final int pingingRecogFor_Confirmation = 2;
    private final int pingingRecogFor_KegStatus = 3;
    private final int pingingRecogFor_Clarification = 4;
    private final int pingingRecogFor_Nothing = -1;

    private int currentKegNumber;

    private String[] currentPossiblePhrasesNeedingClarification;

    //++++[Location Variables]
        private GoogleApiClient mGoogleApiClient;
        private Location locationReceivedFromLocationUpdates;
        private Location userLocation;
        private MainActivity.AddressResultReceiver geoCoderServiceResultReciever;
        private int locationScanInterval;

        LocationRequest request;
        private final int SETTINGS_REQUEST_ID = 8888;
        private final String SAVED_LOCATION_KEY = "79";
    //+++++[/Location Variables]

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //[Location Setup]
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();

        locationScanInterval = 60;//in seconds


        request = new LocationRequest();
        request.setInterval(locationScanInterval * 1000);//in mileseconds
        request.setFastestInterval(5000);//caps how fast the locations are recieved, as other apps could be triggering updates faster than our app.
        request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY); //accurate to 100 meters.

        LocationSettingsRequest.Builder requestBuilder = new LocationSettingsRequest.Builder().addLocationRequest(request);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        requestBuilder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>()
        {
            @Override
            public void onResult(@NonNull LocationSettingsResult aResult)
            {
                final Status status = aResult.getStatus();
                final LocationSettingsStates states = aResult.getLocationSettingsStates();
                switch (status.getStatusCode())
                {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can
                        // initialize location requests here.

                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try
                        {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(MainActivity.this, SETTINGS_REQUEST_ID);
                        } catch (IntentSender.SendIntentException e)
                        {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        break;
                }
            }
        });

        geoCoderServiceResultReciever = new MainActivity.AddressResultReceiver(new Handler());
        //[/Location Setup]



        kegText = (TextView) findViewById(R.id.kegText);
        demoHeadsetButton = (Button) findViewById(R.id.demoHeadsetButton);
        demoHeadsetButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                {
                    toSpeech.speak("What is the Keg's Number: ", TextToSpeech.QUEUE_FLUSH, null, "AskForKegNum");
                }
            }
        });


        currentKegNumber = 0;
        currentLat = 0;
        currentLon = 0;

        recog = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        recog.setRecognitionListener(this);
        recogIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"en");
        recogIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getApplicationContext().getPackageName());
        recogIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recogIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        currentPossiblePhrasesNeedingClarification = new String[]{};

        setupTextToSpeech();


        aCalendar = Calendar.getInstance();
        aDateFormat = new SimpleDateFormat("hh:mm:ss dd/MM/yyyy");


    }

    @Override
    protected void onResume()
    {
        super.onResume();

    }

    @Override
    protected void onPause()
    {
        super.onPause();
        toSpeech.stop();
        recog.stopListening();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        recog.destroy();
    }


    //++++++++[Text To Speech Code]
    private void setupTextToSpeech()
    {
        toSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status)
            {
                Log.i("Text To Speech Update", "onInit Complete");
                toSpeech.setLanguage(Locale.ENGLISH);
                endOfSpeakIndentifier = new HashMap();
                endOfSpeakIndentifier.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "endOfSpeech");
                toSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener()
                {
                    @Override
                    public void onStart(String utteranceId)
                    {
                        Log.i("Text To Speech Update", "onStart called");
                    }

                    @Override
                    public void onDone(String utteranceId)
                    {
                        switch (utteranceId)
                        {
                            case "AskForKegNum":
                                runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        pingingRecogFor = pingingRecogFor_KegNumber;
                                        recog.startListening(recogIntent);
                                    }
                                });
                                break;

                            case "ConfirmKegNum":
                                runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        pingingRecogFor = pingingRecogFor_Confirmation;
                                        recog.startListening(recogIntent);
                                    }
                                });
                                break;

                            case "AskForStatus":
                                runOnUiThread(new Runnable()
                                {
                                    @Override
                                    public void run()
                                    {
                                        pingingRecogFor = pingingRecogFor_KegStatus;
                                        recog.startListening(recogIntent);
                                    }
                                });
                                break;
                        }
                        /*if(utteranceId.matches("QualityAsk") || utteranceId.matches("QuantityAsk"))
                        {
                            pingingRecogFor = pingingRecogFor_Quality
                            recognizer.startListening(recogIntent);
                        }
                        else
                        {
                            recognizer.startListening(recogIntent);
                        }*/
                        //toSpeech.shutdown();
                    }

                    @Override
                    public void onError(String utteranceId)
                    {
                        Log.i("Text To Speech Update", "ERROR DETECTED");
                    }
                });
            }
        });
    }
//++++++++[/Text To Speech Code]

    //++++++++[Recognition Listener Code]
    @Override
    public void onReadyForSpeech(Bundle bundle)
    {
        Log.e("Recog", "ReadyForSpeech");
    }

    @Override
    public void onBeginningOfSpeech()
    {
        Log.e("Recog", "BeginningOfSpeech");
    }

    @Override
    public void onRmsChanged(float v)
    {
        Log.e("Recog", "onRmsChanged");
    }

    @Override
    public void onBufferReceived(byte[] bytes)
    {
        Log.e("Recog", "onBufferReceived");
    }

    @Override
    public void onEndOfSpeech()
    {
        Log.e("Recog", "End ofSpeech");
        recog.stopListening();
    }

    @Override
    public void onError(int i)
    {
        switch (i)
        {
            //case RecognizerIntent.RESULT_AUDIO_ERROR: Log.e("Recog", "RESULT AUDIO ERROR"); break;
            //case RecognizerIntent.RESULT_CLIENT_ERROR: Log.e("Recog", "RESULT CLIENT ERROR"); break;
            //case RecognizerIntent.RESULT_NETWORK_ERROR: Log.e("Recog", "RESULT NETWORK ERROR"); break;
            //case RecognizerIntent.RESULT_SERVER_ERROR: Log.e("Recog", "RESULT SERVER ERROR"); break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: Log.e("Recog", "SPEECH TIMEOUT ERROR"); break;
            case SpeechRecognizer.ERROR_SERVER: Log.e("Recog", "SERVER ERROR"); break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: Log.e("Recog", "BUSY ERROR"); break;
            case SpeechRecognizer.ERROR_NO_MATCH: Log.e("Recog", "NO MATCH ERROR");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                {
                    toSpeech.speak("No Response Detected, aborting.", TextToSpeech.QUEUE_FLUSH, null, null);
                }
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: Log.e("Recog", "NETWORK TIMEOUT ERROR"); break;
            case SpeechRecognizer.ERROR_NETWORK: Log.e("Recog", "TIMEOUT ERROR"); break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: Log.e("Recog", "INSUFFICENT PERMISSIONS ERROR"); break;
            case SpeechRecognizer.ERROR_CLIENT: Log.e("Recog", "CLIENT ERROR"); break;
            case SpeechRecognizer.ERROR_AUDIO: Log.e("Recog", "AUDIO ERROR"); break;
            default: Log.e("Recog", "UNKNOWN ERROR: " + i); break;
        }
    }



    @Override
    public void onResults(Bundle bundle)
    {
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        recogResultLogic(matches);

    }

    @Override
    public void onPartialResults(Bundle bundle)
    {
        Log.e("Recog", "Partial Result");
    }

    @Override
    public void onEvent(int i, Bundle bundle)
    {
        Log.e("Recog", "onEvent");
    }
//++++++++[/Recognition Listener Code]

    //++++++++[Recognition Other Code]
    private String sortThroughRecognizerResults(ArrayList<String> results, String[] matchablePhrases)
    {
        for (String aResult: results)
        {
            Log.i("Recog", "Sorting results for result: " + aResult);
            for (String aPhrase: matchablePhrases)
            {
                Log.i("Recog", "Sorting results for result: " + aResult.toLowerCase().replace("-", " ") + " and Phrase: " + aPhrase.toLowerCase());
                if((aResult.toLowerCase().replace("-"," ")).contains(aPhrase.toLowerCase()))
                {
                    Log.i("Recog", "Match Found");
                    return aPhrase;
                }
            }
        }
        Log.i("Recog", "No matches found, returning empty string \"\" .");
        return "";
    }



    private void sortThroughRecognizerResultsForAllPossiblities(ArrayList<String> results, String[] matchablePhrases)
    {
        ArrayList<String> possibleResults = new ArrayList<String>();
        for (String aResult: results)
        {
            Log.i("Recog", "All Possiblities, Sorting results for result: " + aResult);
            for (String aPhrase: matchablePhrases)
            {
                Boolean isDuplicate = false;
                Log.i("Recog", "All Possiblities, Sorting results for result: " + aResult.toLowerCase().replace("-", " ") + " and Phrase: " + aPhrase.toLowerCase());
                for (String b: possibleResults)
                {
                    if(b.matches(aPhrase)){isDuplicate = true; break;}
                }

                if((aResult.toLowerCase().replace("-"," ")).contains(aPhrase.toLowerCase()) && !isDuplicate)
                {
                    Log.i("Recog", "All Possiblities, Match Found");
                    possibleResults.add(aPhrase);
                }
            }
        }

        currentPossiblePhrasesNeedingClarification = possibleResults.toArray(new String[possibleResults.size()]);
        //if there is more than 1 keyword in the passed phrase, the method will list those keywords back to the user and ask them to repeat  the correct 1.
        //This in turn will call recogResult from the utterance listener and trigger the pinging for Clarification case where the repeated word will then be used
        //to resolve the logic of the previous call to recogResult.
        if(possibleResults.size() > 1)
        {
            String clarificationString = "I'm sorry but did you mean.";

            for (String a: possibleResults)
            {
                clarificationString += (". " + a);
                if(!possibleResults.get(possibleResults.size() - 1).matches(a))
                {
                    clarificationString += ". or";
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                pingingRecogFor = pingingRecogFor_Clarification;
                toSpeech.speak(clarificationString, TextToSpeech.QUEUE_FLUSH, null, textToSpeechID_Clarification);
            }
        }
        //if there is only 1 keyword in the passed phrase, the method skips speech confirmation and immediately calls it's own listener in recogResults,
        // which(given that there is only 1 possible match, will skip to resolving the previous call to recogResult's logic)
        else if (possibleResults.size() == 1)
        {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                pingingRecogFor = pingingRecogFor_Clarification;
                recogResultLogic(possibleResults);
                //toSpeech.speak("h", TextToSpeech.QUEUE_FLUSH, null, textToSpeechID_Clarification);
            }
        }
        else
        {
            Log.i("Recog", "No matches found, Requesting Repetition .");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                toSpeech.speak("Can you please repeat that?", TextToSpeech.QUEUE_FLUSH, null, textToSpeechID_Clarification);
            }
        }
    }

    private String sortThroughRecognizerResults(ArrayList<String> results, String matchablePhrase)
    {
        for (String aResult: results)
        {
            Log.i("Recog", "Sorting results for result: " + aResult.replace("-", " ") + " and Phrase: " + matchablePhrase.toLowerCase());
            if((aResult.replace("-", " ")).contains(matchablePhrase.toLowerCase()))
            {
                Log.i("Recog", "Match Found");
                return matchablePhrase;
            }
        }
        Log.i("Recog", "No matches found, returning empty string \"\" .");
        return "";
    }


    //CALLED FROM: RecogListener onResults()
    private void recogResultLogic(ArrayList<String> matches)
    {
        String[] phrases;
        Log.i("Recog", "Results recieved: " + matches);
        String response = "-Null-";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            Log.i("Recog", "Pinging For: " + pingingRecogFor);
            switch (pingingRecogFor)
            {
                case pingingRecogFor_KegNumber:

                    Log.i("Recog", "onResult for Keg Number");
                    int aNum = 0;
                    String aNumString = "";
                    boolean isANumber = false;
                    for(String aMatch: matches)
                    {
                        try
                        {
                            aNum = (int) Integer.parseInt(aMatch.replace(" ", ""));
                            if(aNum / 10000000 >= 1) //if number is 8 digits long
                            {
                                isANumber = true;
                                aNumString = aMatch;
                                break;
                            }
                            else
                            {
                                toSpeech.speak("Number not Recognised, please repeat", TextToSpeech.QUEUE_FLUSH, null, "AskForKegNum");
                            }
                        }
                        catch(Exception e)
                        {
                            Log.e("Recog", "match is not a number:" + aMatch);
                        }
                    }

                    if(isANumber)
                    {
                        currentKegNumber = aNum;
                        toSpeech.speak("Keg Number: " + aNumString + ". is that correct?", TextToSpeech.QUEUE_FLUSH, null, "ConfirmKegNum");
                    }
                break;


                case pingingRecogFor_Confirmation:

                    Log.i("Recog", "onResult for Confirmation");
                    phrases = new String[]{"yes", "that is correct", "ok", "right", "thats right", "no"};
                    response = sortThroughRecognizerResults(matches, phrases);
                    Log.i("Recog", "onConfirmation: Response= " + response);
                    if (response.matches("yes") || response.matches("that is correct") || response.matches("ok") || response.matches("right") || response.matches("thats right"))
                    {
                        toSpeech.speak("Number Confirmed. What is the kegs status?", TextToSpeech.QUEUE_FLUSH, null, "AskForStatus");
                    }
                    else if(response.matches("no"))
                    {
                        toSpeech.speak("Please Repeat the Keg Number: ", TextToSpeech.QUEUE_FLUSH, null, "AskForKegNum");
                    }
                break;

                case pingingRecogFor_KegStatus:
                    Log.i("Recog", "onResult for KegStatus");
                    phrases = new String[]{"dropping off", "drop off", "dropped off", "picked up", "pickup", "picking up", "damaged"};
                    response = sortThroughRecognizerResults(matches, phrases);
                    Log.i("Recog", "onConfirmation: Response= " + response);
                    if (response.matches("dropping off") || response.matches("dropped off") || response.matches("drop off"))
                    {
                        Log.i("Recog", "Keg " + currentKegNumber + " dropped off.");
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                kegText.setText("Keg " + currentKegNumber + " dropped off at \n(" + currentLat + "^, " + currentLon + "^) at \n" + aDateFormat.format(aCalendar.getTime()));
                            }
                        });

                    }
                    else if(response.matches("picking up") || response.matches("picked up") || response.matches("pickup"))
                    {
                        Log.i("Recog", "Keg " + currentKegNumber + " picking up.");
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                kegText.setText("Keg " + currentKegNumber + " picked up at \n(" + currentLat + "^, " + currentLon + "^) at \n" + aDateFormat.format(aCalendar.getTime()));
                            }
                        });
                    }
                    else if(response.matches("damaged"))
                    {
                        Log.i("Recog", "Keg " + currentKegNumber + " reported damaged.");
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                kegText.setText("Keg " + currentKegNumber + " reported damaged at \n(" + currentLat + "^, " + currentLon + "^) at \n" + aDateFormat.format(aCalendar.getTime()));
                            }
                        });
                    }
                break;

            }
        }
    }
//++++++++[/Recognition Other Code]


//++++++++[Location Code]
//**********[Location Update and server pinging Code]
    @Override
    public void onConnectionFailed(ConnectionResult result)
    {
    // An unresolvable error has occurred and a connection to Google APIs
    // could not be established. Display an error message, or handle
    // the failure silently

    // ...
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            locationReceivedFromLocationUpdates = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, this);
            if(locationReceivedFromLocationUpdates != null)
            {
                //YES, lat and long are multi digit.
                if(Geocoder.isPresent())
                {
                    startIntentService();
                }
                else
                {
                    Log.e("ERROR:", "Geocoder is not avaiable");
                }
            }
            else
            {

            }


        }



    }

    @Override
    public void onConnectionSuspended(int i)
    {
        //put other stuff here
    }

    //update app based on the new location data, and then begin pinging servlet with the new location
    @Override
    public void onLocationChanged(Location location)
    {
        locationReceivedFromLocationUpdates = location;
        userLocation = locationReceivedFromLocationUpdates;
        //locationReceivedFromLocationUpdates = fakeUserLocation;


        if(locationReceivedFromLocationUpdates != null)
        {
            //startDownload();
            currentLat = userLocation.getLatitude();
            currentLon = userLocation.getLongitude();
        }
        else
        {

            Log.e("ERROR", "Unable to send location to sevrver, current location = null");
        }

    }

    @Override
    public void onSaveInstanceState(Bundle savedState)
    {
        savedState.putParcelable(SAVED_LOCATION_KEY, locationReceivedFromLocationUpdates);
        super.onSaveInstanceState(savedState);
    }

    private void restoreSavedValues(Bundle savedInstanceState)
    {
        if (savedInstanceState != null)
        {

            // Update the value of mCurrentLocation from the Bundle and update the
            // UI to show the correct latitude and longitude.
            if (savedInstanceState.keySet().contains(SAVED_LOCATION_KEY))
            {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that
                // mCurrentLocationis not null.
                locationReceivedFromLocationUpdates = savedInstanceState.getParcelable(SAVED_LOCATION_KEY);
            }

        }
    }

    protected void startIntentService()
    {
        Intent intent = new Intent(this, geoCoderIntent.class);
        intent.putExtra(Constants.RECEIVER, geoCoderServiceResultReciever);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, locationReceivedFromLocationUpdates);
        startService(intent);
    }

class AddressResultReceiver extends ResultReceiver
{
    public AddressResultReceiver(Handler handler) {
        super(handler);
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {

        // Display the address string
        // or an error message sent from the intent service.
        resultData.getString(Constants.RESULT_DATA_KEY);


        // Show a toast message if an address was found.
        if (resultCode == Constants.SUCCESS_RESULT)
        {
            Log.i("Success", "Address found");
        }
        else
        {
            Log.e("Network Error:", "in OnReceiveResult in AddressResultReceiver: " +  resultData.getString(Constants.RESULT_DATA_KEY));
        }

    }
}
//++++++++[/Location Code]

//++++++++[Headset Button Code]
@Override
public boolean onKeyDown(int keyCode, KeyEvent event) {
    Log.i("HeadsetButton", "Button Pressed: " + keyCode);


    switch (keyCode) {
        case KeyEvent.KEYCODE_HEADSETHOOK:
            Log.i("HeadsetButton", "Headset Button Pressed");
            demoHeadsetButton.callOnClick();
            return true;

        case KeyEvent.KEYCODE_MEDIA_PLAY:
            Log.i("HeadsetButton", "Headset Button Pressed");
            demoHeadsetButton.callOnClick();
            return true;

        case KeyEvent.KEYCODE_MEDIA_PAUSE:
            Log.i("HeadsetButton", "Headset Button Pressed");
            demoHeadsetButton.callOnClick();
            return true;
    }
    return super.onKeyDown(keyCode, event);
}


//++++++++[/Headset Button Code]
}
