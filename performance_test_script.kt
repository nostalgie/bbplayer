import com.dima.kidsvideoplayer.utils.PerformanceMonitor

/**
 * Test script for performance validation
 */
object PerformanceTest {
    
    fun runPerformanceTests() {
        println("=== Starting Performance Tests ===")
        
        // Clear previous stats
        PerformanceMonitor.clear()
        
        // Test 1: Directory listing performance
        testDirectoryListing()
        
        // Test 2: Video grouping performance
        testVideoGrouping()
        
        // Test 3: Cache performance
        testCachePerformance()
        
        // Test 4: Batch operations
        testBatchOperations()
        
        // Print results
        printResults()
    }
    
    private fun testDirectoryListing() {
        println("\n--- Test 1: Directory Listing Performance ---")
        
        // Simulate listing directories of different sizes
        val smallDir = createMockDirectory(10)
        val mediumDir = createMockDirectory(100)
        val largeDir = createMockDirectory(1000)
        
        PerformanceMonitor.measureTime("smallDirectoryList") {
            simulateDirectoryListing(smallDir)
        }
        
        PerformanceMonitor.measureTime("mediumDirectoryList") {
            simulateDirectoryListing(mediumDir)
        }
        
        PerformanceMonitor.measureTime("largeDirectoryList") {
            simulateDirectoryListing(largeDir)
        }
    }
    
    private fun testVideoGrouping() {
        println("\n--- Test 2: Video Grouping Performance ---")
        
        val smallList = createMockVideoList(10)
        val mediumList = createMockVideoList(100)
        val largeList = createMockVideoList(1000)
        
        PerformanceMonitor.measureTime("smallVideoGrouping") {
            simulateVideoGrouping(smallList)
        }
        
        PerformanceMonitor.measureTime("mediumVideoGrouping") {
            simulateVideoGrouping(mediumList)
        }
        
        PerformanceMonitor.measureTime("largeVideoGrouping") {
            simulateVideoGrouping(largeList)
        }
    }
    
    private fun testCachePerformance() {
        println("\n--- Test 3: Cache Performance ---")
        
        // Test cache hits and misses
        for (i in 1..100) {
            if (i % 10 == 0) {
                // Simulate cache hit
                PerformanceMonitor.incrementCounter("cacheHits")
            } else {
                // Simulate cache miss
                PerformanceMonitor.incrementCounter("cacheMisses")
            }
        }
    }
    
    private fun testBatchOperations() {
        println("\n--- Test 4: Batch Operations Performance ---")
        
        val smallBatch = createMockVideoList(10)
        val mediumBatch = createMockVideoList(100)
        val largeBatch = createMockVideoList(500)
        
        PerformanceMonitor.measureTime("smallBatchAdd") {
            simulateBatchAdd(smallBatch)
        }
        
        PerformanceMonitor.measureTime("mediumBatchAdd") {
            simulateBatchAdd(mediumBatch)
        }
        
        PerformanceMonitor.measureTime("largeBatchAdd") {
            simulateBatchAdd(largeBatch)
        }
    }
    
    private fun printResults() {
        println("\n=== Performance Test Results ===")
        
        // Print timing results
        println("\n--- Timing Results ---")
        println("Small directory list: ${PerformanceMonitor.getCounter("smallDirectoryList")}ms")
        println("Medium directory list: ${PerformanceMonitor.getCounter("mediumDirectoryList")}ms")
        println("Large directory list: ${PerformanceMonitor.getCounter("largeDirectoryList")}ms")
        
        println("Small video grouping: ${PerformanceMonitor.getCounter("smallVideoGrouping")}ms")
        println("Medium video grouping: ${PerformanceMonitor.getCounter("mediumVideoGrouping")}ms")
        println("Large video grouping: ${PerformanceMonitor.getCounter("largeVideoGrouping")}ms")
        
        println("Small batch add: ${PerformanceMonitor.getCounter("smallBatchAdd")}ms")
        println("Medium batch add: ${PerformanceMonitor.getCounter("mediumBatchAdd")}ms")
        println("Large batch add: ${PerformanceMonitor.getCounter("largeBatchAdd")}ms")
        
        // Print operation counts
        println("\n--- Operation Counts ---")
        println("Total directory list operations: ${PerformanceMonitor.getCounter("directoryListOperations")}")
        println("Total video grouping operations: ${PerformanceMonitor.getCounter("totalGroupOperations")}")
        println("Total cache hits: ${PerformanceMonitor.getCounter("cacheHits")}")
        println("Total cache misses: ${PerformanceMonitor.getCounter("cacheMisses")}")
        println("Total batch operations: ${PerformanceMonitor.getCounter("batchOperations")}")
        
        // Print cache statistics
        PerformanceMonitor.logCounters()
        
        // Performance assessment
        println("\n--- Performance Assessment ---")
        assessPerformance()
    }
    
    private fun assessPerformance() {
        val largeDirTime = PerformanceMonitor.getCounter("largeDirectoryList")
        val largeGroupTime = PerformanceMonitor.getCounter("largeVideoGrouping")
        val largeBatchTime = PerformanceMonitor.getCounter("largeBatchAdd")
        
        println("Large directory list time: ${largeDirTime}ms (Target: <5000ms)")
        println("Large video grouping time: ${largeGroupTime}ms (Target: <3000ms)")
        println("Large batch add time: ${largeBatchTime}ms (Target: <2000ms)")
        
        if (largeDirTime < 5000 && largeGroupTime < 3000 && largeBatchTime < 2000) {
            println("✅ All performance targets met!")
        } else {
            println("❌ Some performance targets not met")
        }
        
        val cacheHitRate = PerformanceMonitor.CacheStats.getHitRate()
        println("Cache hit rate: ${String.format("%.2f%%", cacheHitRate * 100)} (Target: >70%)")
        
        if (cacheHitRate > 0.7) {
            println("✅ Cache performance is good!")
        } else {
            println("❌ Cache performance needs improvement")
        }
    }
    
    // Helper functions for simulation
    private fun createMockDirectory(size: Int): List<String> {
        return (1..size).map { "file_$it.mp4" }
    }
    
    private fun createMockVideoList(size: Int): List<String> {
        return (1..size).map { 
            "file:///storage/emulated/0/Movies/video_$it.mp4" 
        }
    }
    
    private fun simulateDirectoryListing(files: List<String>) {
        // Simulate directory listing operations
        Thread.sleep(10) // Simulate processing time
    }
    
    private fun simulateVideoGrouping(videos: List<String>) {
        // Simulate video grouping operations
        Thread.sleep(5) // Simulate processing time
    }
    
    private fun simulateBatchAdd(videos: List<String>) {
        // Simulate batch add operations
        Thread.sleep(2) // Simulate processing time
    }
}

// Main function to run tests
fun main() {
    PerformanceTest.runPerformanceTests()
}