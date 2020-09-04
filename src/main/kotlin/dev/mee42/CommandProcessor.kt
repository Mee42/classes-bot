package dev.mee42

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.entity.channel.GuildMessageChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import reactor.util.Loggers

private val logger = Loggers.getLogger("CommandProcessor")

@FlowPreview
suspend fun processCommands(client: GatewayDiscordClient) = client.on(MessageCreateEvent::class.java).asFlow()
    .filter { it.guildId.isPresent } // ignore DMs
    .filter { it.guildId.get() == Snowflake.of(747884555922440263) } // only in the '2020 classes bot' guild
    .flatMapMerge { event -> flow<Unit> {
        val message = event.message
        if (message.channelId !in listOf(Snowflake.of(747887763923402822), Snowflake.of(747916458327277568))) {
            return@flow // only in #bot-testing or #admin-bot-commands
        }
        val channel = message.channel.cast(GuildMessageChannel::class.java).awaitSingle()
        val content = message.content
        if (!content.startsWith("!")) return@flow // prefix is '!', no option to change. Do we want something different?
        val (commandName, args) = content
            .drop(1) // remove the !
            .trim()
            .split(" ", limit = 2)
            .let { if (it.size == 1) it.plus("") else it } // handles the ["!help"] case, changes it to ["!help",""]
            .map { it.trim() } // for good measure
        if (commandName.isEmpty()) return@flow // if the message is just "!"? or maybe "! help"? either way, ignore
        val command = commands.firstOrNull { it.command == commandName }
        if (command == null) {
            channel.createMessage("Not a command: \"$commandName\"").await()
            logger.info("\"${commandName.replace("\n", "\\n")}\" command was ran, but doesn't exist")
            return@flow
        }
        val author = message.author.get()
        if (command.needsAdmin && author.id != Snowflake.of(293853365891235841)) {
            logger.info("User ${author.id.asString()} tried to run !$commandName, but is not an admin")
            channel.createMessage("You need admin to use this command").await()
            return@flow
        }
        val context = Context(
            event = event,
            channel = channel,
            message = message,
            argument = args,
            author = author
        )
        logger.info("${author.id.asString()} is running !$commandName. args: \"" + args.replace("\n", "\\n") + '"')
       try {
           command.processor(context)
       } catch(e: Exception) {
           e.printStackTrace()
       }
        this.emit(Unit)
    } }.collect()