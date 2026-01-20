package com.example.selfiedemo

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.SharedFlow

class MainViewModel : ViewModel(), HolisticRecognizerHelper.Listener {

    private val holisticRecognizerHelper = HolisticRecognizerHelper()

    fun setupHelper(context: Context) {
        viewModelScope.launch {
            holisticRecognizerHelper.apply {
                listener = this@MainViewModel
                setup(context)
            }
        }
    }

    fun shutdownHelper() {
        viewModelScope.launch {
            holisticRecognizerHelper.apply {
                listener = null
                shutdown()
            }
        }
    }

    fun recognizeLiveStream(imageProxy: ImageProxy) {
        holisticRecognizerHelper.recognizeLiveStream(
            imageProxy = imageProxy,
        )
    }

    sealed class UiEvent {
        data class Face(
            val face: FaceResultBundle
        ) : UiEvent()

        data class Gesture(
            val gestures: GestureResultBundle,
        ) : UiEvent()
    }

    private val _uiEvents = MutableSharedFlow<UiEvent>(1)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents


    override fun onFaceLandmarkerResults(resultBundle: FaceResultBundle) {
        _uiEvents.tryEmit(UiEvent.Face(resultBundle))
    }

    override fun onGestureResults(resultBundle: GestureResultBundle) {
        _uiEvents.tryEmit(UiEvent.Gesture(resultBundle))
    }

    override fun onFaceLandmarkerError(error: String, errorCode: Int) {
        Log.e(TAG, "Face landmarker error $errorCode: $error")
    }

    override fun onGestureError(error: String, errorCode: Int) {
        Log.e(TAG, "Gesture recognizer error $errorCode: $error")
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}