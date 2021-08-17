package dev.mee42

import discord4j.common.util.Snowflake
import kotlinx.serialization.Serializable
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.kotlin.mapTo
import org.jdbi.v3.core.result.ResultProducer
import org.jdbi.v3.core.statement.Slf4JSqlLogger
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.core.statement.UnableToCreateStatementException
import org.jdbi.v3.sqlite3.SQLitePlugin
import java.sql.PreparedStatement
import java.util.function.Supplier


typealias AuthToken = String
typealias UserId = String
typealias ClassId = Int


/*
 schema:
 users:
   id INT PRIMARY KEY
   username STRING
   discrim STRING
   grade INT
   name STRING

 classes:
   id INT PRIMARY KEY
   name STRING
   teacher STRING
   room STRING
 periods:
   period INT  1-8
   user INT
   class INT
  authTokens:
   token STRING PRIMARY KEY
   user INT

*/
@Serializable
data class User(val id: UserId, val username: String, val discrim: String, val grade: Grade, val name: String) // all classes in this object must be part of the global classes object
@Serializable
data class Class(val id: ClassId,
                 val name: String,
                 val teacher: String,
                 val room: String) // -1 -> not class, 0 -> empty class
@Serializable
data class Period(val period: Int, val `class`: ClassId, val user: UserId)


object DB {
    private val jdbi = Jdbi.create("jdbc:sqlite:test.db")
        .installPlugin(KotlinPlugin())
        .installPlugin(SQLitePlugin())
        .setSqlLogger(Slf4JSqlLogger())
        .installPlugins()
    fun getJDBIInternal(): Jdbi = jdbi
}
private data class HandleWrapper(val h: Handle)

private fun <T> DB.withHandle(f: HandleWrapper.() -> T): T {
    return getJDBIInternal().withHandle<T, Exception> { h -> f(HandleWrapper(h)) }
}
private fun DB.useHandle(f: HandleWrapper.() -> Unit) {
    getJDBIInternal().useHandle<Exception> { h -> f(HandleWrapper(h)) }
}



