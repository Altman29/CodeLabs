package com.zeen.coroutinelib

import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

/**
 * 1.默认顺序(Sequential by default)
 * 假设定义了俩个挂起函数，他们用于执行一些有用的操作，比如某种远程服务调用或者是计算操作。假设这俩个函数是有实际意义的。
 */
suspend fun doSomethingUsefulOne(): Int {
    delay(1000L)
    return 13
}

suspend fun doSomethingUsefulTwo(): Int {
    delay(1000L)
    return 29
}

/**
 * 在实践中，如果我们需要依靠第一个函数的运行结果来决定是否需要调用或者如何调用第二个函数，此时就需要按照顺序来运行这俩个函数。
 * 使用默认顺序来调用这俩个函数，因为协程中代码和常规代码一样，在默认情况下是顺序的执行的。下面来计算来个挂起函数运行的所需总时间。
 */
fun main31() = runBlocking {
    val time = measureTimeMillis {
        val one = doSomethingUsefulOne()
        val two = doSomethingUsefulTwo()
        println("The answer is ${one + two}")
    }
    println("Completed in $time ms")

    /**
     * >>
     * The answer is 42
     * Completed in 2019 ms
     */
}

/**
 * 2.使用async并发(Concurrent using async)
 * 如果one和two俩个函数之间没有依赖关系，并且希望通过同时执行这俩个操作(并发)以便更快的得到答案，此时就需要用到async了。
 *
 * 从概念上讲，async就类似与launch。async启动一个单独的协程，这是一个与所有其他协程同时工作的轻量级协程。不同之处在于，launch返回job对象并且
 * 不懈怠任何运行结果值。而async返回一个轻量级非阻塞的Deferred对象，可用于在之后去除返回值，可以通过调用Deferred的await()方法来获取最终结果。
 * 此外，Deferred也实现了job接口，所以可以根据需要来取消它。
 */
fun main32() = runBlocking {
    val time = measureTimeMillis {
        val one = async { doSomethingUsefulOne() }
        val two = async { doSomethingUsefulTwo() }
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")

    /**
     * 可以看到运行耗时几乎是减半了，因为这两个协程是同时运行，总的耗时时间可以说是取决于耗时最长的任务。需要注意，协程的并发总是显式的
     *
     * The answer is 42
     * Completed in 1019 ms
     */
}

/**
 * 3.惰性启动async(Lazily started async)
 * 可选的，可以将async的start参数设置为CoroutineStart.lazy使其变为懒加载模式。在这种模式下，只有在主动调用Deferred的await()或者start()方法
 * 时才会启动协程，如下所示：
 */
fun main33() = runBlocking {
    val time = measureTimeMillis {
        val one = async(start = CoroutineStart.LAZY) { doSomethingUsefulOne() }
        val two = async(start = CoroutineStart.LAZY) { doSomethingUsefulTwo() }
        one.start()
        two.start()
        println("The answer is ${one.await() + two.await()}")
    }
    println("Completed in $time ms")

    /**
     * >>
     * The answer is 42
     * Completed in 1016 ms
     *
     * 以上定义了俩个协程，但没有像起码的栗子那样直接执行，而是将控制权交给了开发者通过调用start()函数来确切的开始执行。首先启动了one，然后启动了two，
     * 然后再等待协程运行结束。
     *
     * 注意，如果只是在prinln中调用了await()而不首先调用start()，这将新城顺序行为，因为await()会启动协程并等待其完成，这不是lazy模式的预期效果。
     * async(start = CoroutineStart.LAZY)的用例是标准库中lazy函数的替代品，用于在值的计算涉及挂起函数的情况下。
     */
}

/**
 * 4.异步风格的函数（Async-style functions）
 * 可以定义异步风格的函数，使用带有显式GlobalScope引用的异步协程生成器来调用one和two函数。用Async后缀来命名这些函数，以此来强调它们
 * 用来启动异步计算，并且需要通过返回的延迟值来获取结果。
 */
@DelicateCoroutinesApi
fun somethingUsefulOneAsync() = GlobalScope.async {
    doSomethingUsefulOne()
}

fun somethingUsefulTwoAsync() = GlobalScope.async {
    doSomethingUsefulTwo()
}

/**
 * 注意，这些xxxAsync函数不是挂起函数，他们可以从任何地方调用。但是调用这些函数意味着要用异步形式来执行操作。
 * 以下示例展示了他们在协程之外的使用：
 */
fun main34() {
    val time = measureTimeMillis {
        val one = somethingUsefulOneAsync()
        val two = somethingUsefulTwoAsync()
        runBlocking {
            println("The answer is ${one.await() + two.await()}")
        }
    }
    println("Completed in $time ms")

    /**
     * 这里展示的带有异步函数的编程样式仅供说明，因为它是其他编程语言中的流行样式。强力建议不要此样式与kitlin协程一起使用，原因如下：
     *
     * 如果在val one = somethingUsefulOneAsync()和one.await()这俩行代码之间存在逻辑错误，导致程序抛出异常，正在执行的操作也被中止，
     * 此时会发生什么情况？通常，全局的错误处理者可以捕获此异常，未开发人员记录并报告错误。但是程序可以继续执行其他操作。但是这里函数仍然还在
     * 后台运行，因为其协程作用域是GlobalScope，即使其启动这已经被中止了。这个问题不会在结构化并发中出现，如下节所示
     */
}

/**
 * 5.使用async的结构化并发(Structured concurrency with async)
 * 以Concurrent using async章节为栗，提取一个同时执行dowSomethingUsefulOne()和doSomethingUsefulTwo()并返回其结果之和的函数。
 * 因为async函数被定义为CoroutineScope上的一个扩展函数，所以需要将它放在CoroutineScope中，这就是coroutineScope函数提供的功能：
 */
suspend fun concurrentSum(): Int = coroutineScope {
    val one = async { doSomethingUsefulOne() }
    val two = async { doSomethingUsefulTwo() }
    one.await() + two.await()
}

/**
 * 这样，如果concurrentSum()函数发送错误并引发异常，则在其作用域中启动的所有协程都将被取消
 */
fun main351() = runBlocking<Unit> {
//sampleStart
    val time = measureTimeMillis {
        println("The answer is ${concurrentSum()}")
    }
    println("Completed in $time ms")
//sampleEnd
    /**
     * >>
     * The answer is 42
     * Completed in 1017 ms
     */
}

/**
 * 取消操作始终通过协程的层次结构来进行传播
 */
fun main() = runBlocking<Unit> {
    try {
        failedConcurrentSum()
    } catch(e: ArithmeticException) {
        println("Computation failed with ArithmeticException")
    }
}

suspend fun failedConcurrentSum(): Int = coroutineScope {
    val one = async<Int> {
        try {
            delay(Long.MAX_VALUE) // Emulates very long computation
            42
        } finally {
            println("First child was cancelled")
        }
    }
    val two = async<Int> {
        println("Second child throws an exception")
        throw ArithmeticException()
    }
    one.await() + two.await()

    /**
     * 需要注意协程 one 和正在等待的父级是如何在协程 two 失败时取消的
     * >>
     * Second child throws an exception
     * First child was cancelled
     *Computation failed with ArithmeticException
     */
}