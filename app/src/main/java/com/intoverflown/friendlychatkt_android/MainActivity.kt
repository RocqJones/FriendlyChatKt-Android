package com.intoverflown.friendlychatkt_android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.database.FirebaseListAdapter
import com.firebase.ui.database.FirebaseListOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import timber.log.Timber
import java.util.*
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {

    companion object {
        const val ANONYMOUS = "anonymous"
        const val DEFAULT_MSG_LENGTH_LIMIT = 10
        const val RC_SIGN_IN = 1
        const val RC_PHOTO_PICKER = 2
        const val FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length"
    }

    private lateinit var mListView: ListView
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mPhotoPickerButton: ImageButton
    private lateinit var mMessageEditText: EditText
    private lateinit var mSendButton: Button

    private var mUsername: String = ANONYMOUS

    private lateinit var mFirebaseDatabase: FirebaseDatabase
    private lateinit var mMessagesDatabaseReference: DatabaseReference
    private lateinit var mFirebaseAuth: FirebaseAuth
    private lateinit var mAuthStateListener: FirebaseAuth.AuthStateListener

    private lateinit var mFirebaseStorage: FirebaseStorage
    private lateinit var mChatPhotosStoreageReference: StorageReference

    private lateinit var mFirebaseRemoteConfig: FirebaseRemoteConfig

    private var mAdapter: FirebaseListAdapter<FriendlyMessage>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mUsername = ANONYMOUS

        mFirebaseDatabase = FirebaseDatabase.getInstance()
        mFirebaseAuth = FirebaseAuth.getInstance()
        mFirebaseStorage = FirebaseStorage.getInstance()
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

        mMessagesDatabaseReference = mFirebaseDatabase.reference.child("messages")
        mChatPhotosStoreageReference = mFirebaseStorage.reference.child("chat_photos")

        // Initialize references to views
        mProgressBar = findViewById(R.id.progressBar)
        mListView = findViewById(R.id.messageListView)
        mPhotoPickerButton = findViewById(R.id.photoPickerButton)
        mMessageEditText = findViewById(R.id.messageEditText)
        mSendButton = findViewById(R.id.sendButton)

        // Initialize progress bar
        mProgressBar.visibility = ProgressBar.INVISIBLE

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/jpeg"
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER)
        }

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                mSendButton.isEnabled = charSequence.toString().trim { it <= ' ' }.isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        mMessageEditText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT))

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener {
            val friendlyMessage = FriendlyMessage(mMessageEditText.text.toString(), mUsername, null)

            mMessagesDatabaseReference.push().setValue(friendlyMessage)

            // Clear input box
            mMessageEditText.setText("")
        }

        mAuthStateListener = FirebaseAuth.AuthStateListener { p0 ->
            val user = p0.currentUser
            if (user != null) {
//                Toast.makeText(this@MainActivity, "You're signed in!", Toast.LENGTH_LONG).show()
                onSignInInitialize(user.displayName)
            } else {
                onSignOutCleanUp()
                startActivityForResult(AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setIsSmartLockEnabled(false)
                        .setAvailableProviders(
                                listOf(
                                        AuthUI.IdpConfig.EmailBuilder().build(),
                                        AuthUI.IdpConfig.GoogleBuilder().build()
                                ))
                        .build(),
                        RC_SIGN_IN)
            }
        }

        val configSetting = FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(3600L).build()
        mFirebaseRemoteConfig.setConfigSettingsAsync(configSetting)

        val defaultConfigMap = HashMap<String, Any>()
        defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT)
        mFirebaseRemoteConfig.setDefaultsAsync(defaultConfigMap)
        fetchConfig()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Signed in!", Toast.LENGTH_SHORT).show()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Sing in cancelled", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == Activity.RESULT_OK) {
            val selectedImageUri = data!!.data
            val photoRef = selectedImageUri?.lastPathSegment?.let {
                mChatPhotosStoreageReference.child(
                        it
                )
            }
            if (selectedImageUri != null) {
                photoRef!!.putFile(selectedImageUri).addOnSuccessListener { taskSnapshot ->
                    val downloadUri = taskSnapshot.storage.downloadUrl
                    val friendlyMessage = FriendlyMessage(null, mUsername, downloadUri.toString())
                    mMessagesDatabaseReference.push().setValue(friendlyMessage)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mFirebaseAuth.addAuthStateListener(mAuthStateListener)
    }

    override fun onPause() {
        super.onPause()
        mFirebaseAuth.removeAuthStateListener(mAuthStateListener)
    }

    override fun onDestroy() {
        super.onDestroy()
//        mAdapter.cleanup()
        mListView.adapter = null
        mAdapter = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    private fun onSignInInitialize(username: String?) {

//        val options: FirebaseListOptions<ChatMessage> = FirebaseListOptions.Builder<ChatMessage>().setQuery(query,
//                ChatMessage::class.java).setLayout(android.R.layout.simple_list_item_1).build()

        mUsername = username ?: ANONYMOUS

        // Initialize message ListView and its adapter
//        mAdapter = object : FirebaseListAdapter<FriendlyMessage>(
//                this,
//                FriendlyMessage::class.java,
//                R.layout.item_message,
//                mMessagesDatabaseReference,
//        )
        val query: Query = mMessagesDatabaseReference

        val options : FirebaseListOptions<FriendlyMessage> = FirebaseListOptions.Builder<FriendlyMessage>().setQuery(query, FriendlyMessage::class.java).setLayout(R.layout.item_message).build()
        mAdapter = object : FirebaseListAdapter<FriendlyMessage>(options){
            public override fun populateView(view: View, message: FriendlyMessage, position: Int) {
                val photoImageView = view.findViewById<ImageView>(R.id.photoImageView)
                val messageTextView = view.findViewById<TextView>(R.id.messageTextView)
                val authorTextView = view.findViewById<TextView>(R.id.nameTextView)

                val isPhoto = message.photoUrl != null
                if (isPhoto) {
                    messageTextView.visibility = View.GONE
                    photoImageView.visibility = View.VISIBLE

                    Glide.with(photoImageView.context)
                            .load(message.photoUrl)
                            .into(photoImageView)
                } else {
                    messageTextView.visibility = View.VISIBLE
                    photoImageView.visibility = View.GONE
                    messageTextView.text = message.text
                }
                authorTextView.text = message.name
            }
        }
        mListView.adapter = mAdapter
    }

    private fun onSignOutCleanUp() {
        mUsername = ANONYMOUS
//        mAdapter?.cleanup()
        mListView.adapter = null
        mAdapter = null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sign_out_menu -> {
                AuthUI.getInstance().signOut(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun fetchConfig() {
        val cacheExpiration = 3600L

//        if (mFirebaseRemoteConfig.info.configSettings.isDeveloperModeEnabled()) {
//            cacheExpiration = 0L
//        }

        mFirebaseRemoteConfig.fetch(cacheExpiration)
                .addOnSuccessListener {
                    mFirebaseRemoteConfig.fetchAndActivate()
                    applyRetrieveLengthLimit()
                }
                .addOnFailureListener { exception ->
                    Timber.w(exception, "Error fetching config")
                    applyRetrieveLengthLimit()
                }
    }

    private fun applyRetrieveLengthLimit() {
        val friendly_msg_length = mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY)
        mMessageEditText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(friendly_msg_length.toInt()))
        Timber.d("%s = %d", FRIENDLY_MSG_LENGTH_KEY, friendly_msg_length)
    }
}