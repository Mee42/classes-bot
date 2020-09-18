package dev.mee42

import discord4j.common.util.Snowflake
import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.channel.CategorizableChannel
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import kotlinx.coroutines.reactive.awaitLast
import reactor.core.publisher.Mono

val majorCat = Snowflake.of(752600005944279141)
val minorCat = Snowflake.of(752600226086518947)

suspend fun assign(classes: List<String?>, isMajor: Boolean, category: Snowflake, guild: Guild) {
    println("adding these classes, isMajor: $isMajor: " + classes)
    classes.filterNotNull().forEach { channelName ->
        // we need to make a channel
        val peoplePartOfChannel = Database.withHandle { h ->
            h.createQuery("""
                SELECT (id) FROM users u WHERE p1 IN (SELECT (id) FROM classes WHERE major = :val)
                    OR p2 IN (SELECT (id) FROM classes WHERE major = :val)
                    OR p3 IN (SELECT (id) FROM classes WHERE major = :val)
                    OR p4 IN (SELECT (id) FROM classes WHERE major = :val)
                    OR p5 IN (SELECT (id) FROM classes WHERE major = :val)
                    OR p6 IN (SELECT (id) FROM classes WHERE major = :val)
                    OR p7 IN (SELECT (id) FROM classes WHERE major = :val)
                    OR p8 IN (SELECT (id) FROM classes WHERE major = :val)
                """.let { if(!isMajor) it.replace("major", "minor") else it })
                    .bind("val",channelName)
                    .mapTo(String::class.java)
                    .list()
        }
//        println("creating text channel $channelName")
        guild.createTextChannel {
            it.setName(channelName)
            it.setParentId(category)
            it.setPermissionOverwrites(
                    setOf(
                            PermissionOverwrite.forRole(Snowflake.of(747884555922440263),/*everyone*/
                                    PermissionSet.none(),
                                    PermissionSet.of(Permission.VIEW_CHANNEL)
                            ),
                            *peoplePartOfChannel.map { snowflake ->
                                PermissionOverwrite.forMember(Snowflake.of(snowflake), PermissionSet.of(Permission.VIEW_CHANNEL), PermissionSet.none())
                            }.toTypedArray()
                    )
            )
        }.await()
    }
}

val assignClasses = Command("assign-classes", "not for you","not for you", needsAdmin = true) {
    if(this.author.id != Snowflake.of(293853365891235841)) return@Command
    val forAll = { str: String ->
        Database.withHandle { h ->
            h.createQuery("SELECT DISTINCT $str FROM classes").mapTo(String::class.java).list()
        }
    }
    assign(forAll("major"), isMajor = true, majorCat, guild)
    assign(forAll("minor"), isMajor =false, minorCat, guild)
}

val clearChannels = Command("clear-channels", "not for you","not for you", needsAdmin = true) {
    guild.channels
            .ofType(CategorizableChannel::class.java)
            .filter { it.categoryId.map { u -> u == majorCat || u == minorCat }.orElse(false) }
            .flatMap { it.delete() }
            .then(Mono.just(true))
            .await()
}
    