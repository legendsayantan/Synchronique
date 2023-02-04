package com.legendsayantan.sync

import EncryptionManager
import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.legendsayantan.sync.fragments.ConnectionFragment
import com.legendsayantan.sync.fragments.HomeFragment
import com.legendsayantan.sync.fragments.LoginFragment
import com.legendsayantan.sync.interfaces.EndpointInfo
import com.legendsayantan.sync.services.AdvertiserService
import com.legendsayantan.sync.services.DiscoverService
import com.legendsayantan.sync.services.MediaService
import com.legendsayantan.sync.services.SingularConnectionService
import com.legendsayantan.sync.views.AppDialog
import me.ibrahimsn.lib.SmoothBottomBar
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {
    private var bottomAppBar: SmoothBottomBar? = null
    private lateinit var firebaseAuth: FirebaseAuth
    private val RC_SIGN_IN = 1001


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(resources.getColor(R.color.accent1_600)))
        bottomAppBar = findViewById(R.id.bottomBar);
        firebaseAuth = Firebase.auth
    }

    override fun onResume() {
        super.onResume()
        instance = this
        if (Firebase.auth.currentUser == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerView, LoginFragment()).commit()
        } else {
            checkPermissions()
            loginSuccess()

        }
    }

    override fun onPause() {
        super.onPause()
        instance = null
        stopService(Intent(this, DiscoverService::class.java))
        if(SingularConnectionService.CONNECTED){
            stopService(Intent(this, AdvertiserService::class.java))
        }else{
            startForegroundService(Intent(this, AdvertiserService::class.java))
        }
    }

    private fun loginSuccess() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainerView, HomeFragment()).commit()
        supportActionBar?.title = getString(R.string.app_name);
        bottomAppBar?.itemActiveIndex = 0
        bottomAppBar?.onItemSelected = {
            when (it) {
                0 -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, HomeFragment()).commit()
                    supportActionBar?.title = getString(R.string.app_name);
                }
                1 -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, ConnectionFragment()).commit()
                    supportActionBar?.title = getString(R.string.menu_link);
                }
                2 -> {
                    //supportFragmentManager.beginTransaction().replace(R.id.fragment_container, HomeFragment()).commit()
                }
            }
        }
        startFromNotification(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: Exception) {
                // Google Sign In failed, update UI appropriately
                Toast.makeText(this, "Google Sign In failed", Toast.LENGTH_SHORT).show()
                println("-------------ERROR----------------")
                e.printStackTrace()
                println("-------------ERROR----------------")
            }
        }
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, OnCompleteListener<AuthResult?> { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val user: FirebaseUser? = firebaseAuth.currentUser
                    firebaseAuth.currentUser?.getIdToken(true)?.addOnCompleteListener {
                        getSharedPreferences("default", MODE_PRIVATE).edit()
                            .putString("authtoken", it.result.token).apply()
                    }
                    EncryptionManager.fetchDynamicKey({
                        loginSuccess()
                    }, {})
                } else {
                    // If sign in fails, display a message to the user.
                    Toast.makeText(this, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            })
    }

    fun googleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.web_client_id)).requestEmail().build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    fun signOutDialog() {

    }

    fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)).requestEmail().build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnCompleteListener(this) {
            firebaseAuth.signOut()
            exitProcess(0)
        }
    }

    fun askLocationPermission() {
        requestPermissions(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ), 100
        )
    }

    fun askBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_ADVERTISE,
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ), 101
            )
        } else {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_ADMIN
                ), 101
            )
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationAccess = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.BLUETOOTH_ADVERTISE
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAccess = true
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.BLUETOOTH_ADMIN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAccess = true
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    fun startFromNotification(intent: Intent) {
        if (intent.hasExtra("endpointName")) {
            val x = intent.getStringExtra("endpointName").toString().split("_")
            var endpoint =
                EndpointInfo(intent.getStringExtra("endpointId").toString(), x[0], x[1], null)
            println("------------- x ----------------")
            println(x)
            println("------------- x ----------------")

            AppDialog(this,
                "Request Details",
                endpoint.name.toString(),
                endpoint.uidHash,
                "Accept",
                arrayFrom(x[2]),
                { ints: ArrayList<Int>, appDialog: AppDialog ->
                    if(ints.contains(0)){
                        if(!NotificationManagerCompat.getEnabledListenerPackages(this).contains(this.packageName)){
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            Toast.makeText(this, "Please enable notification access", Toast.LENGTH_SHORT).show();
                            return@AppDialog
                        }
                        if(getSharedPreferences("default", MODE_PRIVATE).getBoolean("streamMedia", false)){
                            if (ActivityCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.RECORD_AUDIO
                                ) != PackageManager.PERMISSION_GRANTED
                            ){
                                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 102)
                                return@AppDialog
                            }
                        }
                        MediaService.streamMode = getSharedPreferences("default", MODE_PRIVATE).getBoolean("streamMedia", false);
                    }
                    SingularConnectionService.connectionUpdate =
                        { if (SingularConnectionService.CONNECTED) appDialog.hide(); }
                    SingularConnectionService.ENDPOINT_ID = endpoint.endpointId
                    SingularConnectionService.ENDPOINT_NAME = endpoint.name.toString()
                    SingularConnectionService.CONNECTION_MODE =
                        SingularConnectionService.Companion.ConnectionMode.ACCEPT
                    SingularConnectionService.ENDPOINT_HASH = endpoint.uidHash
                    SingularConnectionService.ACCESS = ints
                    val intent = Intent(applicationContext, SingularConnectionService::class.java)
                    startForegroundService(intent)
                },
                { Nearby.getConnectionsClient(this).rejectConnection(endpoint.endpointId) }).show()
        }
    }
    fun arrayFrom(str: String): ArrayList<Int>{
        var data = str
        if(data.startsWith("[")&&data.endsWith("]")){
            data = data.substring(1,data.lastIndex-1)
        }
        var splits = data.split(",");
        var out = ArrayList<Int>()
        for (s in splits){
            out.add(s.toInt())
        }
        return out
    }
    companion object {
        var bluetoothAccess: Boolean = false
        var locationAccess: Boolean = false
        var instance: MainActivity? = null
        fun isLocationEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return LocationManagerCompat.isLocationEnabled(manager)
        }
    }
}