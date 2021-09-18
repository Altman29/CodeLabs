package com.zeen.coroutinelib

import kotlinx.coroutines.*

/**
 * 协程总是在由 Kotlin 标准库中定义的 CoroutineContext 表示的某个上下文中执行协程上下文包含多种子元素。
 * 主要的元素是协程作业（Job，我们之前见过），以及它的调度器（Dispatche，本节将介绍）
 */


/**
 * 1.调度器和线程(Dispatchers and threads)
 * 协程上下文(coroutine context)包含一个协程调度器(参阅CoroutineDispatcher)，协程调度器用于确定执行协程的目标载体，即运行于哪个线程，
 * 包含一个还是多个线程。协程调度器可以将协程的执行操作限制在特定线程上，也可以将其分派到线程池中，或者让它无限制的运行。
 *
 * 所有协程构造器(如launch和async)都接受一个可选参数，即CoroutineContext，该参数可用于显式指定要创建的协程和其它上下文元素所要使用的调度器。
 * 示例如下：
 */
fun main41() = runBlocking<Unit> {
//sampleStart
    launch { // context of the parent, main runBlocking coroutine
        println("main runBlocking      : I'm working in thread ${Thread.currentThread().name}")
    }
    launch(Dispatchers.Unconfined) { // not confined -- will work with main thread
        println("Unconfined            : I'm working in thread ${Thread.currentThread().name}")
    }
    launch(Dispatchers.Default) { // will get dispatched to DefaultDispatcher
        println("Default               : I'm working in thread ${Thread.currentThread().name}")
    }
    launch(newSingleThreadContext("MyOwnThread")) { // will get its own new thread
        println("newSingleThreadContext: I'm working in thread ${Thread.currentThread().name}")
    }
//sampleEnd

    /**
     * >>
     * Unconfined            : I'm working in thread main
     * Default               : I'm working in thread DefaultDispatcher-worker-1
     * main runBlocking      : I'm working in thread main
     * newSingleThreadContext: I'm working in thread MyOwnThread
     *
     *
    当 ```launch {...}``` 在不带参数的情况下使用时，它从外部的协程作用域继承上下文和调度器。在本例中，它继承于在主线程中中运行的
    runBlocking 协程的上下文

    Dispatchers.Unconfined 是一个特殊的调度器，看起来似乎也在主线程中运行，但实际上它是一种不同的机制，稍后将进行解释

    在 GlobalScope 中启动协程时默认使用的调度器是 Dispatchers.default，并使用共享的后台线程池，因此 ```launch(Dispatchers.default){...}```
    与 ```GlobalScope.launch{...}``` 是使用相同的调度器

    newSingleThreadContext 用于为协程专门创建一个新的线程来运行。专用线程是非常昂贵的资源。在实际的应用程序中，它必须在不再需要时使用 close 函数释放掉，
    或者存储在顶级变量中以此实现在整个应用程序中重用
     */
}

/**
 * 2.Unconfined调度器 vs confined调度器
 * Dispatchers.Unconfined调度器在调用者线程中启动一个协程，但它仅仅只是运行到第一个挂起点。在挂起之后，它将回复线程中的协程，该协程完全由调用的挂起
 * 函数决定。Unconfined调度器适用于既不消耗CPU时间和不更新任何受限于特定线程的共享数据(如UI)的协程
 *
 * 另一方面，调度器是默认继承于外部的协程作用域的。尤其是runBlocking启动的调度器只能是调用者所在的线程，因此集成runBlocking的结果是在此线程上的
 * 调度执行操作是可预测的FIFO。
 *
 *
 */
