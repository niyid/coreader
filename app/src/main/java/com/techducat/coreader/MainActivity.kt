package com.techducat.coreader

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hbb20.CountryCodePicker
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
data class Session(val session_id: String, val title: String)

data class SessionResponse(val type: String, val sessions: List<Session>)

class MainActivity : AppCompatActivity(), NotificationInterface, CellphoneAcquisitionListener {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var telephoneButton: ImageButton
    private lateinit var createSessionButton: ImageButton
    private lateinit var relayClient: RelayClient
    private lateinit var deviceId: String
    private lateinit var sessionId: String
    private lateinit var sessionRecyclerView: RecyclerView
    private lateinit var sessionAdapter: SessionAdapter
    private lateinit var uploadFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var pdfUri: Uri
    private val sessions = mutableListOf<Session>()
    private var notificationId = 0
    private var policyAccepted = false

    //Start and telephone buttons display first
    //When start button is clicked, then ready and upload buttons are displayed
    //TODO Implement when a session is created and launched or an existing one is joined
    companion object {
        private const val TAG = "MainActivity"
        const val CHANNEL_ID = "message_channel"
        private const val PREFS_NAME = "com.techducat.coreader.Prefs"
        private const val KEY_CELLPHONE = "key_cellphone"
        private const val KEY_DEVICEID = "key_deviceid"
        private const val KEY_POLICY_ACCEPT = "key_policy_accept"
        private val PHONE_INTER_REGEX = Regex("^\\+?[1-9]\\d{1,14}\$")

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        telephoneButton = findViewById(R.id.telephoneButton)
        sessionRecyclerView = findViewById(R.id.sessionRecyclerView)
        createSessionButton = findViewById(R.id.createSessionButton)

        relayClient = RelayClient.getInstance()
        relayClient.addNotificationInterface(this)

        createSessionButton.setOnClickListener {
            showCreateSessionDialog()
        }

        telephoneButton.setOnClickListener {
            displayAcquireCellphonePopupDialog(this)
        }

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        policyAccepted = sharedPreferences.getBoolean(KEY_POLICY_ACCEPT, false)

        if (!policyAccepted) {
            displayDisclosureDialog()
        } else {
            continueWithAppInitialization()
        }
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        uploadFileLauncher.launch(intent)
    }

