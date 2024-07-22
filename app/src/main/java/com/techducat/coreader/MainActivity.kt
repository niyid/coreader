package com.example.coreader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.techducat.coreader.R
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okio.IOException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private val PICK_PDF_REQUEST = 1
    private var webSocket: WebSocket? = null
    private lateinit var textView: TextView
    private lateinit var uploadButton: Button
    private lateinit var readyButton: Button
    private lateinit var startButton: Button
    private var isReady = false
    private var currentPage = 0
    private var currentParagraph = 0
    private var pdfFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textView = findViewById(R.id.textView)
        uploadButton = findViewById(R.id.uploadButton)
        readyButton = findViewById(R.id.readyButton)
        startButton = findViewById(R.id.startButton)

        setupWebSocket()

        uploadButton.setOnClickListener { openFilePicker() }
        readyButton.setOnClickListener {
            isReady = !isReady
            sendReadyState()
        }
        startButton.setOnClickListener {
            if (pdfFile != null) {
                startCoreadingSession()
            } else {
                Toast.makeText(this, "Please upload a PDF first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/pdf"
        }
        startActivityForResult(intent, PICK_PDF_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PDF_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val uri: Uri? = data.data
            uri?.let {
                pdfFile = File(cacheDir, "uploaded.pdf")
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        FileOutputStream(pdfFile).use { outputStream ->
                            val buffer = ByteArray(1024)
                            var length: Int
                            while (inputStream.read(buffer).also { length = it } > 0) {
                                outputStream.write(buffer, 0, length)
                            }
                        }
                    }
                    uploadPdf(pdfFile!!)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun uploadPdf(file: File) {
        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name,
                file.asRequestBody("application/pdf".toMediaTypeOrNull())
            )
            .build()
        val request = Request.Builder()
            .url("http://localhost:8080/upload")
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "PDF uploaded successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun setupWebSocket() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://localhost:8765").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.getString("type") == "turn_paragraph") {
                        val page = json.getInt("page")
                        val paragraph = json.getInt("paragraph")
                        loadParagraph(page, paragraph)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun sendReadyState() {
        val json = JSONObject()
        try {
            json.put("type", "ready")
            json.put("ready", isReady)
            json.put("page", currentPage)
            json.put("paragraph", currentParagraph)
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startCoreadingSession() {
        // Implement the logic to start the coreading session
        // Notify peers that the session is starting
        val json = JSONObject()
        try {
            json.put("type", "start_session")
            json.put("page", currentPage)
            json.put("paragraph", currentParagraph)
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadParagraph(page: Int, paragraph: Int) {
        currentPage = page
        currentParagraph = paragraph

        // Implement the logic to load the specific paragraph text for the given page and paragraph number
        val paragraphText = getParagraphText(page, paragraph)
        runOnUiThread { textView.text = paragraphText }
    }

    private fun getParagraphText(page: Int, paragraph: Int): String {
        // Placeholder method to fetch paragraph text
        return "Page $page, Paragraph $paragraph"
    }

    override fun onDestroy() {
        webSocket?.close(1000, null)
        super.onDestroy()
    }
}
