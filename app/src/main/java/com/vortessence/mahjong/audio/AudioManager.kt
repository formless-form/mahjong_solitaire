package com.vortessence.mahjong.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.MediaPlayer
import android.media.SoundPool
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vortessence.mahjong.R
import com.vortessence.mahjong.data.model.GameSettings
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.random.Random

class AudioManager(private val context: Context) {

    private val musicPlayer = BackgroundMusicPlayer(context.applicationContext)
    private val fireplace = FireplaceSound(context.applicationContext)
    private var currentSettings: GameSettings? = null
    private var isInForeground = false
    // On the game screen, where the fireplace burns beside the table
    private var room = FireRoom.NONE
    private var matchStreak = 0

    // Audio focus, held while the music plays, so a call, an alarm or another app's music and
    // podcasts pause the music and the fire, and they come back when that's over (or, if another
    // app took over for good, when the player next returns). With the music off the game takes no
    // focus: the faint fire and the tile sounds play alongside whatever else is playing
    private val system = context.getSystemService(android.media.AudioManager::class.java)
    private var focusHeld = false
    // Lost to a call or another app; asked for again only when the player next comes back
    private var focusLost = false
    private val focusRequest = AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener({ onFocusChange(it) }, Handler(Looper.getMainLooper()))
        .build()
    private var lastMatchTimeMs = 0L

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    // A light wooden tap: two takes at two pitches each. Built by tools/make-tap-sounds.py
    private val pickSoundIds = intArrayOf(
        soundPool.load(context.applicationContext, R.raw.tile_tap_1, 1),
        soundPool.load(context.applicationContext, R.raw.tile_tap_2, 1),
        soundPool.load(context.applicationContext, R.raw.tile_tap_3, 1),
        soundPool.load(context.applicationContext, R.raw.tile_tap_4, 1),
    )

    // Two tiles clacking together over a soft burst of stardust (chimes and glitter), in four
    // takes at slightly different pitches with their own sparkles, so repeated matches never
    // sound mechanical; each match also plays at a fresh random speed within 3% (pitch and
    // length together). Built by tools/make-match-sounds.py
    private val matchSoundIds = intArrayOf(
        soundPool.load(context.applicationContext, R.raw.tile_match_1, 1),
        soundPool.load(context.applicationContext, R.raw.tile_match_2, 1),
        soundPool.load(context.applicationContext, R.raw.tile_match_3, 1),
        soundPool.load(context.applicationContext, R.raw.tile_match_4, 1),
    )

    // The dull knock of a blocked tile. Built by tools/make-knock-sound.py
    private val blockedSoundIds = intArrayOf(soundPool.load(context.applicationContext, R.raw.tile_blocked, 1))

    // While the Fire sound slider is dragged, the fire is heard as loud as at the table (it's set
    // by ear for the table, and the menus play it at a fifth)
    private var auditioningFire = false

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun updateSettings(settings: GameSettings) {
        val previousSettings = currentSettings
        currentSettings = settings
        syncFocus()

        if (musicOn(settings)) {
            musicPlayer.setVolume(loudness(settings.musicVolume))
            val wasMusicEnabled = previousSettings?.let { musicOn(it) } ?: false
            if (!canPlayMusic) {
                // Never start playback in the background or without focus; onResume() (or
                // regaining focus) will start it.
                // Stopping here ensures a random song is picked when music was just enabled.
                if (!wasMusicEnabled) musicPlayer.stop()
            } else if (!wasMusicEnabled) {
                // When music is enabled on startup or toggled on, start with a random song
                musicPlayer.startRandom()
            } else {
                musicPlayer.resume()
            }
        } else {
            // When music is disabled, stop playback so re-enabling starts with a random song
            musicPlayer.stop()
        }
        if (isInForeground) fireplace.play(fireLevel(settings))
    }

    private fun musicOn(settings: GameSettings) = settings.musicEnabled && settings.musicVolume > 0f

    private val canPlayMusic get() = isInForeground && focusHeld && !focusLost