fun main42() = runBlocking<Unit> {
    //not confined -- will work with main thread
    launch(Dispatchers.Unconfined) {
        println("Unconfined: I'm working in thread ${Thread.currentThread().name}")
        delay(500)
        println("Unconfined: After delay in thread ${Thread.currentThread().name}")
    }
    //context of parent,main runBlocking coroutine
    launch {
        println("main runBlocking: I'm working in thread ${Thread.currentThread().name}")
        delay(1000)
        println("main runBlocking: After delay in thread ${Thread.currentThread().name}")
    }

    /**
     * >>
     * Unconfined      : I'm working in thread main
     * main runBlocking: I'm working in thread main
     * Unconfined      : After delay in thread kotlinx.coroutines.DefaultExecutor
     * main runBlocking: After delay in thread main
     *
     * 因此，从runBlocking{...}继承了上下文的协程继续在主线程中执行，而调度器是unconfined的协程，在delay函数之后的代码则默认运行与delay函数
     * 所使用的运行线程。
     * unconfined调度器是一种高级机制，可以在某些极端情况下提供帮助而不需要调度协程以便稍后执行或产生不希望的副作用，因为某些操作必须立即在协程中
     * 执行。非受限调度器不应该在一般代码中使用。
     */
}

/**
 * 3.调式协程和线程(Debugging coroutines and threads)
 * 协程可以在一个线程上挂起，在另一个线程上继续执行。即使使用单线程的调度器，也可能很难明确知道协程当前在做什么/在哪里/处于什么状态。调试线程的
 * 常用方法是在日志文件中为每一条日志语句加上线程名，日志框架普遍支持此功能。当使用协程时，线程名本身没有提供太多的上下文信息。因此kotlinx.coroutines
 * 包含了调试工具以便使协程调式起来更加容易。
 * 开启JVM的-Dkotlinx.coroutines.debug配置后运行一下代码：
 */
fun log(msg: String) = println("[${Thread.currentThread().name}] $msg")

fun main43() = runBlocking<Unit> {
//sampleStart
    val a = async {
        log("I'm computing a piece of the answer")
        6
    }
    val b = async {
        log("I'm computing another piece of the answer")
        7
    }
    log("The answer is ${a.await() * b.await()}")
    //sampleEnd

    /**
     * >>
     * [main @coroutine#2] I'm computing a piece of the answer
     * [main @coroutine#3] I'm computing another piece of the answer
     * [main @coroutine#1] The answer is 42
     */
}

/**
 * 4.在线程间切换（jumping between threads）
 */
fun main44() {
//sampleStart
    newSingleThreadContext("Ctx1").use { ctx1 ->
        newSingleThreadContext("Ctx2").use { ctx2 ->
            runBlocking(ctx1) {
                log("Started in ctx1")
                withContext(ctx2) {
                    log("Working in ctx2")
                }
                log("Back to ctx1")
            }
        }
    }
//sampleEnd

    /**
     *
     * 这里演示了几种新技巧。一个是对显示指定的上下文使用 runBlocking，另一个是使用 withContext 函数更改协程的上下文并同时仍然保持在另一个协程中，
     * 如你在下面的输出中所看到的
     * >>
     * [Ctx1 @coroutine#1] Started in ctx1
     * [Ctx2 @coroutine#1] Working in ctx2
     * [Ctx1 @coroutine#1] Back to ctx1
     *
     * 注意，本例还使用了 kotlin 标准库中的 ```use``` 函数用来在不再需要时释放 newSingleThreadContext 所创建的线程
     */
}


/**
 * 5.上下文中的Job(job in the context)
 * 协程中的Job是其上下文中的一部分，可以通过coroutineContext[Job]表达式从上下文中获取到。
 */
fun main45() = runBlocking<Unit> {
    println("My job is ${coroutineContext[Job]}")
    /**
     * >>
     * My job is "coroutine#1":BlockingCoroutine{Active}@6d311334
     */
}

/**
 * 6.子协程(children of a coroutine)
 * 当一个协程在另外一个协程的协程作用域中启动时，它将通过CoroutineScope.coroutineContext继承其上下文，新启动的协程的Job将成为父协程
 * 的Job的子Job。当父协程被取消时，它的所有子协程也会递归的被取消。
 * 但是，当使用GlobalScope启动协程时，协程的Job没有父级。因此，它不受其启动的作用域和地理运作范围的限制。
 */
