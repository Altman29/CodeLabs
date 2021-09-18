package com.zeen.coroutinelib

import kotlinx.coroutines.*

//fun main() {
//    coroutineSample1()
//    coroutineSample2()
//    coroutineSample3()
//    coroutineSample4()
//    coroutineSample5()
//    coroutineSample6()
//    coroutineSample8()
//}

/**
 * 1.第一个协程程序(Your first coroutine)
 */
@DelicateCoroutinesApi
fun coroutineSample1() {
    GlobalScope.launch { // 在后台启动一个新协程，并继续执行之后的代码
        // 挂起函数不会阻塞线程，而是会将协程挂起，在特定的时间再继续执行。
        delay(1000L) // 非阻塞式地延迟一秒   delay: 是一个挂起函数(suspend function),挂起函数只能由协程或其他挂起函数调度。
        println("World!") // 延迟结束后打印
    }
    println("Hello,") //主线程继续执行，不受协程 delay 所影响
    Thread.sleep(2000L) // 主线程阻塞式睡眠2秒，以此来保证JVM存活 //注释此行，World 打印不出来。

    /**
     * >>
     * Hello,
     * World!
     */

    /**
     * 本质上，协程可以称为**轻量级线程**。协程在 CoroutineScope （协程作用域）的上下文中通过 launch、async 等协程构造器（coroutine builder）来启动。
     * 在上面的例子中，在 GlobalScope ，即**全局作用域**内启动了一个新的协程，这意味着该协程的生命周期只受整个应用程序的生命周期的限制，
     * 即只要整个应用程序还在运行中，只要协程的任务还未结束，该协程就可以一直运行
     *
     * 开发者需要明白，协程是运行于线程上的，一个线程可以运行多个（可以是几千上万个）协程。线程的调度行为是由 OS 来操纵的，
     * 而协程的调度行为是可以由开发者来指定并由编译器来实现的。当协程 A 调用 delay(1000L) 函数来指定延迟1秒后再运行时，
     * 协程 A 所在的线程只是会转而去执行协程 B，等到1秒后再把协程 A 加入到可调度队列里。所以说，线程并不会因为协程的延时而阻塞，
     * 这样可以极大地提高线程的并发灵活度
     */
}

/**
 * 2.桥接阻塞与非阻塞的世界(Bridging blocking and non-blocking worlds)
 */
@DelicateCoroutinesApi
fun coroutineSample2() {
    /**
     * 在第一个协程程序里，混用了非阻塞代码 ```delay()``` 与阻塞代码 ```Thread.sleep()``` ，使得我们很容易就搞混当前程序是否是阻塞的。
     * 可以改用 runBlocking 来明确这种情形
     */
    //launch a new coroutine in background and continue
    GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    //main thread continues here immediately
    println("Hello,")
    //bug runBlocking,blocks the main thread
    runBlocking {
        //...while delay for 2 seconds to keep JVM alive
        delay(2000L)
    }

    /**
     * >>
     * Hello,
     * World!
     */

    /**
     * 运行结果和第一个程序是一样的，但是这段代码只使用了非阻塞延迟。主线程调用了 runBlocking 函数，
     * 直到 runBlocking 内部的所有协程执行完成后，之后的代码才会继续执行
     *
     * 可以换一种写法，如下Sample3
     */
}

/**
 * 3.
 */
@DelicateCoroutinesApi
fun coroutineSample3() = runBlocking {
    GlobalScope.launch {
        delay(1000L)
        println("World!(${Thread.currentThread().name})")
    }
    println("Hello,(${Thread.currentThread().name})")
    delay(2000L)
    /**
     * 需要注意的是，runBlocking 代码块默认运行于其声明所在的线程，而 launch 代码块默认运行于线程池中，可以通过打印当前线程名来进行区分
     */
}

/**
 * 4.等待作业（Waiting for a job）
 * 延迟一段时间来等待另一个协程运行并不是一个好的选择，可以显式（非阻塞的方式）地等待协程执行完成
 */
fun coroutineSample4() = runBlocking {
    //sample start
    val job = GlobalScope.launch {
        delay(1000L)
        println("World!")
    }
    println("Hello,")
    //wait until child coroutine complete
    job.join()
    //sample end

    /**
     * >>
     * Hello,
     * World!
     */

    /**
     * 现在，代码的运行结果仍然是相同的，但是主协程与后台作业的持续时间没有任何关系，这样好多了
     */
}