    // Holds focus while the music should play, and lets it go otherwise
    private fun syncFocus() {
        val want = isInForeground && currentSettings?.let { musicOn(it) } == true
        if (want && !focusHeld && !focusLost) {
            val granted = system?.requestAudioFocus(focusRequest) ?: android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusHeld = granted == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            // Refused (in a call, say): silent until the player next comes back
            if (!focusHeld) {
                focusLost = true
                fireplace.pause()
            }
        } else if (!want && focusHeld) {
            system?.abandonAudioFocusRequest(focusRequest)
            focusHeld = false
        }
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                focusLost = false
                if (isInForeground) resumeAmbience()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusLost = true
                pauseAmbience()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS -> {
                // Another app took over for good: let go, and ask again when the player comes back
                focusLost = true
                pauseAmbience()
                system?.abandonAudioFocusRequest(focusRequest)
                focusHeld = false
            }
            // A notification or directions over the music: Android turns it down itself
            else -> Unit
        }
    }

    private fun resumeAmbience() {
        val settings = currentSettings ?: return
        if (musicOn(settings) && canPlayMusic) musicPlayer.resume()
        fireplace.play(fireLevel(settings))
        fireplace.resume()
    }

    private fun pauseAmbience() {
        musicPlayer.pause()
        fireplace.pause()
        releaseWinPlayer()
    }

    /**
     * Where the player is: at the table, the fireplace is heard as set; on the menu screens
     * (Settings, Levels, Layouts), whose stone it lights faintly, softly (a fifth as loud); on
     * the title screen not at all.
     */
    /** The Fire sound slider is being dragged ([on]), or has been let go. */
    fun auditionFire(on: Boolean) {
        auditioningFire = on
        val settings = currentSettings ?: return
        if (isInForeground) fireplace.play(fireLevel(settings))
    }

    fun setFireRoom(room: FireRoom) {
        this.room = room
        // At the table the fireworks are made ready for a win; anywhere else they aren't needed
        if (room == FireRoom.TABLE && isInForeground) prepareWin() else dropReadyWin()
        val settings = currentSettings ?: return
        if (isInForeground) fireplace.play(fireLevel(settings))
    }

    /**
     * The fireplace's crackle: ambience, never a distraction. Its own volume setting runs up to
     * just under half the recording's level, a little louder as the fire burns brighter. At the
     * table it is silent when the fire is out; the menu screens always have their faint fire, so
     * always its soft crackle. Silent on the title screen.
     */
    private fun fireLevel(settings: GameSettings): Float {
        val level = loudness(settings.fireVolume) * 0.45f * (0.8f + 0.4f * settings.fireIntensity)
        if (auditioningFire) return level
        // The fire is out, crackle and all, while the phone's Remove animations is on
        if (!settings.animationsEnabled) return 0f
        return when (room) {
            FireRoom.TABLE -> if (settings.fireplaceLit) level else 0f
            FireRoom.MENU -> level * MENU_FIRE_SHARE
            FireRoom.NONE -> 0f
        }
    }

    fun onResume() {
        isInForeground = true
        focusLost = false
        if (room == FireRoom.TABLE) prepareWin()
        syncFocus()
        // Do not play any music until settings have actually been checked and loaded
        if (!focusLost) resumeAmbience()
    }

    fun onPause() {
        isInForeground = false
        syncFocus()
        musicPlayer.pause()
        fireplace.pause()
        // The fireworks don't carry on in the background, nor wait there
        releaseWinPlayer()
        dropReadyWin()
        pendingWinVolume = null
    }

    fun release() {
        // So nothing more is made ready, and what's being made is let go
        isInForeground = false
        syncFocus()
        releaseWinPlayer()
        dropReadyWin()
        winLoader.shutdown()
        musicPlayer.release()
        fireplace.stop()
        try {
            soundPool.release()
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun playTileSelect() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(pickSoundIds, loudness(settings.soundVolume))
        }
        vibrate { HapticPatterns.select(it) }
    }

    // Title-screen menu: a quiet, slightly higher tick made from a tile-pick sound
    fun playMenuClick() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(pickSoundIds, loudness(settings.soundVolume) * 0.35f, rate = 1.25f)
        }
        vibrate { HapticPatterns.select(it) }
    }

    fun playTileDeselect() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(pickSoundIds, loudness(settings.soundVolume))
        }
        vibrate { HapticPatterns.deselect(it) }
    }

    fun playTileMatch() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(matchSoundIds, loudness(settings.soundVolume), rate = 0.97f + Random.nextFloat() * 0.06f)
        }
        val now = SystemClock.elapsedRealtime()
        matchStreak = if ((now - lastMatchTimeMs) <= MATCH_STREAK_WINDOW_MS) {
            (matchStreak + 1).coerceAtMost(HapticPatterns.MAX_MATCH_STREAK)
        } else {
            0
        }
        lastMatchTimeMs = now
        vibrate { HapticPatterns.match(it, matchStreak) }
    }

    fun playTileBlocked() {
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playRandomSound(blockedSoundIds, loudness(settings.soundVolume))
        }
        vibrate { HapticPatterns.blocked() }
    }

    fun playWin() {
        if (!isInForeground || focusLost) return
        val settings = currentSettings ?: return
        if (settings.soundEnabled) {
            playWinPhrase(loudness(settings.soundVolume))
        }
        vibrate { HapticPatterns.win(it) }
    }

    // The win sound: a real fireworks show, as long as the light show (5.5 s, built by
    // tools/make-win-sound.py). Too long for the SoundPool, so it has its own player, and the music
    // eases down beneath it and back after.
    private var winPlayer: MediaPlayer? = null
    private var winBoost: LoudnessEnhancer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // The fireworks' player is made (the recording opened and decoding started) off the main
    // thread, and kept ready while a board is open, so a win starts it without a hitch. Touched
    // only on the main thread, apart from the making
    private class WinSound(val player: MediaPlayer, val boost: LoudnessEnhancer?)
    private val winLoader = Executors.newSingleThreadExecutor { r -> Thread(r, "win-sound").apply { isDaemon = true } }
    private var readyWin: WinSound? = null
    private var winLoading = false
    // A win that came before the player was ready, at this volume
    private var pendingWinVolume: Float? = null

    private fun prepareWin() {
        if (readyWin != null || winLoading) return
        winLoading = true
        if (winLoader.isShutdown) return
        winLoader.execute {
            val made = runCatching { makeWinSound() }.getOrNull()
            mainHandler.post {
                winLoading = false
                if (made == null) return@post
                val volume = pendingWinVolume
                pendingWinVolume = null
                when {
                    !isInForeground -> release(made)
                    volume != null -> startWin(made, volume)
                    room == FireRoom.TABLE && readyWin == null -> readyWin = made
                    else -> release(made)
                }
            }
        }
    }

    private fun makeWinSound(): WinSound? {
        val player = MediaPlayer.create(
            context.applicationContext,
            R.raw.win_fireworks,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            // A session of its own for the loudness enhancer: on Android 13, AUDIO_SESSION_ID_GENERATE
            // here leaves the player on session 0, the whole output, which the enhancer can't use
            context.getSystemService(android.media.AudioManager::class.java)?.generateAudioSessionId()
                ?: android.media.AudioManager.AUDIO_SESSION_ID_GENERATE,
        ) ?: return null
        // The recording already peaks at full scale, so it is boosted as it plays, with Android's
        // loudness enhancer keeping the peaks from distorting
        val boost = runCatching {
            LoudnessEnhancer(player.audioSessionId).apply {
                setTargetGain(WIN_BOOST_MILLIBELS)
                enabled = true
            }
        }.getOrNull()
        return WinSound(player, boost)
    }

    private fun playWinPhrase(volume: Float) {
        releaseWinPlayer()
        val ready = readyWin
        readyWin = null
        if (ready != null) {
            startWin(ready, volume)
        } else {
            // Not ready (the board opened a moment ago): it starts as soon as it is
            pendingWinVolume = volume
            prepareWin()
        }
    }

    private fun startWin(sound: WinSound, volume: Float) {
        val level = volume.coerceIn(0f, 1f)
        sound.player.setVolume(level, level)
        sound.player.setOnCompletionListener {
            if (winPlayer === it) releaseWinPlayer()
        }
        winPlayer = sound.player
        winBoost = sound.boost
        rampMusic(to = 0.4f)
        sound.player.start()
    }

    private fun release(sound: WinSound) {
        runCatching { sound.boost?.release() }
        runCatching { sound.player.release() }
    }

    private fun dropReadyWin() {
        readyWin?.let(::release)
        readyWin = null
    }

    private fun releaseWinPlayer() {
        val player = winPlayer ?: return
        winPlayer = null
        runCatching { winBoost?.release() }
        winBoost = null
        try {
            player.release()
        } catch (_: Exception) {
            // Ignore
        }
        rampMusic(to = 1f)
        // Ready for the next win, if still at the table
        if (room == FireRoom.TABLE && isInForeground) prepareWin()
    }

    // Eases the music's ducking to [to] over about half a second
    private fun rampMusic(to: Float) {
        mainHandler.removeCallbacksAndMessages(MUSIC_RAMP)
        val from = musicDuck
        val steps = 12
        for (i in 1..steps) {
            mainHandler.postAtTime(
                {
                    musicDuck = from + (to - from) * i / steps
                    musicPlayer.duck(musicDuck)
                },
                MUSIC_RAMP,
                SystemClock.uptimeMillis() + i * 40L,
            )
        }
    }

    private var musicDuck = 1f

    private fun playRandomSound(soundIds: IntArray, volume: Float, rate: Float = 1.0f) {
        if ((volume <= 0f) || soundIds.isEmpty()) return
        val soundId = soundIds[Random.nextInt(soundIds.size)]
        val scaledVolume = volume.coerceIn(0f, 1f)
        try {
            soundPool.play(soundId, scaledVolume, scaledVolume, 1, 0, rate)
        } catch (_: Exception) {
            // Ignore audio playback exception
        }
    }

    private fun vibrate(effect: (Vibrator) -> VibrationEffect) {
        if (currentSettings?.vibrationEnabled != true) return
        val vibrator = vibrator ?: return
        try {
            vibrator.vibrate(effect(vibrator))
        } catch (_: Exception) {
            // Ignore if device has no vibration motor or permission denied
        }
    }

    private companion object {
        // Matches made within this window of each other build up the haptic streak
        const val MATCH_STREAK_WINDOW_MS = 4000L

        // Token for the music-ramp steps, so a new ramp can cancel one in progress
        private val MUSIC_RAMP = Any()

        // How much the win fireworks are boosted as they play (500 mB = 5 dB)
        const val WIN_BOOST_MILLIBELS = 500
    }
}

/**
 * A volume slider's position (0..1) as a gain: on a decibel scale over 40 dB, as loudness is
 * heard, so the whole slider changes it evenly. 0 is silent, 1 full.
 */
internal fun loudness(slider: Float): Float =
    if (slider <= 0f) 0f else 10f.pow(2f * (slider.coerceAtMost(1f) - 1f))

/** Where the fireplace can be heard from. */
enum class FireRoom { TABLE, MENU, NONE }

// On the menu screens the fire is heard at a fifth of its table volume
private const val MENU_FIRE_SHARE = 0.2f