    private fun handleFileUpload(uri: Uri) {
        // Obtain the file from the URI and start the upload process
        Log.i(TAG, "File upload URI: $uri")
        pdfUri = uri
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onDestroy() {
        super.onDestroy()
        GlobalScope.launch(Dispatchers.IO) {
            relayClient.unregister()
        }
    }

    override fun notify(message: String) {
        handleMessage(this, message)
    }
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.title_notification_name), importance).apply {
                description = getString(R.string.title_notification_description)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, message: String) {
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE)

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.title_notification))
            .setContentText(message)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            notify(notificationId++, notificationBuilder.build())
        }
    }

    private fun playBuzzingSound() {
        val toneGen1 = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen1.startTone(ToneGenerator.TONE_PROP_BEEP)
    }

    private fun handleMessage(context: Context, message: String) {
        playBuzzingSound()
        createNotificationChannel(context)
        showNotification(context, message)
    }


    private fun displayDisclosureDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_disclosure, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        val disclosureTextView = dialogView.findViewById<TextView>(R.id.disclosureTextView)
        disclosureTextView.text = Html.fromHtml(getString(R.string.disclosure_text),
            Html.FROM_HTML_MODE_LEGACY)
        val okButton = dialogView.findViewById<Button>(R.id.okButton)
        okButton.setOnClickListener {
            dialog.dismiss()
            displayPolicyDialog()
        }

        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        cancelButton.setOnClickListener {
            finish()
        }
        cancelButton.setBackgroundColor(ContextCompat.getColor(this, R.color.red))

        dialog.show()
    }

    private fun displayPolicyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_privacy_policy, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        val policyTextView = dialogView.findViewById<TextView>(R.id.policyTextView)
        policyTextView.text = Html.fromHtml(getString(R.string.privacy_policy_text),
            Html.FROM_HTML_MODE_LEGACY)

        val policyCheckbox = dialogView.findViewById<CheckBox>(R.id.policyCheckbox)
        policyCheckbox.setOnClickListener {
            policyAccepted = policyCheckbox.isChecked
            sharedPreferences.edit().putBoolean(KEY_POLICY_ACCEPT, policyCheckbox.isChecked).apply()
        }

        val okButton = dialogView.findViewById<Button>(R.id.okButton)
        okButton.setOnClickListener {
            if (policyAccepted) {
                dialog.dismiss()
                continueWithAppInitialization()
            } else {
                showToast(getString(R.string.accept_privacy_policy))
            }
        }

        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        cancelButton.setOnClickListener {
            finish()
        }
        cancelButton.setBackgroundColor(ContextCompat.getColor(this, R.color.red))

        dialog.show()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun continueWithAppInitialization() {
        deviceId = sharedPreferences.getString(KEY_DEVICEID, "").toString()
        if (deviceId.isEmpty()) {
            displayAcquireCellphonePopupDialog(this)
        }
        val serviceIntent = Intent(this, WebSocketService::class.java)
        startService(serviceIntent)

        sessionAdapter = SessionAdapter(sessions) { session ->
            GlobalScope.launch(Dispatchers.IO) {
                sessionId = session.session_id
                relayClient.joinSession(deviceId, sessionId)
            }
        }

        sessionRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = sessionAdapter
        }

        createSessionButton.setOnClickListener {
            showCreateSessionDialog()
        }


        uploadFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
            Log.i(TAG, "result => $result")
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    handleFileUpload(uri)
                }
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            relayClient.listSessions()
        }
    }

    private fun generateSessionId(): String {
        return UUID.randomUUID().toString()
    }

    private fun generateDeviceId(cellphone: String?): String {
        Log.i(TAG, "generateDeviceId: $cellphone")
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedBytes = if (cellphone != null) {
            digest.digest(cellphone.toByteArray())
        } else {
            digest.digest(System.currentTimeMillis().toString().toByteArray())
        }
        val stringBuilder = StringBuilder()
        for (byte in hashedBytes) {
            stringBuilder.append(String.format("%02x", byte))
        }
        return stringBuilder.toString()

    }
    private fun displayAcquireCellphonePopupDialog(listener: CellphoneAcquisitionListener) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_acquire_cellphone, null)
        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.ok), null) // Initialize button with null listener
            .create()

        val countryCodePicker = dialogView.findViewById<CountryCodePicker>(R.id.countryCodePicker)
        val cellphoneEditText = dialogView.findViewById<EditText>(R.id.cellphoneEditText)

        val savedCellPhone = sharedPreferences.getString(KEY_CELLPHONE, "")
        if(savedCellPhone?.isNotEmpty() == true) {
            setCellPhone(savedCellPhone, countryCodePicker, cellphoneEditText)
        }

        cellphoneEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val phoneNumber = s.toString()
                val isValidPhoneNumber = validatePhoneNumber(phoneNumber)
                dialogBuilder.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = isValidPhoneNumber
            }
        })

        dialogBuilder.setOnShowListener {
            dialogBuilder.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // Retrieve the selected country code and cellphone number
                val countryCode = countryCodePicker.selectedCountryCode
                val phoneNumber = cellphoneEditText.text.toString()
                // Save the cellphone number if not empty
                val cellphone = if (phoneNumber.isNotBlank()) {
                    "+$countryCode$phoneNumber"
                } else {
                    null
                }
                cellphone?.let { listener.onCellphoneAcquired(it) }
                dialogBuilder.dismiss()
                restartActivity()
            }
        }

        dialogBuilder.show()
    }

    private fun restartActivity() {
        val intent = intent
        finish()
        startActivity(intent)
    }

    private fun setCellPhone(cellPhoneStr: String,
                             countryCodePicker: CountryCodePicker,
                             cellphoneEditText: EditText
    ) {
        try {
            val phoneNumberUtil = PhoneNumberUtil.createInstance(applicationContext)
            val parsedPhoneNumber = phoneNumberUtil.parse(cellPhoneStr, null)

            val countryCode = parsedPhoneNumber.countryCode
            countryCodePicker.setCountryForPhoneCode(countryCode)
            val localNumber = cellPhoneStr.replace("+$countryCode", "")
            Log.d(TAG, "countryCode: $countryCode, localNumber: $localNumber")

            cellphoneEditText.setText(localNumber)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun validatePhoneNumber(phoneNumber: String): Boolean {
        return phoneNumber.matches(PHONE_INTER_REGEX)
    }

    override fun onCellphoneAcquired(cellphone: String) {
        Log.d("MainActivity", "Acquired cellphone: $cellphone")

        // validate and format cellphone
        val editor = sharedPreferences.edit()
        editor.putString(KEY_CELLPHONE, cellphone)
        editor.apply()
        deviceId = generateDeviceId(cellphone)
        editor.putString(KEY_DEVICEID, deviceId)
        editor.apply()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun showCreateSessionDialog() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.dialog_create_session, null)
        val titleEditText = view.findViewById<EditText>(R.id.titleEditText)
        val uploadPdfButton = view.findViewById<ImageButton>(R.id.uploadPdfButton)

        uploadPdfButton.setOnClickListener {
            pickFile()
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.create_session))
            .setView(view)
            .setPositiveButton(getString(R.string.create)) { dialog, _ ->
                val title = titleEditText.text.toString()
                if (title.isNotEmpty()) {
                    sessionId = generateSessionId()
                    GlobalScope.launch(Dispatchers.IO) {
                        if(validateSession()) {
                            relayClient.createSession(deviceId, sessionId, title, pdfUri)
                        } else {
                            runOnUiThread {
                                showToast(getString(R.string.create_session_error))
                            }
                        }
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .show()
    }

    private fun validateSession(): Boolean {
        var flag = false
        if(deviceId.isNotEmpty() && sessionId.isNotEmpty() && sessionId.isNotEmpty() && ::pdfUri.isInitialized) {
            flag = true
        }
        return flag
    }

    override fun onSessionsUpdated(sessions: List<Session>) {
        runOnUiThread {
            sessionAdapter.updateSessions(sessions)
        }
    }

    override fun onSessionsUpdated(count: Int, page: Int, pageCount: Int, pageHtml: String) {
        Log.i(TAG, "onSessionsUpdated - does nothing")
    }

    override fun onSessionsUpdated(count: Int) {
        Log.i(TAG, "onSessionsUpdated - does nothing")
    }

    fun launchCoreadActivity(title: String) {
        try {
            Log.i(TAG, "Launching CoreadActivity")
            val intent = Intent(this, CoreadActivity::class.java)

            // Optional: Add any extra data you want to pass to CoreadActivity
            intent.putExtra("EXTRA_SESSION_ID", sessionId)
            intent.putExtra("EXTRA_DEVICE_ID", deviceId)
            intent.putExtra("EXTRA_TITLE", title)

            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
