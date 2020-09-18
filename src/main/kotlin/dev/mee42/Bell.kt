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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

val playerManager = DefaultAudioPlayerManager().let { 
    it.configuration.frameBufferFactory = AudioFrameBufferFactory { a: Int, b: AudioDataFormat, c: AtomicBoolean -> NonAllocatingAudioFrameBuffer(a, b, c) }
    AudioSourceManagers.registerLocalSource(it)
    it
}



val bell = Command("bell", "bell","get tf to class", true) {
    val channel = this.message.client.getChannelById(Snowflake.of(747884556429820007L)).await()
    if (channel !is VoiceChannel) return@Command
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