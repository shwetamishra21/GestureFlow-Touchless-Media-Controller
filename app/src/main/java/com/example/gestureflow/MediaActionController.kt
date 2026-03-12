package com.example.gestureflow

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.media.session.MediaSessionManager
import android.content.ComponentName
import android.media.AudioAttributes

class MediaActionController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private var isMuted = false
    private var volumeBeforeMute = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun setVolume(fraction: Float) {
        if (isMuted) return
        val target = (fraction * maxVolume).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }

    fun getVolumeFraction(): Float {
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
    }

    fun toggleMute() {
        if (isMuted) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeBeforeMute, 0)
            isMuted = false
        } else {
            volumeBeforeMute = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            isMuted = true
        }
    }

    fun togglePlayPause() {
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun nextTrack() {
        sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    private fun sendMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}