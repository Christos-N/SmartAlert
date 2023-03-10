package com.unipi.chrispana.smartalert;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity implements LocationListener {

    public static int REQUEST_PERMISSION=1;
    LocationManager locationManager;
    Button login;
    EditText email, password;
    FirebaseAuth mAuth;
    String token = "";
    FirebaseDatabase database;
    DatabaseReference reference;
    FirebaseUser user;
    String location = "";
    private static final int TIME_INTERVAL = 2000; // 2 seconds
    private long mBackPressed;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getString(R.string.mainActivity));
        setContentView(R.layout.activity_main);
        login = findViewById(R.id.loginbutton);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        askForLocationPermission();
        email = findViewById(R.id.insertEmail);
        password = findViewById(R.id.insertPassword);
        mAuth = FirebaseAuth.getInstance();

        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            System.out.println("Could not access the token");
                            return;
                        }

                        // Get new FCM registration token
                        token = task.getResult();
                    }
                });
    }
    //If user has given permission to Location then it gets the user's current location
    public void getLocation(){

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                // Build the alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Location Services Not Enabled!");
                builder.setMessage("Please enable Location Services.");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // Show location settings when the user acknowledges the alert dialog
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                });
                Dialog alertDialog = builder.create();
                alertDialog.setCanceledOnTouchOutside(true);
                alertDialog.show();
            } else {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, (LocationListener) this);
            }
        }
    }
    //Asks user for Location Permission
    private void askForLocationPermission(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},123);
        }
    }
    //Checks if user has given permission to Background Location and Notifications and if yes redirects the user to Register Activity
    public void register(View view){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_BACKGROUND_LOCATION )== PackageManager.PERMISSION_GRANTED) {
            getLocation();
            Intent intent = new Intent(this, RegisterActivity.class);
            startActivity(intent);
        }else {
            Toast.makeText(this, getString(R.string.toastGrantLocation), Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        }
    }
    //Checks if user has given permission to Background Location and Notifications and if yes then checks the authentication
    //If it is a User: it opens the DatabaseListener Service and redirects them to ViewStatistics
    //If it is an Employee: it redirects them to ViewEvents
    public void login(View view){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_BACKGROUND_LOCATION )== PackageManager.PERMISSION_GRANTED) {
            getLocation();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, (LocationListener) this);
            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (loc != null) {
                location = loc.getLatitude()+ "," + loc.getLongitude();
            }
            if(!email.getText().toString().equals("") && !password.getText().toString().equals("")) {
                    if (location.equals(""))
                        showMessage(getString(R.string.error), getString(R.string.occurerror));
                    else {
                        database = FirebaseDatabase.getInstance();
                        reference = database.getReference("all_users");
                        mAuth.signInWithEmailAndPassword(email.getText().toString(), password.getText().toString()).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                if (task.isSuccessful()) {
                                    user = mAuth.getCurrentUser();
                                    reference.child(user.getUid()).child("token").setValue(token);
                                    reference.child(user.getUid()).child("location").setValue(location);
                                    Toast.makeText(MainActivity.this, getString(R.string.toastSucLogIn), Toast.LENGTH_SHORT).show();
                                    reference.get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<DataSnapshot> task) {
                                            if (task.isSuccessful()) {
                                                for (DataSnapshot alertSnapshot : task.getResult().getChildren()) {
                                                    if (alertSnapshot.child("uid").getValue().equals(user.getUid())) {
                                                        if (alertSnapshot.child("role").getValue(String.class).equals("user")) {
                                                            Intent intent = new Intent(MainActivity.this, ViewStatistics.class);
                                                            startActivity(intent);
                                                            return;
                                                        } else if (alertSnapshot.child("role").getValue(String.class).equals("employee")) {
                                                            Intent intent = new Intent(MainActivity.this, ViewEvents.class);
                                                            startActivity(intent);
                                                            return;
                                                        }
                                                    }
                                                }
                                            } else {
                                                Log.d("Task was not successful", String.valueOf(task.getResult().getValue()));
                                            }
                                        }
                                    });
                                } else {
                                    showMessage(getString(R.string.error), task.getException().getLocalizedMessage());
                                }
                            }
                        });
                    }
                }
                else{
                    showMessage(getString(R.string.error), getString(R.string.fillForm));
                }
        }
        else {
            Toast.makeText(this, getString(R.string.toastGrantLocation), Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    //Displays a message to the user
    public void showMessage(String title, String text){
        new android.app.AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle(title)
                .setMessage(text)
                .show();
    }
    //If the user presses the back button twice in 2 seconds the app moves to the background
    @Override
    public void onBackPressed() {
        if (mBackPressed + TIME_INTERVAL > System.currentTimeMillis()) {
            moveTaskToBack(true);
        } else {
            Toast.makeText(getBaseContext(), getString(R.string.toastBackAgain), Toast.LENGTH_SHORT).show();
        }
        mBackPressed = System.currentTimeMillis();
    }
}