/**
 * 5.结构化并发（Structured concurrency）
 * 以上对于协程的使用还有一些需要改进的地方。GlobalScope.launch会创建一个顶级协程。尽管很轻量级，但在运行时还会消耗一些内存资源。
 * 如果开发者忘记保留对改协程的引用，它将可以一直运行在整个应用程序停止。会遇到一些比较麻烦的情形，比如协程中的代码被挂起(比如错误
 * 地延迟了太多时间)，或者启动了太多协程导致内存不足。此时需要手动保留对所有协程的引用以便在需要的时候停止协程，但这很容易出错。
 *
 * Kotlin提供了更好的方案。可以在代码中采用结构化并发。正如通常使用线程那样(线程总是全局的)，可以在特定范围内启动协程。
 *
 * 在上面栗子中，通过runBlocking将main()函数转化为协程。每个协程构造器(包括runBlocking)都会将CoroutineScope的实例添加到代码块的
 * 作用域中。我们可以在这个作用域中启动协程，而不必显式的join，因为外部协程（代码实例中的runBlocking）在其他作用域中启动的所有协程
 * 完成前不会结束。因此，可以简化示例代码。
 */
fun coroutineSample5() = runBlocking {
    launch {
        delay(1000L)
        println("World!")
    }
    println("Hello,")

    /**
     * >>
     * Hello,
     * World!
     */

    /**
     * launch函数是CoroutineScope的扩展函数，而runBlocking的函数体也是被声明为CoroutineScope的扩展函数，所以launch函数就隐式的持有了
     * 和runBlocking相同的协程作用域。此时delay再久，println("World!")也一定会被执行。
     */
}

/**
 * 6.作用域构造器(Scope builder)
 * 除了使用官方的几个协程构建器所提供的协程作用域之外，还可以使用 ```coroutineScope``` 来声明自己的作用域。coroutineScope 用于创建一个协程作用域，
 * 直到所有启动的子协程都完成后才结束。
 *
 * runBlocking 和 coroutineScope 看起来很像，因为它们都需要等待其内部所有相同作用域的子协程结束后才会结束自己。两者的主要区别在于
 * runBlocking 方法会阻塞当前线程，而 coroutineScope 只是挂起并释放底层线程以供其它协程使用。由于这个差别，
 * 所以 runBlocking 是一个普通函数，而 coroutineScope 是一个挂起函数。
 *
 * 通过以下示例来演示：
 */
fun coroutineSample6() = runBlocking {
    launch {
        delay(200L)
        println("Task from runBlocking")
    }

    coroutineScope {
        launch {
            delay(500L)
            println("Task from nested launch")
        }

        delay(100L)
        println("Task from coroutine scope")
    }
    println("Coroutine scope is over")

    /**
     * >>
     * Task from coroutine scope
     * Task from runBlocking
     * Task from nested launch
     * Coroutine scope is over
     */

    /**
     * 注意：在"Task from coroutine scope"消息打印后，在等待launch执行完成之前，将执行并打印"Task from runBlocking"，
     * 尽管此时coroutineScope尚未完成。
     */
}

/**
 * 7.提取函数并重构(Extract function refactoring)
 * 抽取launch内部的代码块为一个独立的函数，需要将其声明为挂起函数。挂起函数可以像常规函数一样在协程中使用，但他们的额外特性是：
 * 可以依次使用其他挂起函数（如delay函数）来使协程挂起。
 */
suspend fun coroutineSample7() {
    delay(1000L)
    println("World!")
}
//
//fun main() = runBlocking {
//    launch { coroutineSample7() }
//    println("Hello,")
//}

/**
 * 8.协程是轻量级的(Coroutines ARE light-weight)
 */
suspend fun coroutineSample8() {
    repeat(100_000) {
        delay(1000L)
        print(".")
    }
    /**
     * 以上代码启动了10万个协程，每个协程延时一秒后都会打印输出。如果改用线程来完成的话，很大可能会发生内存不足异常，但用协程来完成的话就可以轻松胜任.
     */
}

/**
 * 9.全局协程类似与守护线程(Global coroutines are like daemon threads)
 * 一下代码在GlobalScope中启动了一个会长时间运行的协程，它每秒打印俩次"I'm sleeping"，然后延迟一段时间后从main函数返回
 */
fun main() = runBlocking {
    //sampleStart
    GlobalScope.launch {
        repeat(1000) { i ->
            println("I'm sleeping $i")
            delay(500L)
        }
    }
    delay(1300L)//just quit after delay
    //sampleEnd

    /**
     * >>
     * I'm sleeping 0 ...
     * I'm sleeping 1 ...
     * I'm sleeping 2 ...
     *
     * 这是由于 launch 函数依附的协程作用域是 GlobalScope，而非 runBlocking 所隐含的作用域。在 GlobalScope
     * 中启动的协程无法使进程保持活动状态，它们就像守护线程（当主线程消亡时，守护线程也将消亡）
     */
}
