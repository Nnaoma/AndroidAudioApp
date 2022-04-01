package com.virtual.matiasbplayer

import android.content.ComponentName
import android.media.AudioManager
import android.os.Bundle
import android.os.RemoteException
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton

class MainActivity : AppCompatActivity() {

    private var mediaBrowser : MediaBrowserCompat? = null
    private var mediaController : MediaControllerCompat? = null

    var playState = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val playPause = findViewById<AppCompatImageButton>(R.id.play_pause)

        mediaBrowser = MediaBrowserCompat(this, ComponentName(this, MediaService::class.java), object :
            MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                super.onConnected()
                try{
                    val token = mediaBrowser?.sessionToken
                    mediaController = MediaControllerCompat(this@MainActivity, token!!)
                    MediaControllerCompat.setMediaController(this@MainActivity, mediaController)

                    mediaController?.registerCallback(object : MediaControllerCompat.Callback(){
                        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                            super.onPlaybackStateChanged(state)

                            when(state?.state){
                                PlaybackStateCompat.STATE_PLAYING -> { playPause.setImageResource(R.drawable.pause_24); playState = 0 }

                                PlaybackStateCompat.STATE_PAUSED -> { playPause.setImageResource(R.drawable.play_24); playState = 1 }

                                PlaybackStateCompat.STATE_STOPPED -> { playPause.setImageResource(R.drawable.play_24); playState = 2 }

                                else -> {}
                            }
                        }
                    })
                }catch (e : RemoteException){
                    Log.e("browser service error", e.toString())
                }
            }

            override fun onConnectionSuspended() {
                super.onConnectionSuspended()
                Log.e("browser service error", "connection to service suspended")
            }

            override fun onConnectionFailed() {
                super.onConnectionFailed()
                Log.e("browser service error", "connection to service failed")
            }
        }, null)
        mediaBrowser?.connect()

        playPause.setOnClickListener {
            if(playState != 0){
                if(!mediaBrowser!!.isConnected)
                    mediaBrowser?.connect()
                mediaController?.transportControls?.play()
            }
            else{
                mediaController?.transportControls?.pause()
            }
        }
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaBrowser?.disconnect()
    }
}