package dev.mee42

import discord4j.common.util.Snowflake
import discord4j.core.`object`.PermissionOverwrite
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.delay


val pingCommand = Command("ping", "!ping", "Testing, Testing.. Is this thing on?") {
    val (message, time) = measureTime { createMessage("Pinging....") }
    message.edit { it.setContent(":ping_pong: Pong! Took " + time.toMillis() + "ms") }.await()
}

val helpCommand: Command = Command("help", "!help", "Get some help") {
    val isAdmin = message.author.map { it.id == Snowflake.of(293853365891235841) }.orElse(false)
    createEmbed { spec ->
        for(command in commands) {
            if(command.needsAdmin && !isAdmin) continue
            spec.addField(command.helpTitle, command.helpDesc,false)
        }
    }
}


val reset = Command("reset", "!reset", "Only for testing", needsAdmin = true) {
    Database.fullReset()
    createMessage("reset")
}
val dump = Command("dump","!dump", "Dump all data in the database", needsAdmin = true) {
    createEmbed { spec ->
        spec.setTitle("users")
        Database.withHandle {
            it.createQuery("SELECT * FROM users")
                .map(Database.User.Companion::map)
                .list()
        }.forEach {
            spec.addField("id: " + it.id.asString() + "  name: " + it.name,"classes: " + it.classes, false)
        }
    }
}

val classes = Command("classes", "!classes <person (optional)>", "Print all of your or someone else's classes") {
    val person = getPersonFromArgumentOrAuthor() ?: return@Command
    data class Class(val id: Int, val period: Int, val name: String, val teacher: String)
    val classes = Database.withHandle {
        it.createQuery("""
            SELECT c.id, c.period,c.name, c.teacher FROM (
                    SELECT * FROM users WHERE id = :id
                ) LEFT JOIN classes c on c.id IN (p1, p2, p3, p4, p5, p6, p7, p8) ORDER BY c.period
        """)
                .bind("id", person.asString())
                .map { rs, _ -> Class(rs.getInt("id"),rs.getInt("period"),rs.getString("name"),rs.getString("teacher")) }
                .toList()
    }
    val name = getDisplayName(person = person)
    createEmbed { spec ->
        spec.setTitle("$name's classes")
        for(clazz in classes) {
            spec.addField("[" + clazz.id + "] Period **" + clazz.period + "**, " + clazz.name, "Teacher: " + clazz.teacher, false)
        }
    }
}
suspend fun Context.getPersonFromArgumentOrAuthor(): Snowflake? = when {
    argument.isEmpty() -> author.id
    message.userMentionIds.size == 1 -> message.userMentionIds.first()
    argument.toLongOrNull() != null -> Snowflake.of(argument)
    else -> {
        createMessage("I'm not sure who you mean. Use a mention, or an ID")
        null
    }
}
suspend fun Context.getDisplayName(person: Snowflake = this.author.id): String {
    return Database.withHandle {
        it.createQuery("SELECT name FROM users WHERE id = :id")
                .bind("id",person.asString())
                .mapTo(String::class.java)
                .firstOrNull()
    } ?: this.message.guild.await().getMemberById(person).await().displayName
}
val shared = Command("shared", "!shared", "get all the classes you have with other people!") {
    data class Class(val id: Int, val period: Int, val teacher: String, val name: String, val people: String)
    val person = getPersonFromArgumentOrAuthor() ?: return@Command
    val data = Database.withHandle {
        it.createQuery("""
           SELECT c.period, c.id, c.name, c.teacher, GROUP_CONCAT(u.name, ', ') as people FROM (SELECT c.id, period,c.name,teacher
              FROM (SELECT * FROM users WHERE id = :id) LEFT JOIN classes c on c.id IN (p1, p2, p3, p4, p5, p6, p7, p8) ORDER BY period) c
           LEFT JOIN users u ON c.id IN (p1, p2, p3, p4, p5, p6, p7, p8) GROUP BY c.id
        """)
                .bind("id",person.asString())
                .map { rs, _ -> Class(
                        id = rs.getInt("id"),
                        period = rs.getInt("period"),
                        name = rs.getString("name"),
                        people = rs.getString("people"),
                        teacher = rs.getString("teacher"),
                ) }
                .list()
    }.sortedBy { it.period }
    val name = getDisplayName()
    createEmbed { spec ->
        spec.setTitle("$name's classes")
        for(clazz in data) {
            spec.addField("[" + clazz.id + "] Period " + clazz.period + ", " + clazz.name + " (" + clazz.teacher + ")",
                    clazz.people, false)
        }
    }
}

val slowPing = Command("slowping", "!slowping", "Pings, but it's really slow. Used for testing some async things") {
    delay(30 * 1000)
    createMessage("Pong! " + "https://discordapp.com/channels/747884555922440263/" + channel.id.asString() + "/" + message.id.asString())
}
var id = 0
val helpme = Command("helpme", "!helpme", "Need help with something? Use this command") {
    val g = message.guild.await()
    val c = g.createTextChannel {
        it.setParentId(Snowflake.of(750553164213649448))
        it.setPermissionOverwrites(
                setOf(
                        PermissionOverwrite.forMember(author.id, PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.none()),
                        PermissionOverwrite.forRole(Snowflake.of(747884555922440263), PermissionSet.none(), PermissionSet.of(Permission.VIEW_CHANNEL))
                )
        )
        it.setName("help-" + author.id.asString() + "-" + (id++))
    }.await()
    c.createMessage("<@" + author.id.asString() + "> Here you can ask for help! (pinging <@293853365891235841>)").await()
}



val commands = listOf(pingCommand, helpCommand, reset, dump, register, classes, slowPing, shared, helpme)