fun DB.initTables() {
    withHandle {
        h.execute("""
            CREATE TABLE IF NOT EXISTS tokens (
                token VARCHAR PRIMARY KEY,
                user INT NOT NULL,
                FOREIGN KEY(user) REFERENCES users(id)
            );
            """)
        h.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INT PRIMARY KEY,
                username VARCHAR NOT NULL,
                discrim VARCHAR NOT NULL,
                grade INT NOT NULL,
                name VARCHAR NOT NULL
            );
            """)
        h.execute("""
            CREATE TABLE IF NOT EXISTS classes (
                id INT PRIMARY KEY,
                name VARCHAR NOT NULL,
                normalized_name VARCHAR NOT NULL,
                teacher VARCHAR NOT NULL,
                normalized_teacher VARCHAR NOT NULL,
                room VARCHAR NOT NULL,
                normalized_room VARCHAR NOT NULL
            );
            """)
        h.execute("""
            CREATE TABLE IF NOT EXISTS periods (
              period INT NOT NULL,
              user INT NOT NULL,
              class INT NOT NULL,
              FOREIGN KEY(user) REFERENCES users(id),
              FOREIGN KEY(class) REFERENCES classes(id)
            )
        """)
        h.execute("""
            CREATE TABLE IF NOT EXISTS channels (
                id INT NOT NULL,
                classname VARCHAR NOT NULL,
                normalized_classname VARCHAR NOT NULL,
                trash INT NOT NULL
            )
        """.trimIndent())
    }
}


fun DB.genClassID(): Int {
    return (getClasses().maxByOrNull { it.id }?.id?.takeUnless { it == -1 } ?: 7) + 3
}
// if user exists already, don't insert
fun DB.insertIfDoesNotExist(user: User) = useHandle {
    val alreadyExists = h.createQuery("SELECT 1 FROM users WHERE id = :id")
        .bind("id", user.id)
        .mapTo<Int>().firstOrNull() == 1
    if(!alreadyExists) {
        h.createUpdate("INSERT INTO users (id, username, discrim, name, grade) VALUES (:id, :username, :discrim, :name, :grade)")
            .bindBean(user)
            .execute()
    }
}

fun DB.updateUser(user: UserId, grade: Grade, name: String) = useHandle {
    h.createUpdate("UPDATE users SET grade = :grade, name = :name WHERE id = :id")
        .bind("id", user)
        .bind("grade", grade)
        .bind("name", name)
        .execute()
}

fun DB.insertAuthToken(authToken: AuthToken, userId: UserId) = useHandle {
    h.createUpdate("INSERT INTO tokens (token, user) VALUES (:token, :user)")
        .bind("token", authToken)
        .bind("user", userId)
        .execute()
}
fun DB.lookupAuthToken(token: AuthToken): UserId? = withHandle {
    h.createQuery("SELECT user FROM tokens WHERE token = :token")
        .bind("token", token)
        .mapTo<UserId>()
        .firstOrNull()
}
fun DB.lookupUserById(userId: UserId): User? = withHandle {
    h.createQuery("SELECT * FROM users WHERE id = :id")
        .bind("id", userId)
        .mapTo<User>()
        .firstOrNull()
}
fun DB.cleanClasses() = useHandle {
    h.execute("DELETE FROM classes WHERE NOT EXISTS (SELECT 1 FROM periods WHERE periods.class = classes.id LIMIT 1)")
}
fun DB.removeAllPeriodsWithUserid(userId: UserId) = useHandle {
    h.execute("DELETE FROM periods WHERE user = ?", userId)
}
fun DB.insertPeriod(period: Period) = useHandle {
    h.createUpdate("INSERT INTO periods (period, user, class) VALUES (:period, :user, :class)")
        .bindBean(period)
        .bind("class", period.`class`)
        .execute()
}
fun DB.fuzzyClassLookup(name: String, teacher: String, room: String): Class? = withHandle {
    h.createQuery("SELECT id, name, teacher, room FROM classes WHERE normalized_name = :n AND normalized_teacher = :t AND normalized_room = :r LIMIT 1")
        .bind("n", name.normalize())
        .bind("t", teacher.normalize())
        .bind("r", room.normalize())
        .mapTo<Class>()
        .firstOrNull()
}
fun DB.insertClass(clazz: Class) = useHandle {
    h.createUpdate("INSERT INTO classes (id, name, normalized_name, teacher, normalized_teacher, room, normalized_room)" +
                    " VALUES (:id, :name, :normalized_name, :teacher, :normalized_teacher, :room, :normalized_room)")
         .bind("id",clazz.id)
         .bind("name", clazz.name)
         .bind("teacher", clazz.teacher)
         .bind("room", clazz.room)
         .bind("normalized_name", clazz.name.normalize())
         .bind("normalized_teacher", clazz.teacher.normalize())
         .bind("normalized_room", clazz.room.normalize())
         .execute()
}
fun DB.getClasses(): List<Class> = withHandle {
    h.createQuery("SELECT * FROM classes")
        .mapTo<Class>()
        .list()
}
fun DB.getUsers(): List<User> = withHandle {
    h.createQuery("SELECT * FROM users")
        .mapTo<User>()
        .list()
}
fun DB.getPeriods(): List<Period> = withHandle {
    h.createQuery("SELECT * FROM periods")
        .mapTo<Period>()
        .list()
}
fun DB.getSchedule(user: UserId): List<Class> {
    return (1..8).map { period ->
        // todo optimize if needed
        val classId = DB.getPeriods().firstOrNull { it.period == period && it.user == user }?.`class`
        DB.getClasses().firstOrNull { it.id == classId } ?: Class(-1, "", "", "")
    }
}
