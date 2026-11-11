# practice 10 - coroutines

this is my practice work. it has part a (10 theory questions answered below) and part b which is a small kotlin console project called CoroutinesLab. i used kotlinx-coroutines-core 1.10.2 like in the task.

(the app module here is just the default empty android template, the actual task is in the CoroutinesLab folder)

## how to run

```bash
./gradlew :CoroutinesLab:run
```

all 5 tasks run one after another in main(). outputs are written below in each task section.

## part a. theory answers

### Q1. newSingleThreadContext vs Dispatchers.Unconfined

newSingleThreadContext("MyThread") makes a real separate thread just for your coroutine. the coroutine always runs on that thread, before delay and after delay, it never leaves it. Dispatchers.Unconfined does not have any thread of its own. the coroutine starts on whatever thread called it, and after it resumes from delay() it just continues on the thread that resumed it (for delay that is the internal timer thread). so unconfined is "continue where you were left", not "stay on one thread".

```kotlin
runBlocking {
    launch(Dispatchers.Unconfined) {
        println(Thread.currentThread().name) // main
        delay(100)
        println(Thread.currentThread().name) // DefaultExecutor thread
    }
    val ctx = newSingleThreadContext("MyThread")
    launch(ctx) {
        println(Thread.currentThread().name) // MyThread
        delay(100)
        println(Thread.currentThread().name) // still MyThread
    }
    ctx.close() // you have to close it yourself or the thread leaks
}
```

before delay: unconfined = caller thread, single thread ctx = MyThread. after delay: unconfined jumps to the resuming thread, single thread ctx is still MyThread. and newSingleThreadContext must be closed with close() when you finish.

### Q2. async vs launch

launch is fire and forget. it returns a Job and no result, you use it when you just want something done (print, update ui etc). async is for when you want a value back. it returns Deferred<T> and you call await() to get the result.

join() is for Job: it waits until the job is done, gives nothing back. if the job failed join throws the exception. await() is for Deferred: it waits AND gives you the result, also throws if it failed.

so: launch -> join(), async -> await().

### Q3. what is a coroutine, suspend vs normal, delay vs Thread.sleep

a coroutine is a small piece of code that can be suspended and resumed later without blocking a thread. they are very light, you can have thousands of them. a normal function just runs from start to end and you can't stop it in the middle. a suspend function can pause itself at a suspension point, give the thread away, and later continue from the same place.

delay() suspends: the coroutine is paused but the thread is free, other coroutines can use it. Thread.sleep() blocks: the thread itself is stuck doing nothing. so if two coroutines on one thread use delay() they both make progress and finish in about 200 ms total. if they use Thread.sleep() the first one blocks the thread for 200 ms and only then the second one can even start, so it goes one by one, ~400 ms.

### Q4. what does suspend keyword do

suspend says that this function is allowed to pause and resume. it does NOT make a new thread. the compiler just rewrites the function into a state machine and adds a hidden extra parameter continuation: Continuation<T>, which is used to save the state and to resume later. because of that a suspend function can only be called from another suspend function or from a coroutine (launch, runBlocking etc), they are the ones that have the continuation.

### Q5. concurrency vs parallelism

concurrency is about structure: many tasks are started and they interleave, each makes a bit of progress during the others time slots. it works even on one thread. parallelism is about real execution: tasks literally run at the same moment, for that you need several cores/threads.

coroutines on one thread = concurrency (like in task 1: Start, B done, A done). parallelism needs several cores, for example Dispatchers.Default has one worker per cpu core so its coroutines can really run in parallel.

### Q6. structured concurrency

structured concurrency means you don't just launch coroutines into the void, they are always started inside a scope and they form a parent-child tree. every launch/async inside a parent becomes its child and inherits its context.

rules:
- the parent waits for all its children, it can't finish before they do
- if the parent is cancelled all children are cancelled too, automatically
- if a child fails (throws), by default the parent and all sibling children are cancelled and the error goes up
- cancelling something cancels everything below it in the tree

so nothing leaks and a failure cleans up the whole branch. i also wrote a code block about how this works in my project at the bottom.

### Q7. launch, async, runBlocking, withContext, coroutineScope

launch - returns Job, does not block, for fire and forget.
async - returns Deferred<T>, does not block, for getting a value.
runBlocking - returns the result of the block, but it BLOCKS the calling thread until everything inside is done.
withContext - suspends, switches to another context/dispatcher and returns the result of the block.
coroutineScope - suspends, creates a sub-scope and waits for all children in it, returns the result of the block.

the only one that blocks the thread is runBlocking. on android ui thread that means the screen is frozen, no drawing, no touches, and after ~5 seconds the system shows an ANR dialog. so you never call runBlocking on the ui thread, you use viewModelScope.launch or similar instead.

### Q8. CoroutineScope and CoroutineContext

CoroutineScope is the thing that owns the lifecycle, it says when the coroutines are alive, and you launch from it (scope.launch {}). CoroutineContext is the data attached to a coroutine: a Job (where it sits in the parent-child tree), a dispatcher (where it runs), a CoroutineName, a CoroutineExceptionHandler. so basically scope = when, dispatcher = where.

if you don't specify anything the dispatcher is inherited from the scope. in runBlocking that is the caller thread (main in a console app), in android viewModelScope it is Dispatchers.Main.immediate.

### Q9. Default, IO, Main

Dispatchers.Default - for cpu work, like sorting a big list or parsing a big json. avoid putting long blocking io there (network/files), it would occupy the cpu workers.
Dispatchers.IO - for blocking io, like reading a file, calling a rest api, room/sqlite queries. avoid long cpu calculations there, it has many threads and you just waste them.
Dispatchers.Main - for ui, like updating text, showing a dialog. avoid anything blocking/sleeping/network there because it freezes the ui.

