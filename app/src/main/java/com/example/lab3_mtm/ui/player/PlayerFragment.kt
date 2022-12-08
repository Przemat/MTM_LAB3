package com.example.lab3_mtm.ui.player

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.lab3_mtm.databinding.FragmentPlayerBinding
import com.example.lab3_mtm.ui.player.PlayerViewModel
import java.io.IOException
import java.util.*

class PlayerFragment : Fragment() {
    // User input mp3 file url in this text box. Or display user selected mp3 file name.
    private var audioFilePathEditor: EditText? = null

    // Click this button to let user select mp3 file.
    private var browseAudioFileButton: Button? = null

    // Start play audio button.
    private var startButton: Button? = null

    // Pause playing audio button.
    private var pauseButton: Button? = null

    // Stop playing audio button.
    private var stopButton: Button? = null

    // Show played audio progress.
    private var playAudioProgress: ProgressBar? = null

    // Used to control audio (start, pause , stop etc).
    private var audioPlayer: MediaPlayer? = null

    // Save user selected or inputted audio file unique resource identifier.
    private var audioFileUri: Uri? = null

    // Wait update audio progress thread sent message, then update audio play progress.
    private var audioProgressHandler: Handler? = null

    // The thread that send message to audio progress handler to update progress every one second.
    private var updateAudioPalyerProgressThread: Thread? = null

    // Record whether audio is playing or not.
    private var audioIsPlaying = false

    // Elements binding
    private var _binding: FragmentPlayerBinding? = null

    private val binding get() = _binding!!
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val playerViewModel =
            ViewModelProvider(this).get(PlayerViewModel::class.java)

        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        val root: View = binding.root
        startButton = binding.playButton
        pauseButton = binding.pauseButton
        stopButton = binding.stopButton
        playAudioProgress = binding.playAudioProgressbar

        /* Initialize audio progress handler. */if (audioProgressHandler == null) {
            audioProgressHandler = @SuppressLint("HandlerLeak")
            object : Handler() {
                override fun handleMessage(msg: Message) {
                    if (msg.what == UPDATE_AUDIO_PROGRESS_BAR) {
                        if (audioPlayer != null) {
                            // Get current play time.
                            val currPlayPosition = audioPlayer!!.currentPosition

                            // Get total play time.
                            val totalTime = audioPlayer!!.duration

                            // Calculate the percentage.
                            val currProgress = currPlayPosition * 1000 / totalTime

                            // Update progressbar.
                            playAudioProgress!!.progress = currProgress
                        }
                    }
                }
            }
        }

        /* When user input key up in this text editor. */
        audioFilePathEditor = binding.filePath
        audioFilePathEditor!!.setOnKeyListener { view, i, keyEvent ->
            val action = keyEvent.action
            if (action == KeyEvent.ACTION_UP) {
                val text = audioFilePathEditor!!.text.toString()
                if (text.length > 0) {
                    startButton!!.isEnabled = true
                    pauseButton!!.isEnabled = false
                    stopButton!!.isEnabled = false
                } else {
                    startButton!!.isEnabled = false
                    pauseButton!!.isEnabled = false
                    stopButton!!.isEnabled = false
                }
            }
            false
        }


        /* Click this button to popup select audio file component. */
        browseAudioFileButton = binding.fileButton
        browseAudioFileButton!!.setOnClickListener { // Require read external storage permission from user.
            val readExternalStoragePermission = ContextCompat.checkSelfPermission(context!!,Manifest.permission.READ_EXTERNAL_STORAGE )
            if (readExternalStoragePermission != PackageManager.PERMISSION_GRANTED) {
                val requirePermission =
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                ActivityCompat.requestPermissions(
                    context!! as Activity,
                    requirePermission,
                    REQUEST_CODE_READ_EXTERNAL_PERMISSION
                )
            } else {
                selectAudioFile()
            }
        }

