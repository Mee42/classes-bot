import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.extensions.authentication
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.sse.SseClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        val clientSecret = if(true) {
            "o9nHSKFuillLkJxmOd9ocpBfVjgsvgAn"
        } else ""
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
            .third.component1()?.takeUnless { it.isBlank()}
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
            users.add(User(id = userInfo.id, username = userInfo.username, discrim = userInfo.discriminator, classes = listOf(), grade = Grade.Senior))
        }
        // generate token only if one does not already exist
        val authToken = authTokens.asIterable().firstOrNull { (k, v) -> v == userInfo.id }?.key
            ?: generateToken()
        authTokens[authToken] = userInfo.id
        ctx.cookie("auth", authToken)
        ctx.redirect("$WEBPAGE_URL/home")
    }



    app.get("/api/user/:id") { ctx ->
        // returns information about a user with the id 'id'. if the id is @me, then return info for that user
        // does not need authentication
        val userID = getUserIDForUnauthorizedEndpoint(ctx) ?: return@get
        val user = users.firstOrNull { it.id == userID } ?: run {
            ctx.res.status = 404
            ctx.result("User $userID not found")
            return@get
        }
        ctx.result(user.toJSON())
    }


    // expects auth key if id = @me
    app.post("/api/user/set-classes") { ctx ->
        println("set class requests")
        val authToken = ctx.header("auth") ?: run {
            ctx.res.status = 400
            ctx.result("No authorization token found")
            return@post
        }
        val userID = authTokens[authToken] ?: run {
            ctx.res.status = 403
            ctx.result("authorization token is invalid")
            return@post
        }
        val user = users.firstOrNull { it.id == userID } ?: run {
            ctx.res.status = 404
            ctx.result("User $userID not found")
            return@post
        }
        // endpoint exists
        val classesSpecified = ctx.body().fromJSON<SetClassesRequest>()
        user.classes = classesSpecified.classes
        user.grade = classesSpecified.grade
    }
    app.get("/api/users") { ctx ->
        ctx.result(users.toJSON())
    }
}

fun getUserIDForUnauthorizedEndpoint(ctx: Context): UserId? {
        val idRequested = ctx.pathParam("id")
        val authToken = ctx.header("auth")
        return if(idRequested == "@me") {
            if(authToken == null) {
                ctx.res.status = 404

                ctx.result("auth token must be present with /user/@me call")
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

val authTokens = mutableMapOf<AuthToken, UserId>()
val users = mutableListOf<User>()

@Serializable
data class User(val id: UserId, val username: String, val discrim: String, var classes: List<Class>, var grade: Grade)
@Serializable
data class Class(val name: String, val teacher: String)

enum class Grade { Freshman, Sophomore, Junior, Senior }

@Serializable
class SetClassesRequest(val classes: List<Class>, val grade: Grade)

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