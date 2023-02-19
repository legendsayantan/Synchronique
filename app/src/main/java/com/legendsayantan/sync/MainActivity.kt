package com.legendsayantan.sync

import EncryptionManager
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.location.LocationManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.legendsayantan.sync.fragments.ExploreFragment
import com.legendsayantan.sync.fragments.ConnectionFragment
import com.legendsayantan.sync.fragments.HomeFragment
import com.legendsayantan.sync.fragments.LoginFragment
import com.legendsayantan.sync.interfaces.EndpointInfo
import com.legendsayantan.sync.services.*
import com.legendsayantan.sync.views.ConnectionDialog
import com.legendsayantan.sync.workers.CardAnimator
import com.legendsayantan.sync.workers.Values
import com.legendsayantan.sync.workers.Values.Companion.REQUEST_CODE_CAPTURE_PERM
import me.ibrahimsn.lib.SmoothBottomBar


class MainActivity : AppCompatActivity() {
    private var bottomAppBar: SmoothBottomBar? = null
    private lateinit var firebaseAuth: FirebaseAuth
    private val RC_SIGN_IN = 1001
    lateinit var cardAnimator: CardAnimator
    lateinit var values : Values
    var homeFragment = HomeFragment()
    var connectionFragment = ConnectionFragment()
    var exploreFragment = ExploreFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cardAnimator = CardAnimator(this)
        supportActionBar?.setBackgroundDrawable(ColorDrawable(window.statusBarColor))
        bottomAppBar = findViewById(R.id.bottomBar);
        firebaseAuth = Firebase.auth
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        values = Values(this)
        if (Firebase.auth.currentUser == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerView, LoginFragment()).commit()
        } else {
            checkPermissions()
            loginSuccess()
        }
    }
    override fun onResume() {
        super.onResume()
        instance = this
        checkPermissions()
        HomeFragment.latestInstance?.refreshFragment()
    }

    override fun onPause() {
        super.onPause()
        instance = null
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
                        .replace(R.id.fragmentContainerView, homeFragment).commit()
                    supportActionBar?.title = getString(R.string.app_name);
                }
                1 -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, connectionFragment).commit()
                    supportActionBar?.title = getString(R.string.menu_link);
                }
                2 -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerView, exploreFragment).commit()
                    supportActionBar?.title = getString(R.string.menu_allow);
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
        if (requestCode == REQUEST_CODE_CAPTURE_PERM) {
            if (resultCode == Activity.RESULT_OK) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (data != null) {
                        ServerService.mediaProjectionManager = mediaProjectionManager
                        ServerService.resultCode = resultCode
                        ServerService.data = data
                        Toast.makeText(applicationContext,"Stream will be started.",Toast.LENGTH_LONG).show()
                    }else{
                        Toast.makeText(applicationContext,"NULL",Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                stopService(Intent(this,WaitForConnectionService::class.java))
                stopService(Intent(this,ServerService::class.java))
                Values.appState = Values.Companion.AppState.IDLE
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
                    user?.getIdToken(true)?.addOnCompleteListener {
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

    fun signOut() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.web_client_id)).requestEmail().build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInClient.signOut().addOnSuccessListener{
            firebaseAuth.signOut().also {
                finishAndRemoveTask()
            }
        }
    }

    fun askLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), 100
        )
    }

    fun askBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), 101
            )
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN
                ), 101
            )
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationAccess = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_ADVERTISE
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothAccess = true
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_ADMIN
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
            val endpoint =
                EndpointInfo(intent.getStringExtra("endpointId").toString(), x[0], x[1], null)

            ConnectionDialog(this,
                "Connection Details",
                endpoint.name.toString(),
                endpoint.uidHash,
                "Accept",
                {
                    print("------------- endpoint ----------------")
                    ServerService.instance!!.acceptConnection(endpoint)
                    Values.onClientAdded = { it.d.dismiss()}
                },
                { Nearby.getConnectionsClient(this).rejectConnection(endpoint.id) }).show()
        }
    }

/*    fun arrayFrom(str: String): ArrayList<Int> {
        var data = str
        if (data.startsWith("[") && data.endsWith("]")) {
            data = data.substring(1, data.lastIndex - 1)
        }
        var splits = data.split(",");
        var out = ArrayList<Int>()
        for (s in splits) {
            out.add(s.toInt())
        }
        return out
    }*/

    companion object {
        var bluetoothAccess: Boolean = false
        var locationAccess: Boolean = false
        var instance: MainActivity? = null
        lateinit var mediaProjectionManager : MediaProjectionManager

        fun isLocationEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return LocationManagerCompat.isLocationEnabled(manager)
        }
    }
}