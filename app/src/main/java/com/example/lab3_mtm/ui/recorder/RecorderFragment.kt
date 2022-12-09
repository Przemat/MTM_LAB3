package com.example.lab3_mtm.ui.recorder

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.lab3_mtm.R
import com.example.lab3_mtm.databinding.FragmentRecorderBinding
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


class RecorderFragment : Fragment() {

    private var output: String? = null
    private var mediaRecorder: MediaRecorder? = null
    private var state: Boolean = false
    private var recordingStopped: Boolean = false

    private var start_btn: Button? = null
    private var pause_btn: Button? = null
    private var stop_btn: Button? = null

    private var fileName: EditText? = null
    private var fileExt: Spinner? = null

    private var seconds = 0

    // Is the stopwatch running?
    private var running = false
    private var wasRunning = false
    private var Uri: Uri? = null
    private var name: String? = null

    // Elements binding
    private var _binding: FragmentRecorderBinding? = null

    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val recorderViewModel =
            ViewModelProvider(this).get(RecorderViewModel::class.java)

        _binding = FragmentRecorderBinding.inflate(inflater, container, false)

        val root: View = binding.root
        if (savedInstanceState != null) {

            // Get the previous state of the stopwatch
            // if the activity has been
            // destroyed and recreated.
            seconds = savedInstanceState
                .getInt("seconds")
            running = savedInstanceState
                .getBoolean("running")
            wasRunning = savedInstanceState
                .getBoolean("wasRunning")
        }
        runTimer()

        // UI binding
        start_btn=binding.btnStart
        pause_btn=binding.btnPause
        stop_btn=binding.btnStop

        fileName=binding.fileName
        val current = LocalDateTime.now()

        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        val formatted = current.format(formatter)
        fileName!!.setText(formatted)

        fileExt = binding.spinner
        val languages = resources.getStringArray(R.array.Extension)
        if (fileExt != null) {
            val adapter = ArrayAdapter(
                context!!,
                android.R.layout.simple_spinner_item, languages
            )
            fileExt!!.adapter = adapter
        }
        fileExt!!.setSelection(0)



        start_btn!!.setOnClickListener {


            if (ContextCompat.checkSelfPermission(context!!,
                    Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context!!,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                ActivityCompat.requestPermissions(context!! as Activity, permissions,0)
            } else {
                mediaRecorder = MediaRecorder(context!!)
                name = fileName!!.text.toString() + "." + fileExt!!.selectedItem.toString()
                val file: File = File(context!!.getExternalFilesDir(null), name!!)
                Uri = file.toUri()
                output = file.absolutePath //rootDir.absolutePath+"/recording.mp3"
                Log.v("Path", output!!)
                mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
                mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder?.setOutputFile(output)
                startRecording()
            }
        }

        stop_btn!!.setOnClickListener{
            stopRecording()
        }

        pause_btn!!.setOnClickListener {
            pauseRecording()
        }
        return root
    }
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveFileUsingMediaStore(context: Context, url: String, fileName: String, fileExt: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/$fileExt")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_RECORDINGS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            URL(url).openStream().use { input ->
                resolver.openOutputStream(uri).use { output ->
                    input.copyTo(output!!, DEFAULT_BUFFER_SIZE)
                }
            }
        }
    }


    private fun startRecording() {
        try {
            running = true
            mediaRecorder?.prepare()
            mediaRecorder?.start()
            state = true
            Toast.makeText(context!!, "Recording started!", Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun pauseRecording() {
        wasRunning = running
        running = false
        if(state) {
            if(!recordingStopped){
                Toast.makeText(context!!,"Stopped!", Toast.LENGTH_SHORT).show()
                mediaRecorder?.pause()
                recordingStopped = true
                pause_btn!!.text = "Resume"
            }else{
                resumeRecording()
            }
        }
    }

    @SuppressLint("RestrictedApi", "SetTextI18n")
    @TargetApi(Build.VERSION_CODES.N)
    private fun resumeRecording() {
        if(wasRunning)running=true
        Toast.makeText(context!!,"Resume!", Toast.LENGTH_SHORT).show()
        mediaRecorder?.resume()
        pause_btn!!.text = "Pause"
        recordingStopped = false
    }

    private fun stopRecording(){
        running = false
        wasRunning = false
        if(state){
            mediaRecorder?.stop()
            mediaRecorder?.release()
            state = false
            saveFileUsingMediaStore(context!!, Uri.toString(), fileName!!.text.toString(), fileExt!!.selectedItem.toString())
        }else{
            Toast.makeText(context!!, "You are not recording right now!", Toast.LENGTH_SHORT).show()
        }
    }
    private fun runTimer() {

        // Get the text view.
        val timeView = binding.time

        // Creates a new Handler
        val handler = Handler()

        // Call the post() method,
        // passing in a new Runnable.
        // The post() method processes
        // code without a delay,
        // so the code in the Runnable
        // will run almost immediately.
        handler.post(object : Runnable {
            override fun run() {
                val minutes: Int = seconds / 60
                val secs: Int = seconds % 60

                // Format the seconds into hours, minutes,
                // and seconds.
                val time: String = java.lang.String
                    .format(
                        Locale.getDefault(),
                        "%d:%02d",
                        minutes, secs
                    )

                // Set the text view text.
                timeView.text = time

                // If running is true, increment the
                // seconds variable.
                if (running) {
                    seconds++
                }

                // Post the code again
                // with a delay of 1 second.
                handler.postDelayed(this, 1000)
            }
        })
    }
}