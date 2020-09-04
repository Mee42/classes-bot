@file:Suppress("EXPERIMENTAL_FEATURE_WARNING")

package dev.mee42

import discord4j.common.util.Snowflake
import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactive.awaitFirstOrNull
import reactor.util.Loggers
import java.time.Duration
import java.util.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val logger = Loggers.getLogger("Register")

private val registeringCategory: Snowflake = Snowflake.of(748969066416439337L)

val register = Command("register", "!register", "Register and put your classes in") {
    val userExists = Database.withHandle {
        it.createQuery("SELECT * FROM users WHERE id = :id")
                .bind("id", author.id.asString())
                .map(Database.User.Companion::map)
                .firstOrNull() != null
    }
    if(userExists) {
        createMessage("you are already registered. TODO here should go instructions to change your classes")
        return@Command
    }
    createMessage("registering " + author.username + "...")
    // okay now we need to make the channel
    val guild = channel.guild.await()
//    val regCategory = guild.getChannelById(registeringCategory).await() as Category
    val regChannel = guild.createTextChannel {
        it.setName("registering - " + author.id.asString())
        it.setParentId(registeringCategory)
        it.setPermissionOverwrites(mutableSetOf(
                PermissionOverwrite.forRole(Snowflake.of(747884555922440263), PermissionSet.none(), PermissionSet.of(Permission.VIEW_CHANNEL)),
                PermissionOverwrite.forMember(author.id, PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.none())
        ))
    }.await()


    createMessage("Moving over to <#" + regChannel.id.asString() + ">")


    val name = promptForMessage(
            channel = regChannel,
            author = message.author.get().id,
            initialMessage = "<@" + author.id.asString() + ">! Here's the channel where you'll be putting in your classes\n" +
                    "To start off, what's your name? reply '-' if you just want to use your discord username.\n " +
                    "*This is used to identify people easier, and is recommended*") checker@ {
        baseChecker<String?>(it)?.let { e -> return@checker e }
        if(it == "-") return@checker Result.Success(null)
        return@checker Result.Success(it)
    }

    // okay, now we're going to pick classes. Give a short tutorial on how to input a class, and then input all 8
    // might take some time lmao
    regChannel
            .createMessage("Now we can proceed to putting in your classes. If you make a mistake on one of these, don't worry, it can be fixed later. \n " +
                    "If possible, put in your class name/teacher/location exactly like they appear on SIS. This will decrease duplicates.")
            .await()
    val classes = (1..8).map { pickClass(it, regChannel, author.id) }

    regChannel.createMessage("Inserting you into the database...").await()
    logger.info("adding ${author.id.asString()} into db as name:\"$name\", classes: $classes")
    Database.useHandle { h ->
        var update = h.createUpdate("""
            INSERT INTO users (id, name, p1, p2, p3, p4, p5, p6, p7, p8) VALUES (:id, :name, :p1, :p2, :p3, :p4, :p5, :p6, :p7, :p8)
        """)
                .bind("id", author.id.asString())
                .bind("name", name)
        for(i in 1..8) {
            update = update.bind("p$i", classes[i - 1].id)
        }
        update.execute()
    }
    regChannel.createMessage("Done! We'll self-destruct this channel in 5 minutes, as it's no longer needed. " +
            "You can view your classes with !classes, and change them with !changeclass if there is a mistake or a schedule change.")
            .await()

    delay(5 * 60 * 1000)

    regChannel.delete().awaitFirstOrNull()
}

fun <T> baseChecker(str: String): Result.Error<T, String>? {
    if(str.isBlank()) return Result.Error("This can't be blank")
    val string = str.trim()
    if(string.length > 64) return Result.Error("character limit is 64. If")
    return null
}

inline class ClassID(val id: Int)

suspend fun pickClass(period: Int, channel: TextChannel, author: Snowflake): ClassID {
    // we gotta do: title, teacher, and location.
    val className = promptForMessage(channel, author, "What's the name of your ${renderNumber(period)} class? " +
            "This should be something like 'World History 2 HN' or 'AP Computer Science A'. For a free period, put 'free'. ") checker@ {
        baseChecker(it) ?: Result.Success(it)
    }
    val teacherName = promptForMessage(channel, author, "What is the teacher of this class (period $period)?" +
            " Remember to copy as close as you can from the name in SIS. For a free period, or if it it does not apply, put `-`") checker@ {
        baseChecker(it) ?: Result.Success(it)
    } // TODO don't allow newlines or tab characters or several spaces
    logger.info("Got class name \"$className\" and teacher name \"$teacherName\"")
    // insert into the db unless it exists already
    val classID = Database.withHandle {h ->
       val existing = h.createQuery("""
           SELECT (id) FROM classes WHERE UPPER(name) = UPPER(:name) AND UPPER(teacher) = UPPER(:teacher) AND period = :period
                """)
                .bind("name", className)
                .bind("teacher", teacherName)
                .bind("period", period)
                .mapTo(Int::class.java)
                .firstOrNull()
        logger.info("existing: $existing")
        existing ?: h.createUpdate("""
                       INSERT INTO classes (period, name, teacher) VALUES (:period, :name, :teacher) 
                    """)
                    .bind("period",period)
                    .bind("name",className)
                    .bind("teacher",teacherName)
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Int::class.java)
                    .first()
    }
    logger.info("Got class ID: $classID")
    return ClassID(classID)
}

fun renderNumber(i: Int) = when (i) {
    1 -> "1st"
    2 -> "2nd"
    3 -> "3rd"
    in 4..9 -> "${i}th"
    else -> error("can't support that oof")
}

sealed class Result<T, E> {
    class Error<T, E>(val e: E): Result<T, E>()
    class Success<T, E>(val t: T): Result<T, E>()
}

suspend fun <T> promptForMessage(channel: TextChannel, author: Snowflake, initialMessage: String, checker: (String) -> Result<T, String>): T {
    channel.createMessage(initialMessage).await()
    val nextMessage = suspend {
        channel.client.on(MessageCreateEvent::class.java)
                .filter {
                    it.message.channelId == channel.id && it.message.author.map { a -> a.id == author }.orFalse() && !it.message.content.isBlank()
                }
                .next().await()
    }
    while(true) {
        val next = nextMessage()
        val content = next.message.content.trim()
        when(val r = checker(content)) {
            is Result.Error -> {
                channel.createMessage(r.e).await()
                continue
            }
            is Result.Success -> {
                return r.t
            }
        }
    }
}


fun Optional<Boolean>.orFalse(): Boolean = this.orElse(false)









 