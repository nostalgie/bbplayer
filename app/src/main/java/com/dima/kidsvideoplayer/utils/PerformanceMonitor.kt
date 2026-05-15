package com.dima.kidsvideoplayer.utils

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Performance monitoring utility for measuring app performance
 */
object PerformanceMonitor {
    private const val TAG = "PerformanceMonitor"
    
    // Simple timers for measuring execution time
    private val timers = ConcurrentHashMap<String, AtomicLong>()
    private val mutex = Mutex()
    
    // Performance counters
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    
    // Cache hit/miss tracking
    object CacheStats {
        val hits = AtomicLong(0)
        val misses = AtomicLong(0)
        
        fun recordHit() {
            hits.incrementAndGet()
        }
        
        fun recordMiss() {
            misses.incrementAndGet()
        }
        
        fun getHitRate(): Float {
            val total = hits.get() + misses.get()
            return if (total > 0) hits.get().toFloat() / total else 0f
        }
    }
    
    /**
     * Start a timer with the given name
     */
    fun startTimer(name: String) {
        timers[name] = AtomicLong(System.currentTimeMillis())
    }
    
    /**
     * Stop a timer and return the elapsed time in milliseconds
     */
    fun stopTimer(name: String): Long {
        val startTime = timers[name]?.get()
        if (startTime != null) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Timer '$name': ${elapsed}ms")
            timers.remove(name)
            return elapsed
        }
        Log.w(TAG, "Timer '$name' not found")
        return -1L
    }
    
    /**
     * Increment a counter
     */
    fun incrementCounter(name: String, delta: Long = 1) {
        counters.getOrPut(name) { AtomicLong(0) }.addAndGet(delta)
    }
    
    /**
     * Get counter value
     */
    fun getCounter(name: String): Long {
        return counters[name]?.get() ?: 0L
    }
    
    /**
     * Log all counters
     */
    fun logCounters() {
        Log.d(TAG, "Performance Counters:")
        counters.forEach { (name, counter) ->
            Log.d(TAG, "  $name: ${counter.get()}")
        }
        Log.d(TAG, "Cache Hit Rate: ${CacheStats.getHitRate() * 100}% (${CacheStats.hits.get()} hits, ${CacheStats.misses.get()} misses)")
    }
    
    /**
     * Clear all timers and counters
     */
    fun clear() {
        timers.clear()
        counters.clear()
        CacheStats.hits.set(0)
        CacheStats.misses.set(0)
    }
    
    /**
     * Measure execution time of a block
     */
    inline fun measureTime(name: String, block: () -> Unit) {
        startTimer(name)
        try {
            block()
        } finally {
            stopTimer(name)
        }
    }
    
    /**
     * Measure execution time and return the result
     */
    inline fun <T> measureTime(name: String, block: () -> T): T {
        startTimer(name)
        return try {
            block()
        } finally {
            stopTimer(name)
        }
    }
}