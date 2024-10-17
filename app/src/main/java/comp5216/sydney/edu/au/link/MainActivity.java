package comp5216.sydney.edu.au.link;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.OpeningHours;
import com.google.android.libraries.places.api.model.PhotoMetadata;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.model.PlaceLikelihood;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

import androidx.navigation.ui.AppBarConfiguration;
import comp5216.sydney.edu.au.link.databinding.ActivityMainBinding;
import comp5216.sydney.edu.au.link.landing.LoginActivity;
import comp5216.sydney.edu.au.link.model.UserProfile;


import java.util.Arrays;
import java.util.List;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private PlacesClient placesClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private Location currentLocation;
    private FirebaseFirestore firestore;
    private String fileName = "user_data.txt";
    private FirebaseUser currentUser;
    private UserProfile currentUserProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initFirestore();
        testDatabase();
        if (currentUserProfile == null) {
            currentUserProfile = new UserProfile();
        }


        // Initialize the Places API
        Places.initialize(getApplicationContext(), "AIzaSyAA87EkKQ1JX341Q3fMnyrDd1UiCs19FI8");

        placesClient = Places.createClient(this);

        // Get the map fragment and set up the callback for when the map is ready
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);

        // Set up Fused Location Provider to get user's location
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        fetchLocation();
        // Request location permissions if not already granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }

        setupNavigationButtons();
    }
    private void fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @SuppressLint("MissingPermission")
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    currentLocation = location;
                    LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                    mMap.setMyLocationEnabled(true);
                }
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        fetchLocation();
        // Get user's current location and update the map
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.getLastLocation()
                    .addOnSuccessListener(new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            if (location != null) {
                                LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                                // Move the map's camera to the user's location
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));

                                // Fetch nearby places and add markers to the map
                                fetchNearbyPlaces(location);
                            }
                        }
                    });
        }

        // Set a marker click listener
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                String placeId = (String) marker.getTag();  // Get the place ID from the marker tag

                // Fetch place details using placeId
                List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME, Place.Field.OPENING_HOURS, Place.Field.ADDRESS, Place.Field.PHOTO_METADATAS);
                FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, placeFields);

                placesClient.fetchPlace(request).addOnSuccessListener((response) -> {
                    Place place = response.getPlace();
                    // Pass place details to VenueDetailActivity
                    Intent intent = new Intent(MainActivity.this, VenueDetailActivity.class);
                    intent.putExtra("placeName", place.getName());
                    intent.putExtra("address", place.getAddress());

                    // Format opening hours
                    String openingHoursFormatted = formatOpeningHours(place.getOpeningHours());
                    intent.putExtra("openingHours", openingHoursFormatted);

                    // Fetch and pass the photo metadata
                    if (place.getPhotoMetadatas() != null && !place.getPhotoMetadatas().isEmpty()) {
                        PhotoMetadata photoMetadata = place.getPhotoMetadatas().get(0);  // Get the first photo
                        intent.putExtra("photoMetadata", photoMetadata);
                    }

                    startActivity(intent);
                });

                return false;
            }
        });
    }

    private void setupNavigationButtons() {
        // Home button click event
        ImageView homeButton = findViewById(R.id.nav_home);
        homeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

        // Notification button click event
        ImageView notificationButton = findViewById(R.id.nav_notification);
        notificationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Intent intent = new Intent(MainActivity.this, NotificationActivity.class);
//                startActivity(intent);
            }
        });

        // Profile button click event
        ImageView profileButton = findViewById(R.id.nav_profile);
        profileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, AccountPage.class);
                intent.putExtra("userProfile", currentUserProfile);
                startActivity(intent);
            }
        });
    }



    private String formatOpeningHours (OpeningHours openingHours){
        if (openingHours == null || openingHours.getWeekdayText() == null) {
            return "No opening hours available";
        }
        StringBuilder formattedHours = new StringBuilder();
        for (String dayHours : openingHours.getWeekdayText()) {
            formattedHours.append(dayHours).append("\n");
        }
        return formattedHours.toString();
    }


    private void fetchNearbyPlaces (Location location){
        List<Place.Field> placeFields = Arrays.asList(Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ID, Place.Field.TYPES); // Add Place.TYPES field
        FindCurrentPlaceRequest request = FindCurrentPlaceRequest.newInstance(placeFields);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            placesClient.findCurrentPlace(request).addOnSuccessListener((response) -> {
                for (PlaceLikelihood placeLikelihood : response.getPlaceLikelihoods()) {
                    Place place = placeLikelihood.getPlace();
                    LatLng placeLatLng = place.getLatLng();
                    String placeId = place.getId(); // Get place ID

                    // Filter only bars and night clubs
                    if (place.getTypes().contains(Place.Type.BAR) || place.getTypes().contains(Place.Type.NIGHT_CLUB)) {
                        if (placeLatLng != null) {
                            // Add marker with place ID as tag
                            Marker marker = mMap.addMarker(new MarkerOptions()
                                    .position(placeLatLng)
                                    .title(place.getName()));

                            // Store placeId in marker's tag
                            marker.setTag(placeId);
                        }
                    }
                }
            }).addOnFailureListener((exception) -> {
                Toast.makeText(MainActivity.this, "Failed to get places: " + exception.getMessage(), Toast.LENGTH_LONG).show();
            });
        }

    }

    @Override
    protected void onStart () {
        super.onStart();

        // Check if user is logged in (non-null) and update UI accordingly.
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            // User is not logged in, redirect to the LoginActivity
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();  // Close the MainActivity to prevent the user from returning to it
        } else {
            loadUserDataFromFirestore();
        }
    }

    private void initFirestore(){
        firestore = FirebaseFirestore.getInstance();

    }

    private void testDatabase(){
        HashMap<String, Object> testData = new HashMap<>();
        testData.put("testKey", "testValue");

        CollectionReference testCollection = firestore.collection("testCollection");
        testCollection.add(testData);
    }

    private void loadUserDataFromFirestore() {
        // Show a loading dialog
        ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setMessage("Loading user data...");
        progressDialog.setCancelable(false);
        progressDialog.show();  // Show the progress dialog

        // Reference to the specific user document in the "userProfiles" collection
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DocumentReference userRef = FirebaseFirestore.getInstance().collection("userProfiles").document(userId);

        userRef.get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                // Dismiss the progress dialog when the task completes
                progressDialog.dismiss();

                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (document.exists()) {
                        // Get data from the Firestore document
                        String name = document.getString("name");
                        String username = document.getString("username");
                        String gender = document.getString("gender");
                        Long age = document.getLong("age");
                        String ageString = age != null ? age.toString() : "";
                        String location = document.getString("location");
                        String relationshipStatus = document.getString("relationshipStatus");
                        boolean visible = document.getBoolean("visible");
                        String visibleString = visible ? "Yes" : "No";  // Convert boolean to Yes/No string
                        String preference = document.getString("preference");
                        String photoUrl = document.getString("profilePictureUrl");

                        // Save the data to local file for later use
                        putDataInUserProfile(name, username, gender, ageString, location, relationshipStatus, visibleString, preference, photoUrl);
                    } else {
                        Log.d("Firestore", "No such document");
                    }
                } else {
                    Log.d("Firestore", "get failed with ", task.getException());
                }
            }
        });
    }

    private void putDataInUserProfile(String name, String username, String gender, String age,
                                      String location, String relationshipStatus, String visible,
                                      String preference, String photoUrl) {
        currentUserProfile.setUserId(currentUser.getUid());
        currentUserProfile.setName(name);  // Set user's name
        currentUserProfile.setUsername(username);  // Set user's username
        currentUserProfile.setGender(gender);  // Set user's gender

        // Parse and set the age if it's a valid number
        try {
            currentUserProfile.setAge(Integer.parseInt(age));  // Convert age to integer and set it
        } catch (NumberFormatException e) {
            e.printStackTrace();  // Handle invalid number input for age
        }

        currentUserProfile.setLocation(location);  // Set user's location
        currentUserProfile.setRelationshipStatus(relationshipStatus);  // Set relationship status

        // Convert visible string ("Yes"/"No") to boolean and set it
        currentUserProfile.setVisible(visible.equals("Yes"));  // Set visibility status

        currentUserProfile.setPreferences(preference);  // Set user's preferences
        currentUserProfile.setProfilePictureUrl(photoUrl);

    }
}