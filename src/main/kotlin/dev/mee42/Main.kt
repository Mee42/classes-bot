package dev.mee42

import discord4j.core.DiscordClient
import kotlinx.coroutines.reactor.mono
import reactor.util.Loggers

private val logger = Loggers.getLogger("Main")
fun main(argv: Array<String>) {
    logger.info("starting up")
//    Database.fullReset()
    val token = argv[0]
    val client = DiscordClient.create(token)
    client.withGateway { mono {
        processCommands(it)
    } }.block()
}

