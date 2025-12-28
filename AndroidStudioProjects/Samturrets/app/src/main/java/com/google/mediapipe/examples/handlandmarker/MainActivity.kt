/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.handlandmarker


import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.mediapipe.examples.handlandmarker.databinding.ActivityMainBinding
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark as TaskLandmark
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark as ProtoLandmark
interface GestureSpeaker {
    fun speakGesture(gesture: String, utteranceId: String)
}


class MainActivity : AppCompatActivity(), GestureSpeaker {
    private lateinit var tts: TextToSpeech
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel : MainViewModel by viewModels()
    private lateinit var gestureClassifierHelper: GestureClassifierHelper
    private val ttsInterval = 3000L // 4 seconds
    var latestGesture: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController
        activityMainBinding.navigation.setupWithNavController(navController)
        activityMainBinding.navigation.setOnNavigationItemReselectedListener {
            // ignore the reselection
        }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts.setLanguage(Locale.US)  // or Locale("en", "IN")
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("MainActivity", "TTS language is missing or not supported.")
                }
                // No temporary test speech here
            }
        }
        gestureClassifierHelper = GestureClassifierHelper(this)
        startTtsIntervalTimer()
    }

    override fun speakGesture(gesture: String, utteranceId: String) {
        if (::tts.isInitialized) {
            tts.speak(gesture, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }

    override fun onBackPressed() {
       finish()
    }

    fun onHandLandmarkerResult(landmarks: List<ProtoLandmark>, gestureLabel: String) {
        latestGesture = gestureLabel
    }

    private fun convertProtoToTaskLandmarks(protoLandmarks: List<ProtoLandmark>): List<TaskLandmark> {
        return protoLandmarks.map { proto ->
            TaskLandmark.create(proto.x, proto.y, proto.z)
        }
    }

    private fun startTtsIntervalTimer() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                latestGesture?.let { gesture ->
                    val spokenLabel = gesture.replace("_", " ").replaceFirstChar { it.uppercase() }
                    speakGesture(spokenLabel, "gestureUtterance")
                }
                handler.postDelayed(this, ttsInterval)
            }
        }, ttsInterval)
    }
}