package dev.mee42

import discord4j.common.util.Snowflake
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.useHandleUnchecked
import org.jdbi.v3.core.kotlin.withHandleUnchecked
import org.jdbi.v3.core.statement.StatementContext
import reactor.util.Loggers
import java.sql.ResultSet



private val logger = Loggers.getLogger("Database")

object Database {

    private val jdbi = Jdbi.create("jdbc:sqlite:data.db")!!
    fun fullReset() {
        logger.info("Performing full reset")
        jdbi.withHandleUnchecked { h ->
            with(h) {
                execute("DROP TABLE IF EXISTS users")
                execute("""
                    CREATE TABLE users (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NULL,
                        p1 INT NULL, 
                        p2 INT NULL, 
                        p3 INT NULL, 
                        p4 INT NULL, 
                        p5 INT NULL, 
                        p6 INT NULL, 
                        p7 INT NULL, 
                        p8 INT NULL, 
                        FOREIGN KEY (p1) REFERENCES classes(id), 
                        FOREIGN KEY (p2) REFERENCES classes(id), 
                        FOREIGN KEY (p3) REFERENCES classes(id), 
                        FOREIGN KEY (p4) REFERENCES classes(id),
                        FOREIGN KEY (p5) REFERENCES classes(id), 
                        FOREIGN KEY (p6) REFERENCES classes(id),
                        FOREIGN KEY (p7) REFERENCES classes(id), 
                        FOREIGN KEY (p8) REFERENCES classes(id) 
                    )
                """)
                execute("DROP TABLE IF EXISTS classes")
                execute("""
                    CREATE TABLE classes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        period INT NOT NULL,
                        name TEXT NOT NULL,
                        teacher TEXT NOT NULL
                    )
                """)
            }
        }
    }
    
    fun <R> withHandle(handle: (Handle) -> R): R {
        return jdbi.withHandleUnchecked(handle)
    }
    fun useHandle(handle: (Handle) -> Unit) {
        jdbi.useHandleUnchecked(handle)
    }


    data class User(val id: Snowflake, val name: String?, val classes: List<Int?>) {
        companion object {
            fun map(rs: ResultSet, ctx: StatementContext): User {
                return User(Snowflake.of(rs.getString("id")), rs.getString("name"), (1..8).map { "p$it" }.map { rs.getInt(it) })
            }
        }
    }
}