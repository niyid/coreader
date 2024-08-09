package com.techducat.coreader

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// This is launched when a session is clicked or a new one created.
class CoreadActivity : AppCompatActivity(), NotificationInterface {

    companion object {
        private const val TAG = "CoreadActivity"
    }

    private lateinit var readerCountTextView: TextView
    private lateinit var pageCountTextView: TextView
    private lateinit var pageTextView: TextView
    private lateinit var readyButton: ImageButton
    private lateinit var titleTextView: TextView

    private lateinit var deviceId: String
    private lateinit var sessionId: String
    private lateinit var title: String
    private lateinit var relayClient: RelayClient
    private var notificationId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coread)

        deviceId = intent.getStringExtra("EXTRA_DEVICE_ID") ?: ""
        sessionId = intent.getStringExtra("EXTRA_SESSION_ID") ?: ""
        title = intent.getStringExtra("EXTRA_TITLE") ?: ""

        relayClient = RelayClient.getInstance()
        relayClient.addNotificationInterface(this)

        readerCountTextView = findViewById(R.id.readerCountTextView)
        pageCountTextView = findViewById(R.id.pageCountTextView)
        pageTextView = findViewById(R.id.pageTextView)
        readyButton = findViewById(R.id.readyButton)
        titleTextView = findViewById(R.id.titleTextView)

        readyButton.setOnClickListener {
            ready()
        }

        readPagesAndParagraphs()
    }

    private fun readPagesAndParagraphs() {
        // Implement logic to read pages and paragraphs
        // For now, this is just a placeholder

        titleTextView.text = title
        if(deviceId.isNotEmpty() &&
            sessionId.isNotEmpty() &&
            readerCountTextView.text.isEmpty() &&
            pageCountTextView.text.isEmpty()) {
            ready()
        }
    }

    private fun ready() {
        Log.i(TAG, "Ready button clicked - $deviceId, $sessionId")
            CoroutineScope(Dispatchers.Main).launch {
                relayClient.setReady(deviceId, sessionId, true)
            }

    }

    override fun notify(message: String) {
        handleMessage(this, message)
    }

    override fun onSessionsUpdated(sessions: List<Session>) {
        Log.i(TAG, "onSessionsUpdated - does nothing")
    }

    override fun onSessionsUpdated(count: Int) {
        readerCountTextView.text = count.toString()
        notify(getString(R.string.readers_update, count))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onSessionsUpdated(count: Int, page: Int, pageCount: Int, pageHtml: String) {
        if(pageCount == page) {
            readyButton.isEnabled = false
            showToast(getString(R.string.book_completed))
        }
        readerCountTextView.text = count.toString()
        pageCountTextView.text = page.toString()
        pageTextView.text = Html.fromHtml(pageHtml, Html.FROM_HTML_MODE_LEGACY)
        notify(getString(R.string.session_update, page))
    }

    private fun playBuzzingSound() {
        val toneGen1 = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        toneGen1.startTone(ToneGenerator.TONE_PROP_BEEP)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(MainActivity.CHANNEL_ID,
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

        val notificationBuilder = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
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
    override fun onResume() {
        super.onResume()
    }
    private fun handleMessage(context: Context, message: String) {
        playBuzzingSound()
        createNotificationChannel(context)
        showNotification(context, message)
    }
}
