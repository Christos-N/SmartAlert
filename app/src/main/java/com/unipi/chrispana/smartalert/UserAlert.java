package com.unipi.chrispana.smartalert;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.unipi.chrispana.smartalert.databinding.ActivityUserAlertBinding;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class UserAlert extends AppCompatActivity implements LocationListener {

    ActivityUserAlertBinding binding;
    Uri imageUri;
    StorageReference storageReference;
    String fileName = "";
    Spinner events;
    EditText comments;
    LocationManager locationManager;
    Button insertAlert;
    String location = "";
    FirebaseDatabase database;
    DatabaseReference reference;
    public static final double earthRadius = 6371.0;
    int hours = 0;
    int kilometers = 0;
    LocationListener locationListener;
    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityUserAlertBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        mAuth = FirebaseAuth.getInstance();
        events = findViewById(R.id.insertEvent);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(getString(R.string.userAlert));
        String eq = getString(R.string.earthquake);
        String flood = getString(R.string.flood);
        String hurricane = getString(R.string.hurricane);
        String fire = getString(R.string.fire);
        String storm = getString(R.string.storm);
        String[] eventSpinner = new String[]{eq, flood, hurricane, fire, storm};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, eventSpinner);
        events.setAdapter(adapter);
        comments = findViewById(R.id.insertComments);
        insertAlert = findViewById(R.id.insertAlert);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        getLocation();

        ActivityResultLauncher<Intent> someActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null && data.getData() != null) {
                                imageUri = data.getData();
                                binding.imageView.setImageURI(imageUri);
                            }
                        }
                    }
                });

        //select image
        binding.selectImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                someActivityResultLauncher.launch(intent);
            }
        });
        binding.imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                someActivityResultLauncher.launch(intent);
            }
        });
    }
    //Stops Listening for Location updates
    @Override
    protected void onPause() {
        super.onPause();
        if (locationListener != null)
            locationManager.removeUpdates(locationListener);
        System.out.println("App was paused");
    }
    //Adds logout to action bar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logout_menu, menu);
        return true;
    }
    //Signs out and redirects to MainActivity
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            Intent closeService = new Intent(this, DatabaseListenerService.class);
            closeService.setAction("CLOSE");
            startService(closeService);
            mAuth.signOut();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            Toast.makeText(getBaseContext(), getString(R.string.toastSucLogout), Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    //Starts listening for location updates
    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if(locationManager != null){
            boolean isGPSEnabled = locationManager.isLocationEnabled();
            if(isGPSEnabled && locationListener!=null){
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 10, locationListener);
            }
        }
        Intent serviceIntent = new Intent(UserAlert.this, DatabaseListenerService.class);
        startService(serviceIntent);
        System.out.println("App Resumed");
    }
    //Opens user's gallery, then user selects an image and it is reformatted with a date format and binds it to the ImageView
    public void uploadImage(){

        AlertDialog.Builder builder = new AlertDialog.Builder(UserAlert.this);
        builder.setMessage("Uploading File...");
        builder.setCancelable(false);
        Dialog alertDialog = builder.create();
        alertDialog.setCanceledOnTouchOutside(true);
        alertDialog.show();
        SimpleDateFormat formatter = new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss", Locale.getDefault());
        Date now = new Date();
        fileName = formatter.format(now);
        storageReference = FirebaseStorage.getInstance().getReference(fileName);
        storageReference.putFile(imageUri)
                .addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        binding.imageView.setImageURI(null);
                        Toast.makeText(UserAlert.this,getString(R.string.toastSucUp),Toast.LENGTH_SHORT).show();
                        if (alertDialog.isShowing())
                            alertDialog.dismiss();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        if (alertDialog.isShowing())
                            alertDialog.dismiss();
                        Toast.makeText(UserAlert.this,getString(R.string.toastFailUp),Toast.LENGTH_SHORT).show();
                    }
                });
    }
    //If user has given permission to Location then it gets the user's current location
    public void getLocation(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},123);
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            // Build the alert dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Location Services Not Enabled");
            builder.setMessage("Please enable Location Services");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                }
            });
            Dialog alertDialog = builder.create();
            alertDialog.setCanceledOnTouchOutside(true);
            alertDialog.show();
        }
        else {
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {

                }
            };
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            Location loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (loc != null) {
                location = loc.getLatitude()+ "," + loc.getLongitude();
            }
        }
    }
    //Gets user's location and inserts an alert in the database based on the fields of the activity
    //If a similar event is uploaded within the same range and time then the count field increments by the number of similar incidents
    public void insertAlert(View view){
        getLocation();
        if(locationManager != null){
            boolean isGPSEnabled = locationManager.isLocationEnabled();
            if(!isGPSEnabled){
                return;
            }
        }
        database = FirebaseDatabase.getInstance();
        reference = database.getReference("alerts");

        if (location.equals(""))
            showMessage(getString(R.string.error),getString(R.string.occurerror));
        else {
            String event = events.getSelectedItem().toString();
            String comment = comments.getText().toString();
            String timestamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").format(LocalDateTime.now());
            if(imageUri != null)
                uploadImage();
            String photo = fileName;
            String id = reference.child("alerts").push().getKey();
            AlertClass alertClass = new AlertClass(id,event, comment, location, timestamp, photo);
            reference.get().addOnCompleteListener(new OnCompleteListener<DataSnapshot>() {
                @Override
                public void onComplete(@NonNull Task<DataSnapshot> task) {
                    if (task.isSuccessful()) {
                        for(DataSnapshot alertSnapshot : task.getResult().getChildren()) {
                            switch (alertClass.getEvent()){
                                case "Earthquake":
                                    hours = 2;
                                    kilometers = 150;
                                    break;
                                case "Flood":
                                    hours = 12;
                                    kilometers = 100;
                                    break;
                                case "Hurricane":
                                    hours = 24;
                                    kilometers = 80;
                                    break;
                                case "Fire":
                                    hours = 48;
                                    kilometers = 200;
                                    break;
                                case "Storm":
                                    hours = 5;
                                    kilometers = 50;
                                    break;
                            }
                            if(isWithinHours(alertSnapshot.child("timestamp").getValue(String.class),alertClass.getTimestamp(),hours) &&
                                    alertSnapshot.child("event").getValue(String.class).equals(alertClass.getEvent()) &&
                                    isWithinKilometers(alertSnapshot.child("location").getValue(String.class),alertClass.getLocation(),kilometers)){
                                alertClass.setCount(alertClass.getCount()+1);
                                reference.child(alertSnapshot.getKey()).child("count").setValue(alertSnapshot.child("count").getValue(Integer.class)+1);
                            }
                            System.out.println(alertSnapshot.getKey().toString());
                        }
                        reference.child(id).setValue(alertClass);
                    }
                    else {

                    }
                }
            });

            Toast.makeText(UserAlert.this, getString(R.string.toastSucAlert), Toast.LENGTH_SHORT).show();
            comments.setText("");
            imageUri = null;
            fileName = "";
            binding.imageView.setImageDrawable(getResources().getDrawable(R.drawable.insert_photo_here));
        }
    }
    //Calculates whether n hours have passed from timestamp1 to timestamp2
    public boolean isWithinHours(String timestamp1, String timestamp2, int n) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        try {
            long diff = dateFormat.parse(timestamp1).getTime() - dateFormat.parse(timestamp2).getTime();
            return Math.abs(diff) <= (long) n * 60 * 60 * 1000;
        } catch (ParseException e) {
            e.printStackTrace();
            return false;
        }
    }
    //Calculates the distance between 2 points using Haversine Formula
    public static boolean isWithinKilometers(String location1, String location2, double n) {
        String[] latLong1 = location1.split(",");
        String[] latLong2 = location2.split(",");
        double lat1 = Double.parseDouble(latLong1[0]);
        double lon1 = Double.parseDouble(latLong1[1]);
        double lat2 = Double.parseDouble(latLong2[0]);
        double lon2 = Double.parseDouble(latLong2[1]);

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = earthRadius * c;

        return distance <= n;
    }
    //Displays a message to the user
    public void showMessage(String title, String text){
        new android.app.AlertDialog.Builder(this)
                .setCancelable(true)
                .setTitle(title)
                .setMessage(text)
                .show();
    }
    @Override
    public void onLocationChanged(@NonNull Location location) {

    }
}