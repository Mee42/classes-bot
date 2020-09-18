package dev.mee42

import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.MessageCreateSpec
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.reactive.awaitSingle
import java.util.function.Consumer

data class Context(val event: MessageCreateEvent,
                   val message: Message,
                   val argument :String,
                   val author: User,
                   val channel: GuildMessageChannel,
                   val guild: Guild
) {

    suspend fun createMessage(content: String): Message {
        return channel.createMessage(content).awaitSingle()
    }
    suspend fun createMessage(spec: Consumer<MessageCreateSpec>): Message {
        return channel.createMessage(spec).awaitSingle()
    }
    suspend fun createEmbed(spec: (EmbedCreateSpec) -> Unit): Message {
        return createMessage { it.setEmbed(spec) }
    }
}


data class Command(val command: String, val helpTitle: String, val helpDesc: String, val needsAdmin: Boolean = false, val processor: suspend Context.() -> Unit)