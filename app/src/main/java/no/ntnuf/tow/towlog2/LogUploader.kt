package no.ntnuf.tow.towlog2

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import no.ntnuf.tow.towlog2.model.DayLog
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale
import kotlin.concurrent.thread

class LogUploader(
    context: Context,
    private val settings: SharedPreferences
) {
    private val appContext = context.applicationContext
    private val pendingUploadsFilename = "pending_uploads_list"
    private var pendingUploads: PendingUploadsList = loadPendingUploadsList() ?: PendingUploadsList()

    private data class PendingUploadsList(
        val pendingFiles: ArrayList<String> = ArrayList()
    ) : Serializable {
        companion object {
            @JvmField
            val serialVersionUID = 1L
        }
    }

    fun addToUploadQueue(daylog: DayLog) {
        pendingUploads.pendingFiles.add(daylog.getFilename())
        Log.e("LOGUPLOADER", "Added a log file for future upload: ${daylog.getFilename()}")
        savePendingUploadsList()
    }

    fun uploadPending() {
        thread(name = "towlog-upload-pending") {
            val completed = ArrayList<Int>()

            for ((index, pendingFileName) in pendingUploads.pendingFiles.withIndex()) {
                val daylogToUpload = loadDayLogFromFile(pendingFileName)
                if (daylogToUpload != null) {
                    Log.e("LOGUPLOADER", "Found log for pending upload: $pendingFileName")
                    if (uploadOne(daylogToUpload)) {
                        completed.add(index)
                    }
                }
            }

            for (i in completed.size - 1 downTo 0) {
                pendingUploads.pendingFiles.removeAt(completed[i])
            }
            savePendingUploadsList()

            Log.e("LOGUPLOADER", "Uploaded ${completed.size} pending day logs to webserver")
            val message = if (completed.isNotEmpty()) {
                "Uploaded ${completed.size} pending day logs to webserver. Remember to also send via email."
            } else {
                "No internet connection, will upload day log later. Remember to also send via email."
            }

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadDayLogFromFile(filename: String): DayLog? {
        return try {
            appContext.openFileInput(filename).use { fis ->
                ObjectInputStream(fis).use { input ->
                    input.readObject() as DayLog
                }
            }
        } catch (e: ClassNotFoundException) {
            Log.e("LOGUPLOADER", "Upload failed to deserialize: $filename", e)
            null
        } catch (e: FileNotFoundException) {
            Log.e("LOGUPLOADER", "Upload file not found: $filename", e)
            null
        } catch (e: IOException) {
            Log.e("LOGUPLOADER", "Upload failed to load file: $filename", e)
            null
        }
    }

    private fun savePendingUploadsList() {
        try {
            appContext.openFileOutput(pendingUploadsFilename, Context.MODE_PRIVATE).use { fos ->
                ObjectOutputStream(fos).use { output ->
                    output.writeObject(pendingUploads)
                }
            }
        } catch (e: FileNotFoundException) {
            Log.e("LOGUPLOADER", "Pending uploads save, file not found: $pendingUploadsFilename", e)
        } catch (e: IOException) {
            Log.e("LOGUPLOADER", "Pending uploads save, IO exception: $pendingUploadsFilename", e)
        }
    }

    private fun loadPendingUploadsList(): PendingUploadsList? {
        return try {
            appContext.openFileInput(pendingUploadsFilename).use { fis ->
                ObjectInputStream(fis).use { input ->
                    input.readObject() as PendingUploadsList
                }
            }
        } catch (e: ClassNotFoundException) {
            Log.e("LOGUPLOADER", "Pending uploads load, class mismatch", e)
            null
        } catch (e: FileNotFoundException) {
            Log.e("LOGUPLOADER", "Pending uploads load, file not found: $pendingUploadsFilename", e)
            null
        } catch (e: IOException) {
            Log.e("LOGUPLOADER", "Pending uploads load, IO exception: $pendingUploadsFilename", e)
            null
        }
    }

    private fun uploadOne(daylog: DayLog): Boolean {
        val dateString = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(daylog.date)
        val tempJsonFile = File(appContext.filesDir, "tmp_daylog.json")

        try {
            appContext.openFileOutput(tempJsonFile.name, Context.MODE_PRIVATE).use { fos ->
                fos.write(daylog.getJSONOutput().toByteArray(Charsets.UTF_8))
            }
        } catch (e: IOException) {
            Log.e("LOGUPLOADER", "Failed to create temp JSON file", e)
            return false
        }

        val requestUrl = settings.getString("upload_log_url", "NO_URL_PROVIDED")
            ?.trim()
            .orEmpty()
        if (requestUrl.isBlank() || requestUrl == "NO_URL_PROVIDED") {
            Log.e("LOGUPLOADER", "Upload URL is not configured")
            return false
        }

        val username = settings.getString("upload_log_username", null)
        val password = settings.getString("upload_log_password", null)
        val userCredentials = if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            "$username:$password"
        } else {
            null
        }

        val gpxFiles = daylog.tows.mapNotNull { tow ->
            tow.gpx_filename?.let { File(appContext.filesDir, it) }
        }.filter { it.exists() }

        return try {
            val multipart = MultipartUploader(
                requestUrl = requestUrl,
                charset = Charsets.UTF_8.name(),
                userCredentialsOrNull = userCredentials
            )

            multipart.addFormField("date", dateString)
            multipart.addFilePart("logfile", tempJsonFile)
            multipart.addFormField("n_tracks", gpxFiles.size.toString())

            gpxFiles.forEachIndexed { index, track ->
                multipart.addFilePart("file_$index", track)
                multipart.addFormField("filename_$index", track.name)
            }

            val response = multipart.finish()
            Log.e("LOGUPLOADER", "Completed uploading one day, response: $response")
            true
        } catch (e: IOException) {
            Log.e("LOGUPLOADER", "Failed to upload one day", e)
            false
        }
    }

    private class MultipartUploader(
        requestUrl: String,
        private val charset: String,
        userCredentialsOrNull: String?
    ) {
        private val boundary = "-----${System.currentTimeMillis()}----------"
        private val lineFeed = "\r\n"
        private val connection: HttpURLConnection
        private val outputStream: OutputStream
        private val writer: PrintWriter

        init {
            connection = URL(requestUrl).openConnection() as HttpURLConnection
            connection.useCaches = false
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.requestMethod = "POST"
            if (!userCredentialsOrNull.isNullOrBlank()) {
                val encoded = Base64.encodeToString(
                    userCredentialsOrNull.toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
                connection.setRequestProperty("Authorization", "Basic $encoded")
            }
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("User-Agent", "TowLog Android")

            outputStream = connection.outputStream
            writer = PrintWriter(OutputStreamWriter(outputStream, charset), true)
        }

        fun addFormField(name: String, value: String) {
            writer.append("--").append(boundary).append(lineFeed)
            writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"")
                .append(lineFeed)
            writer.append("Content-Type: text/plain; charset=").append(charset).append(lineFeed)
            writer.append(lineFeed)
            writer.append(value).append(lineFeed)
            writer.flush()
        }

        fun addFilePart(fieldName: String, uploadFile: File) {
            val fileName = uploadFile.name
            writer.append("--").append(boundary).append(lineFeed)
            writer.append("Content-Disposition: form-data; name=\"")
                .append(fieldName)
                .append("\"; filename=\"")
                .append(fileName)
                .append("\"")
                .append(lineFeed)
            writer.append("Content-Type: ")
                .append(URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream")
                .append(lineFeed)
            writer.append("Content-Transfer-Encoding: binary").append(lineFeed)
            writer.append(lineFeed)
            writer.flush()

            FileInputStream(uploadFile).use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) {
                        break
                    }
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            outputStream.flush()
            writer.append(lineFeed)
            writer.flush()
        }

        fun finish(): String {
            writer.append(lineFeed).flush()
            writer.append("--").append(boundary).append("--").append(lineFeed)
            writer.close()

            val status = connection.responseCode
            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    buildString {
                        var line: String?
                        while (true) {
                            line = reader.readLine() ?: break
                            append(line)
                        }
                    }
                }
            } else {
                ""
            }

            connection.disconnect()
            if (status !in 200..299) {
                throw IOException("Server returned non-OK status: $status body=$response")
            }
            return response
        }
    }
}
