package dev.mee42

import kotlinx.coroutines.reactive.awaitSingle
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

suspend fun <T> Mono<T>.await(): T {
    return this.awaitSingle()
}


suspend inline fun <T> measureTime(crossinline block: suspend () -> T): Pair<T, Duration> {
    val start = Instant.now()
    val t = block()
    val end = Instant.now()
    return t to Duration.between(start, end)
}