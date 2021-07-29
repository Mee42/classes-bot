import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.sse.SseClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

const val API_URL = "http://localhost:7000"
const val WEBPAGE_URL = "http://localhost:3000"

fun main() {
    val app = Javalin.create { config ->
        config.enableCorsForAllOrigins()
    }.start(7000)
    app.get("/") { ctx -> ctx.result("Hello, World!") }

    val clients = ConcurrentLinkedQueue<SseClient>()
    var n = 0
    app.sse("/sse") { client ->
        clients.add(client)
        client.sendEvent("update", n.toString())
        client.onClose { clients.remove(client) }
    }
    app.post("/incr") {
        n++
        clients.forEach { it.sendEvent("update", n.toString()) }
    }

    val redirectURL = "$API_URL/api/oauth/redirect"
    // the discord url needs to be updated every time the redirect url is updated
    app.get("/api/oauth/redirect") { ctx ->
        val code = ctx.req.getParameter("code")
        println("got oauth redirect, code: $code")
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
                ctx.result("you probably did something wrong, discord returned nothing")
                return@get
            }
        println(oauthToken)



        val userInfoJson = Fuel.get("https://discord.com/api/v8/oauth2/@me")
            .authentication()
            .bearer(oauthToken.access_token)
            .responseString().third.component1()!!
        println("user info json: $userInfoJson")

        val userInfo = userInfoJson
            .fromJSON<OauthMeResponse>().user
        // TODO how do we make sure this is good?

        // does the user exist already?
        if(users.none { it.id == userInfo.id }) {
            // create the account
            users.add(
                User(
                    id = userInfo.id,
                    username = userInfo.username,
                    discrim = userInfo.discriminator,
                    grade = Grade.Senior,
                    name = ""
                )
            )
        }

        // generate token only if one does not already exist
        val authToken = authTokens.asIterable().firstOrNull { (k, v) -> v == userInfo.id }?.key
            ?: generateToken()

        authTokens[authToken] = userInfo.id
        ctx.cookie("auth", authToken)
        ctx.redirect("$WEBPAGE_URL/home")
    }





    // takes in: setclassesrequest with the ids all set to -1
    app.post("/api/user/set-classes") { ctx ->
        val authToken = ctx.header("auth") ?: run {
            ctx.res.status = 400; ctx.result("No authorization token found")
            return@post
        }
        val userID = authTokens[authToken] ?: run {
            ctx.res.status = 403; ctx.result("authorization token is invalid")
            return@post
        }
        val user = users.firstOrNull { it.id == userID } ?: run {
            ctx.res.status = 404; ctx.result("User $userID not found")
            return@post
        }
        // endpoint exists
        val classesSpecified = ctx.body().fromJSON<SetClassesRequest>()
        if(classesSpecified.classes.size != 8) {
            ctx.res.status = 400; ctx.result("there must be exactly 8 classes")
            return@post
        }
        // we need to make sure all the classes are in the classes table, and add/modify all the respective period objects
        for((clazz, period) in classesSpecified.classes.zip(1..8)) {
            // we need to make one with the right ids
            if (clazz.room == "" || clazz.teacher == "" || clazz.room == "") {
                periods.removeIf { it.user == user.id && it.period == period } // make sure they don't have an entry for that period
            } else {
               val newClass = classes.firstOrNull {
                   it.room roughEquals clazz.room &&
                   it.name roughEquals clazz.name &&
                   it.teacher roughEquals clazz.teacher
               } ?: run {
                   // we need to create a class in the database if one does not exist already
                   val c = Class(genClassID(), clazz.name, clazz.teacher, clazz.room)
                   classes.add(c)
                   c
               }
                // INSERT OR REPLACE
                periods.removeIf { it.user == user.id && it.period == period }
                periods.add(Period(period, newClass.id, user.id))
            }
        }
        user.grade = classesSpecified.grade
        user.name = classesSpecified.name
        classes.removeIf { periods.none { period -> period.`class` == it.id } }

    }

    // returns all users
    app.get("/api/users") { ctx ->
        ctx.result(users.toJSON())
    }
    // returns the user with the id :id
    app.get("/api/users/:id") { ctx -> // if :id = @me, use auth token
        val userID = getUserIDForUnauthorizedEndpoint(ctx) ?: return@get
        val user = users.firstOrNull { it.id == userID } ?: run {
            ctx.res.status = 404; ctx.result("User $userID not found"); return@get
        }
        ctx.result(user.toJSON())
    }
    // returns all classes
    app.get("/api/classes") { ctx ->
        ctx.result(classes.toJSON())
    }
    // returns a list of classes, 'null' if there's no class, in period order for the person specified by :id
    app.get("/api/classes/:id") { ctx ->
        val userID = getUserIDForUnauthorizedEndpoint(ctx) ?: return@get
        val user = users.firstOrNull { it.id == userID } ?: run {
            ctx.res.status = 404; ctx.result("User $userID not found"); return@get
        }
        ctx.result((1..8).map { period ->
            val classId = periods.firstOrNull { it.period == period && it.user == user.id }?.`class`
            classes.firstOrNull { it.id == classId } ?: Class(-1, "", "", "")
        }.toJSON())
    }
    app.get("/api/periods") { ctx ->
        ctx.result(periods.toJSON())
    }
}
/*
 schema:
 users:
   id INT
   username STRING
   discrim STRING
   grade INT
   name STRING

 classes:
   id INT
   name STRING
   teacher STRING
   room STRING
 periods:
   period INT  1-8
   user INT
   class INT
authTokens:
   token STRING
   user INT

*/

@Serializable
data class User(val id: UserId, val username: String, val discrim: String, var grade: Grade, var name: String) // all classes in this object must be part of the global classes object
@Serializable
data class Class(val id: ClassId, val name: String, val teacher: String, val room: String) // -1 -> not class, 0 -> empty class
@Serializable
data class Period(val period: Int, val `class`: ClassId, val user: UserId)

val authTokens = mutableMapOf<AuthToken, UserId>() // TODO transition to db
val users = mutableListOf<User>()
val classes = mutableListOf<Class>()
val periods = mutableSetOf<Period>()


infix fun String.roughEquals(b: String): Boolean = this.simplify() == b.simplify()
fun String.simplify() = this.replace(Regex("""[\s.\-"']"""),"").lowercase()

fun getUserIDForUnauthorizedEndpoint(ctx: Context): UserId? {
        val idRequested = ctx.pathParam("id")
        val authToken = ctx.header("auth")
        return if(idRequested == "@me") {
            if(authToken == null) {
                ctx.res.status = 404

                ctx.result("auth token must be present with a /@me call")
                return null
            }
            val userID = authTokens[authToken]
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

typealias AuthToken = String
typealias UserId = String
typealias ClassId = Int

fun genClassID(): Int {
    return (classes.maxByOrNull { it.id }?.id?.takeUnless { it == -1 } ?: 7) + 1
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
data class DiscordUSerObject(val id: String, val username: String, val avatar: String, val discriminator: String, val public_flags: Int)