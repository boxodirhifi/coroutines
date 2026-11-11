package uz.ttpu.coroutineslab

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlin.system.measureTimeMillis

// Task 1: two launched coroutines, delay() suspends instead of blocking
fun task1(blocking: Boolean = false) = runBlocking {
    launch {
        if (blocking) Thread.sleep(200) else delay(200)
        println("A done")
    }
    launch {
        if (blocking) Thread.sleep(100) else delay(100)
        println("B done")
    }
    println("Start")
}

// Task 2: fetchName() fails the first 2 times, then returns "Ada"
private var callCount = 0

suspend fun fetchName(): String? {
    delay(100)
    callCount++
    return if (callCount <= 2) null else "Ada"
}

suspend fun showUser(n: Int) {
    for (k in 1..n) {
        val name = fetchName()
        if (name != null) {
            println("User: $name")
            return
        }
        println("try $k failed")
    }
    println("Server is down, show cached user")
}

// Task 3: cancelEarly = cancelAndJoin() after 250 ms, otherwise join() to the end
fun task3(cancelEarly: Boolean = true) = runBlocking {
    val job = launch {
        try {
            repeat(5) {
                println("tick $it")
                delay(100)
            }
        } finally {
            println("cleanup")
        }
    }
    if (cancelEarly) {
        delay(250)
        job.cancelAndJoin()
    } else {
        job.join()
    }
    println("isCancelled=${job.isCancelled}")
}

// Task 4: loadName() and loadAge() take ~100 ms each
private suspend fun loadName(): String {
    delay(100)
    return "Ada"
}

private suspend fun loadAge(): Int {
    delay(100)
    return 36
}

fun task4() = runBlocking {
    val oneByOne = measureTimeMillis {
        val name = loadName()
        val age = loadAge()
        println("User: $name, age $age")
    }
    println("One by one: $oneByOne ms")

    val bothAsync = measureTimeMillis {
        val name = async { loadName() }
        val age = async { loadAge() }
        println("User: ${name.await()}, age ${age.await()}")
    }
    println("Both async, awaited later: $bothAsync ms")

    val eagerAwait = measureTimeMillis {
        // the second async starts only after the first await() has returned
        val name = async { loadName() }.await()
        val age = async { loadAge() }.await()
        println("User: $name, age $age")
    }
    println("await() right after each async: $eagerAwait ms")
}

// Task 5: which thread each dispatcher runs on
fun task5() = runBlocking {
    launch(Dispatchers.Default) {
        println("Default:  ${Thread.currentThread().name}")
    }
    launch(Dispatchers.IO) {
        println("IO:       ${Thread.currentThread().name}")
    }
    launch(Dispatchers.Unconfined) {
        println("Unconfined before delay:  ${Thread.currentThread().name}")
        delay(100)
        println("Unconfined after delay:   ${Thread.currentThread().name}")
    }
    launch {
        println("No dispatcher:            ${Thread.currentThread().name}")
    }
    val myThread = newSingleThreadContext("MyThread")
    launch(myThread) {
        println("MyThread before delay:    ${Thread.currentThread().name}")
        delay(100)
        println("MyThread after delay:     ${Thread.currentThread().name}")
    }.join()
    myThread.close() // manually created context must be closed when finished
}

fun main() {
    println("Task 1: runBlocking + launch + delay")
    task1()

    println("\nTask 1 optional: delay replaced with Thread.sleep")
    task1(blocking = true)

    println("\nTask 2 happy path: showUser(3)")
    callCount = 0
    runBlocking { showUser(3) }

    println("\nTask 2 all tries fail: showUser(2)")
    callCount = 0
    runBlocking { showUser(2) }

    println("\nTask 3: cancelAndJoin() after 250 ms")
    task3()

    println("\nTask 3 optional: join() instead")
    task3(cancelEarly = false)

    println("\nTask 4: async vs one by one")
    task4()

    println("\nTask 5: thread of each dispatcher")
    task5()
}
