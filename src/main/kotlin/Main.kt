package dev.mee42

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import io.javalin.Javalin
import io.javalin.http.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

const val API_URL = "https://classes.carson.sh"
const val WEBPAGE_URL = "https://classes.carson.sh"

val Context.logger: Logger
    get() = LoggerFactory.getLogger(this.path())

fun main() {
    DB.initTables()
    val app = Javalin.create { config ->
        config.enableCorsForAllOrigins()
    }.start(8002)
    app.before { ctx ->
        ctx.logger.info("==> " + ctx.ip())
    }
    app.after { ctx ->
        if(ctx.status() != 200) {
            ctx.logger.info("<== " + ctx.status())
        }
    }
    app.get("/") { ctx ->
        ctx.result("Hello, World!")
    }

    val redirectURL = "$API_URL/api/oauth/redirect"
    // the discord url needs to be updated every time the redirect url is updated
    app.get("/api/oauth/redirect") { ctx ->
        val code: String? = ctx.req.getParameter("code")
        ctx.logger.debug("got oauth redirect, code: $code")
        if(code == null) {
            ctx.logger.warn("Code was null")
            ctx.res.status = 400
            ctx.result("No code")
            return@get
        }
        val clientSecret = File("clientsecret.txt").readLines()[0]
        val clientId = "747884756011712705"
        // make http request
        val data = mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "grant_type" to "authorization_code",
            "code" to code,
            "redirect_uri" to redirectURL
        )

        fun encode(str: String) = str.replace(" ", "+")
        val encoded = data.map { (key, value) -> encode(key) + "=" + encode(value) }.joinToString(separator = "&")


        val oauthToken = Fuel.post("https://discord.com/api/v8/oauth2/token")
            .body(encoded)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .responseString()
            .third.component1()?.takeUnless { it.isBlank() }
            ?.fromJSON<OauthRedirectResponse>() ?: run {
            ctx.res.status = 404
            ctx.result("You probably did something wrong, discord returned nothing")
            ctx.logger.warn("Discord return nothing for oauth token request")
            return@get
        }


        val userInfoJson = Fuel.get("https://discord.com/api/v8/oauth2/@me")
            .authentication()
            .bearer(oauthToken.access_token)
            .responseString().third.component1()!!

        ctx.logger.debug("user info json: $userInfoJson")

        val userInfo = userInfoJson
            .fromJSON<OauthMeResponse>().user

        // does the user exist already?
        DB.insertIfDoesNotExist(
            User(
                id = userInfo.id,
                username = userInfo.username,
                discrim = userInfo.discriminator,
                grade = Grade.Senior,
                name = ""
            )
        )


        // generate token only if one does not already exist
        val authToken = generateToken()
        DB.insertAuthToken(authToken, userInfo.id)
        ctx.cookie("auth", authToken)
        ctx.redirect("$WEBPAGE_URL/home")
    }


    // takes in: setclassesrequest with the ids all set to -1
    app.post("/api/user/set-classes") { ctx ->
        val authToken = ctx.header("auth") ?: run {
            ctx.res.status = 400; ctx.result("No authorization token found")
            ctx.logger.warn("No authorization token found")
            return@post
        }
        val userID = DB.lookupAuthToken(authToken) ?: run {
            ctx.res.status = 403; ctx.result("Authorization token is invalid")
            ctx.logger.warn("Authorization token is invalid")
            return@post
        }
        val user = DB.lookupUserById(userID) ?: run {
            ctx.res.status = 404; ctx.result("User $userID not found")
            ctx.logger.warn("User $userID not found")
            return@post
        }
        // endpoint exists
        val setClassesReq = ctx.body().fromJSON<SetClassesRequest>()
        if (setClassesReq.classes.size != 8) {
            ctx.res.status = 400; ctx.result("there must be exactly 8 classes")
            ctx.logger.warn("There must be exactly 8 classes, got ${setClassesReq.classes.size}")
            return@post
        }
        DB.removeAllPeriodsWithUserid(userID)
        // we need to make sure all the classes are in the classes table, and add all the respective period objects
        for ((clazz, period) in setClassesReq.classes.zip(1..8)) {
            // we need to make one with the right ids
            if (clazz.room == "" || clazz.teacher == "" || clazz.room == "") {
                // do nothing
            } else {
                val classAlreadyExists = DB.fuzzyClassLookup(room = clazz.room, name = clazz.name, teacher = clazz.teacher)
                val classID = if (classAlreadyExists == null) {
                    // we need to create a class
                    val newID = DB.genClassID()
                    DB.insertClass(Class(id = newID, room = clazz.room, name = clazz.name, teacher = clazz.teacher))
                    newID
                } else classAlreadyExists.id
                DB.insertPeriod(Period(period, classID, user.id))
            }
        }
        DB.updateUser(userID, setClassesReq.grade, setClassesReq.name)
        DB.cleanClasses()
    }

    // returns all users
    app.get("/api/users") { ctx ->
        ctx.result(DB.getUsers().toJSON())
    }
    // returns the user with the id :id
    app.get("/api/users/:id") { ctx -> // if :id = @me, use auth token
        val user =  getUserForUnauthorizedEndpoint(ctx) ?: return@get
        ctx.result(user.toJSON())
    }
    // returns all classes
    app.get("/api/classes") { ctx ->
        ctx.result(DB.getClasses().toJSON())
    }
    // returns a list of classes, 'null' if there's no class, in period order for the person specified by :id
    app.get("/api/classes/:id") { ctx ->
        val user = getUserForUnauthorizedEndpoint(ctx) ?: return@get
        ctx.result(DB.getSchedule(user.id).toJSON())
    }
    app.get("/api/periods") { ctx ->
        ctx.result(DB.getPeriods().toJSON())
    }
    discordInit()
}