### Q10. viewModelScope.launch { withContext(Dispatchers.IO) { ... } }

```kotlin
viewModelScope.launch {              // 1
    withContext(Dispatchers.IO) {    // 2
        // ... load from db/network  // 3
    }                                // 4
    // update ui                     // 5
}                                    // 6
```

1. viewModelScope.launch starts a coroutine tied to the viewmodel. its default dispatcher is Dispatchers.Main.immediate, so this part runs on the main thread.
2. withContext(Dispatchers.IO) suspends the coroutine on main and moves the block to an IO worker thread (DefaultDispatcher-worker-N).
3. the loading code runs on the IO thread, meanwhile main thread is free and the ui still works.
4. when the block is done, withContext returns and the coroutine comes back to the main thread automatically.
5. everything after it runs on main again, safe for ui updates.
6. when the viewmodel is cleared, viewModelScope is cancelled, so this coroutine is cancelled at the next suspension point and will not touch the dead ui.

## part b. tasks

all code is in CoroutinesLab/src/main/kotlin/uz/ttpu/coroutineslab/Main.kt, one function per task, all called from main(). below are the real outputs i got.

### task 1. runBlocking + launch + delay

i launch coroutine A (200 ms) and B (100 ms), then print Start. output:

```
Start
B done
A done
```

optional part, same but with Thread.sleep() instead of delay():

```
Start
A done
B done
```

why the order changes: Thread.sleep() blocks the thread, delay() suspends the coroutine and frees the thread. we only have one thread, so with sleep A takes it for the whole 200 ms and B can only start sleeping after that. with delay both coroutines share the same thread and just take turns.

### task 2. suspend function with n tries

fetchName() is a suspend function, delay 100 ms, returns null the first 2 calls then "Ada". showUser(n) calls it up to n times.

happy path, showUser(3):

```
try 1 failed
try 2 failed
User: Ada
```

all tries fail, showUser(2):

```
try 1 failed
try 2 failed
Server is down, show cached user
```

optional part, calling fetchName() from a normal function does not compile, the error i got:

```
Suspend function 'fetchName' should be called only from a coroutine or another suspend function
```

### task 3. control coroutine with Job

the job prints tick 0..4 every 100 ms. i wait 250 ms and call cancelAndJoin():

```
tick 0
tick 1
tick 2
cleanup
isCancelled=true
```

cleanup comes from the finally block, it runs even on cancellation.

optional part, join() instead of cancelAndJoin(): then we just wait, all 5 ticks print and the job finishes normally:

```
tick 0
tick 1
tick 2
tick 3
tick 4
cleanup
isCancelled=false
```

### task 4. load name and age with async

loadName() and loadAge() both take ~100 ms, i measure with measureTimeMillis:

```
One by one: 208 ms
Both async, awaited later: 106 ms
await() right after each async: 203 ms
```

why does await() right after each async go back to ~200 ms: because then the second async is only created AFTER the first await() returned. so the loaders never overlap and it is sequential again. to be fast you need to first create both Deferreds and only then await them.

### task 5. find thread of each dispatcher

i print Thread.currentThread().name inside launch with different dispatchers:

```
Default:  DefaultDispatcher-worker-1
IO:       DefaultDispatcher-worker-1
Unconfined before delay:  main
Unconfined after delay:   kotlinx.coroutines.DefaultExecutor
No dispatcher:            main
MyThread before delay:    MyThread
MyThread after delay:     MyThread
```

default and io are both workers from DefaultDispatcher. unconfined starts on main but after delay it continues on the thread that resumed it. without a dispatcher the launch just inherits runBlocking context, so main. and the single thread context stays on MyThread before and after delay, i close it with close() at the end.

## demo video (max 40 sec)

<details>
<summary>click to expand/collapse demo video and scenario</summary>

![demo video](demo/demo.mp4)

what i show in the video, in order:

1. happy path, task 1: run the app, terminal prints Start, B done, A done
2. optional task 1: sleep version prints Start, A done, B done (order flipped)
3. failure path, task 2: showUser(2), retries repeat until n is exhausted: try 1 failed, try 2 failed, Server is down, show cached user
4. recovery path, task 2: showUser(3), two failed tries then User: Ada
5. task 3: ticks stop after cancelAndJoin(), isCancelled=true, cleanup from finally
6. task 4: one by one ~200 ms, both async ~100 ms, eager await ~200 ms
7. optional parts and task 5: join() prints all 5 ticks with isCancelled=false, then the thread names of each dispatcher

</details>

## how structured concurrency is handled in my repo

```kotlin
fun main() {
    runBlocking {           // root coroutine, blocks main until the whole tree is done
        launch { ... }      // child 1, inherits runBlocking context (main thread)
        launch(Dispatchers.IO) { ... } // child 2, same parent job, io dispatcher
        async { ... }       // child 3, deferred, still a child of the same scope
        val job = launch {  // child 4 (task 3)
            try { repeat(5) { tick; delay(100) } }
            finally { println("cleanup") } // still runs when this child is cancelled
        }
        job.cancelAndJoin() // cancels only this child, siblings keep running
    }
}
```

every coroutine in my project is started inside a scope, no GlobalScope anywhere. runBlocking in main() is the root, and every launch/async inside tasks is its child, so it is all one tree. the root waits for all children, that is why main() does not end while a task is still delaying. when i cancel the job of task 3 with cancelAndJoin() only that one child dies, its finally still runs, and the other tasks are not touched. so nothing leaks, and if the root ever got cancelled the whole tree would be cancelled with it.

## submission

- private repo in ttpu organization, teacher invited
- readme has all 10 answers + demo scenario + structured concurrency explanation