fun main46() = runBlocking<Unit> {
//sampleStart
    // launch a coroutine to process some kind of incoming request
    val request = launch {
        // it spawns two other jobs, one with GlobalScope
        GlobalScope.launch {
            println("job1: I run in GlobalScope and execute independently!")
            delay(1000)
            println("job1: I am not affected by cancellation of the request")
        }
        // and the other inherits the parent context
        launch {
            delay(100)
            println("job2: I am a child of the request coroutine")
            delay(1000)
            println("job2: I will not execute this line if my parent request is cancelled")
        }
    }
    delay(500)
    request.cancel() // cancel processing of the request
    delay(1000) // delay a second to see what happens
    println("main: Who has survived request cancellation?")
//sampleEnd

    /**
     * >>
     * job1: I run in GlobalScope and execute independently!
     * job2: I am a child of the request coroutine
     * job1: I am not affected by cancellation of the request
     * main: Who has survived request cancellation?
     */
}

/**
 * 7.父协程的职责(Parental responsibilities)
 * 父协程总是会等待其所有子协程完成。父协程不必显式跟踪它启动的所有子协程，也不必使用Job.join在末尾等待子协程完成。
 */
fun main47() = runBlocking<Unit> {
    // launch a coroutine to process some kind of incoming request
    val request = launch {
        repeat(3) { i -> // launch a few children jobs
            launch {
                delay((i + 1) * 200L) // variable delay 200ms, 400ms, 600ms
                println("Coroutine $i is done")
            }
        }
        println("request: I'm done and I don't explicitly join my children that are still active")
    }
    request.join() // wait for completion of the request, including all its children
    println("Now processing of the request is complete")

    /**
     * >>
     * request: I'm done and I don't explicitly join my children that are still active
     * Coroutine 0 is done
     * Coroutine 1 is done
     * Coroutine 2 is done
     * Now processing of the request is complete
     */
}

/**
 * 8.为协程命名以便调试(Naming coroutines for debugging)
 * 当协程经常需要进行日志调式时，协程自动分配到的ID是很有用处的，你只需要关联来自同一个协程的日志记录。但是，当一个协程绑定到一个特定请求的处理
 * 或者执行某个特定的后台任务时，最后显式的为它命名，以便进行调试。CoroutineName上下文元素的作用与线程名相同，它包含在启用调试模式时执行此协程的线程名中
 *
 */
fun main48() = runBlocking(CoroutineName("main")) {
    log("Started main coroutine")
    // run two background value computations
    val v1 = async(CoroutineName("v1_coroutine")) {
        delay(500)
        log("Computing v1")
        252
    }
    val v2 = async(CoroutineName("v2_coroutine")) {
        delay(1000)
        log("Computing v2")
        6
    }
    log("The answer for v1 / v2 = ${v1.await() / v2.await()}")

    /**
     * >>
     * [main @main#1] Started main coroutine
     * [main @v1coroutine#2] Computing v1
     * [main @v2coroutine#3] Computing v2
     * [main @main#1] The answer for v1 / v2 = 42
     */
}

/**
 * 9.组合上下文元素（Combining context elements）
 * 有时需要为协程上下文定义多个元素。可以用+运算符。例如，可以同时使用显式指定的调度器和显式指定的名称来启动协程。
 */
fun main49() = runBlocking<Unit> {
    launch(Dispatchers.Default + CoroutineName("test")) {
        println("I'm working in thread ${Thread.currentThread().name}")
    }
    /**
     * >>
     * I'm working in thread DefaultDispatcher-worker-1 @test#2
     */
}

/**
 * 10.协程作用域(Coroutine scope)
 * 把关于作用域/子元素/Job的知识点放在一起。假设我们的应用程序有一个具有生命周期的对象，但该对象不是协程。例如，我们正在编写一个Android应用程序，
 * 并在Android Activity中启动各种协程，以执行异步操作来获取和更新数据/指定动画等。当Activity销毁时，必须取消所有协程来避免内存泄露。当然，可以
 * 手动操作上下文和Job来绑定Activity和协程的生命周期。但是，kotlinx.coroutines提供了一个抽象封装：CoroutineScope。应该已经对协程作用域很
 * 熟悉了，因为所有的协程构造器都被声明为它的扩展函数。
 *
 * 通过创建与Activity生命周期相关联的协程作用域的实例来管理协程的生命周期。CoroutineScope的实例可以通过CoroutineScope()或者MainScope()的
 * 工厂函数来构建。前者创建通用作用域，后者创建UI应用程序的作用域并使用Dispatchers.Main作为默认的调度器。
 */