infix fun String.roughEquals(b: String): Boolean = this.normalize() == b.normalize()
fun String.normalize() = this.replace(Regex("""[\s.\-"']"""),"").lowercase()

// returns null if you should return
fun getUserForUnauthorizedEndpoint(ctx: Context): User? {
    val userID = getUserIDForUnauthorizedEndpoint(ctx) ?: return null
    val user = DB.lookupUserById(userID)
    if(user == null) {
        ctx.res.status = 404;
        ctx.result("User $userID not found");
        ctx.logger.info("User $userID not found")
    }
    return user
}
fun getUserIDForUnauthorizedEndpoint(ctx: Context): UserId? {
        val idRequested = ctx.pathParam("id")
        val authToken = ctx.header("auth")
        return if(idRequested == "@me") {
            if(authToken == null) {
                ctx.res.status = 404

                ctx.result("auth token must be present with a /@me call")
                return null
            }
            val userID = DB.lookupAuthToken(authToken)
            if(userID == null) {
                ctx.res.status = 401
                ctx.result("auth token does not exist")
                return null
            }
            userID
        } else idRequested
}


fun generateToken(): String {
    val s = StringBuilder(10)
    for(n in 0 until 10) s.append(('a'..'z').random())
    return s.toString()
}

enum class Grade { Freshman, Sophomore, Junior, Senior }

@Serializable
class SetClassesRequest(
    val classes: List<Class>,
    val grade: Grade,
    val name: String) // in this request, the class id should be set to -1, as it is unknown

val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}
inline fun <reified T> String.fromJSON(): T = json.decodeFromString(this)
inline fun <reified T> T.toJSON(): String = json.encodeToString(this)


@Serializable
data class OauthRedirectResponse(val access_token: String,val expires_in: Int,val refresh_token: String,val scope: String,val token_type: String)
@Serializable
data class OauthMeResponse(val user: DiscordUSerObject)
@Serializable
data class DiscordUSerObject(val id: String, val username: String, val avatar: String?, val discriminator: String, val public_flags: Int)
