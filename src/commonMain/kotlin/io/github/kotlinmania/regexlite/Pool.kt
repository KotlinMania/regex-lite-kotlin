// port-lint: source pool.rs
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.kotlinmania.regexlite

import kotlin.concurrent.atomics.AtomicReference

/**
 * A thread-safe pool.
 */
internal class Pool<T>(
    private val create: () -> T,
) {
    private class Node<T>(
        val value: T,
        val next: Node<T>?,
    )

    private val head = AtomicReference<Node<T>?>(null)

    /**
     * Get a value from the pool.
     */
    fun get(): PoolGuard<T> {
        while (true) {
            val current = head.load()
            if (current == null) {
                return PoolGuard(this, create())
            }
            if (head.compareAndSet(current, current.next)) {
                return PoolGuard(this, current.value)
            }
        }
    }

    /**
     * Puts a value back into the pool. Callers don't need to call this.
     * Once the guard that's returned by 'get' is released / closed, it is put back
     * into the pool automatically.
     */
    internal fun putValue(value: T) {
        while (true) {
            val current = head.load()
            val newNode = Node(value, current)
            if (head.compareAndSet(current, newNode)) {
                return
            }
        }
    }

    override fun toString(): String = "Pool"
}

/**
 * A guard that is returned when a caller requests a value from the pool.
 */
internal class PoolGuard<T>(
    private val pool: Pool<T>?,
    var value: T,
) : AutoCloseable {
    private var returned = false

    /**
     * Releases the pooled value back to the pool.
     */
    fun release() {
        if (!returned) {
            returned = true
            pool?.putValue(value)
        }
    }

    override fun close() {
        release()
    }

    override fun toString(): String = "PoolGuard(value=$value)"
}
