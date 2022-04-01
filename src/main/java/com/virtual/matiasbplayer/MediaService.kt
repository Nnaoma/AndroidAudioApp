package com.virtual.matiasbplayer

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import java.util.*

class MediaService : MediaBrowserServiceCompat() {

    private val serviceTag = "MEDIA SERVICE"

    private var timer : Timer? = null
    private var mediaSession : MediaSessionCompat? = null
    private var mediaController : MediaControllerCompat? = null

    private var playback : PlaybackStateCompat.Builder? = null
    private var mediaPlayer : MediaPlayer? = null
    private var noteBuilder : NotificationCompat.Builder? = null

    private var audioManager : AudioManager? = null
    private var audioFocus : AudioFocusRequestCompat? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttrs = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mediaPlayer = MediaPlayer.create(this, R.raw.sound, audioAttrs, audioManager!!.generateAudioSessionId())
        mediaPlayer?.setOnCompletionListener {
            timer?.cancel()
            timer?.purge()
            updatePlaybackState(0)
            updateNotesOnPlaybackPause()
        }

        mediaSession = MediaSessionCompat(this, serviceTag)
        mediaSession?.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        sessionToken = mediaSession?.sessionToken

        playback = PlaybackStateCompat.Builder()
        playback?.setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP)


        mediaSession?.setCallback(object : MediaSessionCompat.Callback(){
            override fun onPlay() {
                super.onPlay()

                timer = Timer()

                startPlayback()
                mediaPlayer?.start()

                timer?.schedule(scheduleTimer(), 1000, 1000)
                //AudioManagerCompat.requestAudioFocus(audioManager!!, audioFocus!!)
            }

            override fun onPause() {
                super.onPause()

                updatePlaybackState(0)
                mediaPlayer?.let {
                    if(it.isPlaying) {
                        it.pause()
                    }
                }
                timer?.cancel()
                timer?.purge()

                updateNotesOnPlaybackPause()
                stopForeground(false)
            }

            override fun onStop() {
                super.onStop()

                mediaSession?.isActive = false
                stopForeground(true)
                stopSelf(1)
            }
        })

        mediaController = mediaSession?.controller
        audioFocus = buildAudioRequest()
        buildNotes()

        Log.e(null, mediaController?.sessionActivity?.toString(), null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession!!, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.isActive = false
        mediaSession?.release()
        AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, audioFocus!!)
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot {
        return BrowserRoot(getString(R.string.app_name), null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    private fun updatePlaybackState(state : Int){
        val currentState = when(state){
            0 -> PlaybackStateCompat.STATE_PAUSED

            1 -> PlaybackStateCompat.STATE_STOPPED

            else -> PlaybackStateCompat.STATE_PLAYING
        }

        playback?.setState(currentState, 0, 1.0F)

        mediaSession?.setPlaybackState(playback!!.build())
    }

    private fun buildAudioRequest() : AudioFocusRequestCompat {
        return AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN).setOnAudioFocusChangeListener {
            if (it == AudioManagerCompat.AUDIOFOCUS_GAIN) {
                mediaPlayer?.start()
                Log.e(null, true.toString())
            }
            else {
                mediaController?.transportControls?.pause()
                Log.e(null, false.toString())
            }
        }.setWillPauseWhenDucked(false).build()
    }

    private fun buildNotes(){

        val channel = NotificationChannelCompat.Builder(serviceTag, NotificationManagerCompat.IMPORTANCE_LOW).setName("Matias").build()
        val notManager = NotificationManagerCompat.from(this)

        notManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(this, serviceTag)

        builder.setContentTitle("Test Sound").setContentText("000").setLargeIcon(null).setContentIntent(mediaController?.sessionActivity)
            .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)).setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        builder.setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setShowActionsInCompactView(0).setMediaSession(mediaSession!!.sessionToken))

        noteBuilder = builder
    }

    private fun startPlayback(){
        mediaSession?.isActive = true
        updatePlaybackState(2)

        noteBuilder?.clearActions()
        noteBuilder?.addAction(NotificationCompat.Action(R.drawable.pause_24, "pause", MediaButtonReceiver.buildMediaButtonPendingIntent(this@MediaService, PlaybackStateCompat.ACTION_PLAY_PAUSE)))
        startForeground(1, noteBuilder!!.build())
    }

    private fun scheduleTimer() : TimerTask{
        return object : TimerTask(){
            override fun run() {
                mainLooper.run {
                    mediaPlayer?.let {
                        if (it.isPlaying){
                            val time = it.currentPosition
                            noteBuilder?.setContentText((time/1000).toString())
                            NotificationManagerCompat.from(this@MediaService).notify(1, noteBuilder!!.build())
                        }
                    }
                }
            }
        }
    }

    private fun updateNotesOnPlaybackPause(){
        noteBuilder?.clearActions()
        noteBuilder?.addAction(NotificationCompat.Action(R.drawable.play_24, "play", MediaButtonReceiver.buildMediaButtonPendingIntent(this@MediaService, PlaybackStateCompat.ACTION_PLAY_PAUSE)))
        NotificationManagerCompat.from(this@MediaService).notify(1, noteBuilder!!.build())
    }
}