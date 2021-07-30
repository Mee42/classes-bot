import kotlinx.serialization.Serializable


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
data class Class(val id: ClassId, val name: String, val teacher: String, val room: String) // -1 -> not class, 0 -> empty class
@Serializable
data class Period(val period: Int, val `class`: ClassId, val user: UserId)

private val authTokens = mutableMapOf<AuthToken, UserId>() // TODO transition to db
private val users = mutableListOf<User>()
private val classes = mutableListOf<Class>()
private val periods = mutableSetOf<Period>()

object DB

fun DB.genClassID(): Int {
    return (classes.maxByOrNull { it.id }?.id?.takeUnless { it == -1 } ?: 7) + 1
}
// if user exists already, don't insert
fun DB.insertIfDoesNotExist(user: User) {
    if (users.none { it.id == user.id }) {
        users.add(user)
    }
}

fun DB.updateUser(user: UserId, grade: Grade, name: String) {
    val existingUser = users.removeAt(users.indexOfFirst { it.id == user })
    users.add(existingUser.copy(grade = grade, name = name))
}

fun DB.insertAuthToken(authToken: AuthToken, userId: UserId) {
    authTokens[authToken] = userId
}
fun DB.lookupAuthToken(token: AuthToken): UserId? {
    return authTokens[token]
}
fun DB.lookupUserById(userId: UserId): User? {
    return users.firstOrNull { it.id == userId }
}
fun DB.cleanClasses() {
    classes.removeIf { clazz -> periods.none { period -> period.`class` == clazz.id } }
}
fun DB.removeAllPeriodsWithUserid(userId: UserId) {
    periods.removeIf { it.user == userId }
}
fun DB.insertPeriod(period: Period) {
    periods.add(period)
}
fun DB.fuzzyClassLookup(name: String, teacher: String, room: String): Class? {
    return classes.firstOrNull {
        it.room roughEquals room &&
                it.name roughEquals name &&
                it.teacher roughEquals teacher
    }
}
fun DB.insertClass(clazz: Class) {
    classes.add(clazz)
}
fun DB.getClasses(): List<Class> {
    return classes
}
fun DB.getUsers(): List<User> {
    return users
}
fun DB.getPeriods(): Set<Period> {
    return periods
}