        // When start button is clicked.
        startButton!!.setOnClickListener {
            startButton!!.isEnabled = false
            pauseButton!!.isEnabled = true
            stopButton!!.isEnabled = true
            val audioFilePath = audioFilePathEditor!!.text.toString()
            if (!TextUtils.isEmpty(audioFilePath)) {
                stopCurrentPlayAudio()
                initAudioPlayer()
                audioPlayer!!.start()
                audioIsPlaying = true

                // Display progressbar.
                playAudioProgress!!.visibility = ProgressBar.VISIBLE
                if (updateAudioPalyerProgressThread == null) {

                    // Create the thread.
                    updateAudioPalyerProgressThread = object : Thread() {
                        override fun run() {
                            try {
                                while (audioIsPlaying) {
                                    if (audioProgressHandler != null) {
                                        // Send update audio player progress message to main thread message queue.
                                        val msg = Message()
                                        msg.what =
                                            UPDATE_AUDIO_PROGRESS_BAR
                                        audioProgressHandler!!.sendMessage(msg)
                                        sleep(1000)
                                    }
                                }
                            } catch (ex: InterruptedException) {
                                Log.e(
                                    TAG_PLAY_AUDIO,
                                    ex.message,
                                    ex
                                )
                            }
                        }
                    }
                    (updateAudioPalyerProgressThread as Thread).start()
                }
            } else {
                Toast.makeText(
                    context,
                    "Please specify an audio file to play.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        /* When pause button is clicked. */pauseButton!!.setOnClickListener {
            if (audioIsPlaying) {
                audioPlayer!!.pause()
                startButton!!.isEnabled = true
                pauseButton!!.isEnabled = false
                stopButton!!.isEnabled = true
                audioIsPlaying = false
                updateAudioPalyerProgressThread = null
            }
        }

        /* When stop button is clicked. */stopButton!!.setOnClickListener {
            if (audioIsPlaying) {
                audioPlayer!!.stop()
                audioPlayer!!.release()
                audioPlayer = null
                startButton!!.isEnabled = true
                pauseButton!!.isEnabled = false
                stopButton!!.isEnabled = false
                updateAudioPalyerProgressThread = null
                playAudioProgress!!.progress = 0
                playAudioProgress!!.visibility = ProgressBar.INVISIBLE
                audioIsPlaying = false
            }
        }
        return root
    }

    /* Initialize media player. */
    private fun initAudioPlayer() {
        try {
            if (audioPlayer == null) {
                audioPlayer = MediaPlayer()
                val audioFilePath = audioFilePathEditor!!.text.toString().trim { it <= ' ' }
                Log.d(TAG_PLAY_AUDIO, audioFilePath)
                if (audioFilePath.lowercase(Locale.getDefault()).startsWith("http://")) {
                    // Web audio from a url is stream music.
                    audioPlayer!!.setAudioStreamType(AudioManager.STREAM_MUSIC)
                    // Play audio from a url.
                    audioPlayer!!.setDataSource(audioFilePath)
                } else {
                    if (audioFileUri != null) {
                        // Play audio from selected local file.
                        audioPlayer!!.setDataSource(context!!, audioFileUri!!)
                    }
                }
                audioPlayer!!.prepare()
            }
        } catch (ex: IOException) {
            Log.e(TAG_PLAY_AUDIO, ex.message, ex)
        }
    }

    /* This method start get content activity to let user select audio file from local directory.*/
    private fun selectAudioFile() {
        // Create an intent with ACTION_GET_CONTENT.
        val selectAudioIntent = Intent(Intent.ACTION_GET_CONTENT)

        // Show audio in the content browser.
        // Set selectAudioIntent.setType("*/*") to select all data
        // Intent for this action must set content type, otherwise android.content.ActivityNotFoundException: No Activity found to handle Intent { act=android.intent.action.GET_CONTENT } will be thrown
        selectAudioIntent.type = "audio/*"

        // Start the activity.
        startActivityForResult(selectAudioIntent, REQUEST_CODE_SELECT_AUDIO_FILE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_SELECT_AUDIO_FILE) {
            if (resultCode == RESULT_OK) {
                // To make example simple and clear, we only choose audio file from
                // local file, this is easy to get audio file real local path.
                // If you want to get audio file real local path from a audio content provider
                // Please read another article.
                audioFileUri = data!!.data
                val audioFileName = audioFileUri!!.lastPathSegment
                audioFilePathEditor!!.setText("You selected audio file is $audioFileName")
                initAudioPlayer()
                startButton!!.isEnabled = true
                pauseButton!!.isEnabled = false
                stopButton!!.isEnabled = false
            }
        }
    }

    /* Stop current play audio before play another. */
    private fun stopCurrentPlayAudio() {
        if (audioPlayer != null && audioPlayer!!.isPlaying) {
            audioPlayer!!.stop()
            audioPlayer!!.release()
            audioPlayer = null
        }
    }

    /* This method will be called after user choose grant read external storage permission or not. */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_READ_EXTERNAL_PERMISSION) {
            if (grantResults.size > 0) {
                val grantResult = grantResults[0]
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    // If user grant the permission then open browser let user select audio file.
                    selectAudioFile()
                } else {
                    Toast.makeText(
                        context,
                        "You denied read external storage permission.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        if (audioPlayer != null) {
            if (audioPlayer!!.isPlaying) {
                audioPlayer!!.stop()
            }
            audioPlayer!!.release()
            audioPlayer = null
        }
        super.onDestroy()
    }

    companion object {
        // Used when user select audio file.
        private const val REQUEST_CODE_SELECT_AUDIO_FILE = 1

        // Used when user require android READ_EXTERNAL_PERMISSION.
        private const val REQUEST_CODE_READ_EXTERNAL_PERMISSION = 2

        // Used when update audio progress thread send message to progress bar handler.
        private const val UPDATE_AUDIO_PROGRESS_BAR = 3

        // Used to distinguish log data.
        const val TAG_PLAY_AUDIO = "PLAY_AUDIO"
    }
}