class Activity {
    private val mainScope = MainScope()

    fun destroy() {
        mainScope.cancel()
    }
}

/**
 * 或者，可以在这个Activity类中实现CoroutineScope接口。最好的实现方式是对默认工厂函数使用委托。还可以将所需的调度器（在本例中使用Dispatchers
 * .Default）与作用域结合起来：
 *
 * 现在，可以在这个Activity内启动协程，而不必显式地指定他们的上下文。
 */
class Activity1 : CoroutineScope by CoroutineScope(Dispatchers.Default) {
    fun doSomething() {
        repeat(10) { i ->
            launch {
                delay((i + 1) * 200L)
                println("Coroutine $i is done.")
            }
        }
    }

    fun destroy() {
        cancel()
    }
}

fun main410() = runBlocking<Unit> {
//sampleStart
    val activity = Activity1()
    activity.doSomething() // run test function
    println("Launched coroutines")
    delay(500L) // delay for half a second
    println("Destroying activity!")
    activity.destroy() // cancels all coroutines
    delay(1000) // visually confirm that they don't work
//sampleEnd

    /**
     * >>
     * Launched coroutines
     * Coroutine 0 is done
     * Coroutine 1 is done
     * Destroying activity!
     *
     * 只有前两个协程会打印一条消息，其它的则会被 ```Activity.destroy()``` 中的 ```job.cancel()``` 所取消
     */
}

/**
 * 11.线程局部数据(Thread-local data)
 * 有时，将一些线程局部数据传递到协程或在协程之间传递是有实际用途的。但是，由于协程没有绑定到任何特定的协程，如果手动完成，可能会导致模板代码。
 * 对于ThreadLocal，扩展函数asContextElement可用于解决这个问题。它创建一个附加的上下文元素，该元素保持ThreadLocal给定的值，并在每次
 * 协程切换上下文时恢复该值。 很容易在实践中证明：
 */
val threadLocal = ThreadLocal<String?>() // declare thread-local variable

fun main() = runBlocking<Unit> {
//sampleStart
    threadLocal.set("main")
    println("Pre-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
    val job = launch(Dispatchers.Default + threadLocal.asContextElement(value = "launch")) {
        println("Launch start, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
        yield()
        println("After yield, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
    }
    job.join()
    println("Post-main, current thread: ${Thread.currentThread()}, thread local value: '${threadLocal.get()}'")
//sampleEnd

    /**
     * >>
     * Pre-main, current thread: Thread[main @coroutine#1,5,main], thread local value: 'main'
     * Launch start, current thread: Thread[DefaultDispatcher-worker-1 @coroutine#2,5,main], thread local value: 'launch'
     * After yield, current thread: Thread[DefaultDispatcher-worker-1 @coroutine#2,5,main], thread local value: 'launch'
     * Post-main, current thread: Thread[main @coroutine#1,5,main], thread local value: 'main'
     *
     * 很容易忘记设置相应的上下文元素。如果运行协程的线程会有多个，则从协程访问的线程局部变量可能会有意外值。为了避这种情况，建议使用ensurePresent
     * 方法，并在使用不当时快速失败。
     *
     * ThreadLocal具备一等支持，可以与任何基础kotlinx.coroutines一起使用。不过，它有一个关键限制：当线程局部变量发生变化时，新值不会传导到
     * 协程调用方（因为上下文元素无法跟踪所有的线程本地对象引用）。并且更新的值在下次挂起时丢失。使用withContext更新协程中的线程的局部值，参阅
     * asContextElement。
     *
     * 或者，值可以存储在一个可变的类计数器中(var i: int)，而类计数器又存储在一个线程局部变量中，但是，在这种情况下，完全有责任同步此计数器中变量
     * 的潜在并发修改。
     */
}