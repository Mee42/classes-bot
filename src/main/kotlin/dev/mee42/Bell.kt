package dev.mee42

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBufferFactory
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.channel.VoiceChannel
import discord4j.voice.AudioProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration

val playerManager = DefaultAudioPlayerManager().let { 
    it.configuration.frameBufferFactory = AudioFrameBufferFactory { a: Int, b: AudioDataFormat, c: AtomicBoolean -> NonAllocatingAudioFrameBuffer(a, b, c) }
    AudioSourceManagers.registerLocalSource(it)
    it
}



suspend fun play(channel: VoiceChannel) {
    val track = AtomicReference<AudioTrack>(null)
    val player: AudioPlayer = playerManager.createPlayer()
    val x = channel.join {

        playerManager.loadItem(System.getenv("NATE_MP3") ?: "/bot/nate.mp3", object : AudioLoadResultHandler {

            override fun trackLoaded(t: AudioTrack?) {
                t?.let  { x -> track.set(x) }
            }

            override fun playlistLoaded(playlist: AudioPlaylist?) {
//                TODO("not implemented")
            }

            override fun noMatches() {
//                TODO("not implemented")
            }

            override fun loadFailed(exception: FriendlyException?) {
//                TODO("not implemented")
            }
        })

        it.setProvider(object: AudioProvider(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize())) {
            var frame: MutableAudioFrame = MutableAudioFrame()
            override fun provide(): Boolean {
                val didProvide = player.provide(frame)
                if(didProvide) buffer.flip()
                return didProvide
            }
            init {
                frame.setBuffer(buffer)
            }
        })
    }.await()


    while(track.get() == null) {
        delay(100)
    }
    player.playTrack(track.get())
    delay(4000)
    x.disconnect().awaitFirstOrNull()
}


suspend fun schedule(channel: VoiceChannel) {
    val hour = LocalDateTime.now().hour
    val minute = LocalDateTime.now().minute
    val second = LocalDateTime.now().second
    // we want get-to-class to fire at 9:45, 12, 1:35
    when (hour) {
        9 -> {
            if(minute < 45) {
                val time = (45 - minute) * 1000 * 60L - second * 1000
                println("waiting $time ms")
                delay(time)
                play(channel)
            } // if it's >= 45, just whatever, we missed it
        }
        11 -> {
            val time = (60 - minute) * 1000 * 60L - second * 1000
            println("waiting $time ms")
            delay(time)
            play(channel)
        }
        13 -> {
            if(minute < 35) {
                val time = (35 - minute) * 1000 * 60L - second * 1000
                println("waiting $time ms")
                delay(time)
                play(channel)
            }
        }
    }
    println("waiting 10 minutes...")
    delay(1000 * 60 * 10) // wait 10 minutes and check again
    schedule(channel)
}


val bell = Command("bell", "bell","get tf to class", true) {
    val channel = this.message.client.getChannelById(Snowflake.of(747884556429820007L)).await()
    if (channel !is VoiceChannel) return@Command
    play(channel)

}