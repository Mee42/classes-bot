import discord4j.common.util.Snowflake
import discord4j.core.DiscordClient
import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.PermissionOverwrite
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.spec.EmbedCreateFields
import discord4j.core.spec.EmbedCreateSpec
import discord4j.core.spec.TextChannelCreateSpec
import discord4j.core.spec.TextChannelEditSpec
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import discord4j.rest.util.PermissionSet
import org.slf4j.LoggerFactory
import java.io.File

fun discordInit() {
    initCommands()
    val client = DiscordClient.create(File("key.txt").readLines()[0])
    val gateway = client.login().block()!!
    gateway
        .on(MessageCreateEvent::class.java)
        .filter { it.message.channelId == Snowflake.of(870752780728532992) }
        .filter { it.message.content.startsWith("!") }
        .filter { it.message.author.isPresent }
        .subscribe {
            try {
                handleMessage(it)
            } catch(e: UserFuckedUpException) {
                println("user fucked up: " + e.message)
            } catch(e: Exception) {
                e.printStackTrace()
            }
        }

    gateway.onDisconnect().block()
}
class UserFuckedUpException(str: String): RuntimeException(str)
fun handleMessage(e: MessageCreateEvent) {
    val (command, args) = e.message.content.substring(1).trim().split(Regex(" "), limit = 2) + listOf("")
    val context = Context(
        event = e,
        message = e.message,
        author = e.message.authorAsMember.block()!!,
        args = args,
        client = e.client
    )
    commands[command]?.invoke(context) ?: context.userFuckedUp("Can't find a command with the name of \"$command\"")
}

data class Context(val event: MessageCreateEvent,
                   val message: Message,
                   val author: Member,
                   val args: String,
                   val client: GatewayDiscordClient) {
    fun send(str: String) {
        message.channel.block()!!.createMessage(str).block()
    }
    fun send(spec: EmbedCreateSpec) {
        message.channel.flatMap { it.createMessage(spec) }.block()
    }
    fun userFuckedUp(str: String): Nothing {
        val spec = EmbedCreateSpec.builder()
            .title("Error: $str")
            .description("If this was not an error, please report to the developer")
            .build()
        send(spec)
        throw UserFuckedUpException(str)
    }
}
val commands = mutableMapOf<String, (Context) -> Unit>()
fun <A, B> A.then(f: (A) -> B): B = f(this)

fun initCommands() {
    operator fun String.invoke(command: Context.() -> Unit) {
        commands[this] = command
    }

    "ping" {
        send("pong!")
    }
    "classes" {
        val userId = (if(args.isEmpty()) author.id else null)
            ?: args.toLongOrNull()?.run(Snowflake::of)
            ?: message.userMentions.firstOrNull()?.id
            ?: userFuckedUp("Can't figure out who you are referring too")

        val user = DB.lookupUserById(userId.asString())
            ?: userFuckedUp("You aren't registered. Go to https://classes.carson.sh and register!")

        val schedule = DB.getSchedule(userId.asString())
            .mapIndexed { i, clazz -> EmbedCreateFields.Field.of(
                "Period " + i.inc() + ", " + clazz.name,
                clazz.room + ",  " + clazz.teacher, false
            ) }

        EmbedCreateSpec.create()
            .withFields(schedule)
            .withTitle("Schedule for ${user.name}")
            .withColor(Color.DISCORD_BLACK)
            .then(::send)
    }
    "help" {
        send("Use !classes <user> to see what classes they have. Visit https://classes.carson.sh to see what classes you have, or to sign up. " +
                "If you need any help, just ping Carson")
    }
    "sync" {
        if(this.author.id != carsonID) {
            userFuckedUp("Only for <@293853365891235841>")
        }
        syncCommand()
    }
}


private fun Context.syncCommand() {
    // we will remove existingChannels as they are processed. any left over have no users :(
    val existingChannels = client.getGuildChannels(guildID)
        .ofType(TextChannel::class.java)
        .filter { it.categoryId.map { id -> id == classesCategory }.orElse(false) }
        .collectList()
        .block()!!.toMutableList()



    // loop through every class
    // get every other class that has the same name
    // see if there's a channel associated with it, if there isn't, make one, then revoke all read perms and then give read perms to everyone who needs it
    DB.cleanClasses() // just to make sure
    val classes = DB.getClasses().toMutableList()

    while(classes.isNotEmpty()) {
        val `class` = classes.first()
        val sameClasses = classes.filter { it.name.normalize() == `class`.name.normalize() }
        classes.removeAll(sameClasses)
        val channelName = `class`.name.normalizeForChannel()
        // find a channel with that name
        val channel = existingChannels.firstOrNull { it.name == channelName } ?: run {

            val newChannel = client.getGuildById(guildID).block()!!
                .createTextChannel(
                    TextChannelCreateSpec.builder()
                        .name(channelName)
                        .permissionOverwrites(listOf(
                            PermissionOverwrite.forRole(everyoneRole,
                                PermissionSet.none(),
                                PermissionSet.of(Permission.VIEW_CHANNEL)
                            )
                        ))
                        .parentId(classesCategory)
                        .build()
                ).block()!!

            send("Created channel <#" + newChannel.id.asLong() + ">")
            newChannel
        }
        val classIDs = sameClasses.map { it.id }.toSet()
        val userPermissions = DB.getPeriods()
            .filter { it.`class`in classIDs }
            .map { it.user }
            .map {
                PermissionOverwrite.forMember(Snowflake.of(it),
                    PermissionSet.of(Permission.VIEW_CHANNEL),
                    PermissionSet.none()
                )
            }
        val permissions = userPermissions + listOf(PermissionOverwrite.forRole(
            everyoneRole,
            PermissionSet.none(),
            PermissionSet.of(Permission.VIEW_CHANNEL)
        ))
        val editSpec = TextChannelEditSpec
            .create()
            .withPermissionOverwrites(permissions)

        channel.edit(editSpec).block()

        if(userPermissions.isEmpty()) {
            // no students?
            send("There seems to be no students in <#" + channel.id.asString() + ">")
        }
        existingChannels.remove(channel)
    }

    for(orphan in existingChannels) {
        send("<#" + orphan.id.asString() + "> seems to have no more users")
    }
}

val guildID: Snowflake = Snowflake.of(747884555922440263)
val classesCategory: Snowflake = Snowflake.of(876685138505904150)
val carsonID: Snowflake = Snowflake.of(293853365891235841)
val everyoneRole: Snowflake = Snowflake.of(747884555922440263)

fun String.normalizeForChannel(): String =
    this.replace(" ", "-")
        .replace(".", "-")
        .replace("/", "-")
        .lowercase()