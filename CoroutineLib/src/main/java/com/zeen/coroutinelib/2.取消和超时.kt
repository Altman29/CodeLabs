package com.zeen.coroutinelib

import kotlinx.coroutines.*
import java.lang.Exception
import java.util.concurrent.CancellationException

/**
 * 1.取消协程执行(Cancelling coroutine execution)
 * 在一个长时间运行的应用程序中，可能需要对协程进行细粒度控制。例如，用户可能关闭了启动协程的页面，
 * 现在不再需要其运行结果，此时就需要主动取消协程。launch函数的返回值job对象就可用于取消正在运行的协程。
 */
fun main1() = runBlocking {
    val job = launch {
        repeat(1000) {
            println("job: I'm sleeping $it")
            delay(500L)
        }
    }
    delay(1300L)
    println("main: I'm tired of waiting!")
    job.cancel()
    job.join()
    println("main: Now I can quit.")

    /**
     * >>
     * job: I'm sleeping 0 ...
     * job: I'm sleeping 1 ...
     * job: I'm sleeping 2 ...
     * main: I'm tired of waiting!
     * main: Now I can quit.
     */

    /**
     * 只要main函数调用了job.cancel,就看不到job协程的任何输出了，因为它已被取消。还有一个job
     * 的扩展函数cancelAndJoin，它集合了cancel和join的调用。
     *
     * cancel() 函数用于取消协程，join() 函数用于阻塞等待协程执行结束。之所以连续调用这两个方法，是因为 cancel() 函数调用后会马上返回而不是等待协程结束后再返回，
     * 所以此时协程不一定是马上就停止了，为了确保协程执行结束后再执行后续代码，此时就需要调用 join() 方法来阻塞等待。可以通过调用 Job 的扩展函数 cancelAndJoin() 来完成相同操作
     */
}

/**
 * 2.取消操作是协作完成的(Cancellation is cooperative)
 * 协程的取消操作是（Cooperative）完成的，协程必须协作才能取消。kotlinx.coroutines中的所有挂起函数都是可取消的，他们在运行时会检查协程是否
 * 被取消了，并在取消时抛出CancellationException。但是，如果协程正在执行计算任务，并且未检查是否已处于取消状态的话，则无法取消协程。
 */
fun main2() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        while (i < 5) {
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("job: I'm sleeping ${i++} ...")
                nextPrintTime += 500
            }
        }
    }
    delay(1000L)
    println("main: I'm tired of waiting!")
    job.cancelAndJoin()
    println("main: Now I can quit.")

    /**
     * 运行代码可以看到即使在 cancel 之后协程 job 也会继续打印 "I'm sleeping" ，直到 Job 在迭代五次后（运行条件不再成立）自行结束
     *
     * >>
     * job: I'm sleeping 0 ...
     * job: I'm sleeping 1 ...
     * job: I'm sleeping 2 ...
     * main: I'm tired of waiting!
     * job: I'm sleeping 3 ...
     * job: I'm sleeping 4 ...
     * main: Now I can quit.
     */
}

/**
 * 3.使计算代码可取消(Making computation code cancellable)
 * 有俩种方法可以使计算类型代码可以被取消。第一种方法是定期调用一个挂起函数来检查取消操作，yieid()函数是一个很好的选择。
 * 另一种方法是显示检查取消操作。以下是后一种的实现。
 * 使用while(isActive)替换前面栗子中的while(i<5)
 */
fun main3() = runBlocking {
    val startTime = System.currentTimeMillis()
    val job = launch(Dispatchers.Default) {
        var nextPrintTime = startTime
        var i = 0
        while (isActive) {
            if (System.currentTimeMillis() >= nextPrintTime) {
                println("job: I'm sleeping ${i++} ...")
                nextPrintTime += 500L
            }
        }
    }
    delay(1300L)
    println("main: I'm tired of waiting!")
    job.cancelAndJoin()
    println("main: Now I can quit.")

    /**
     * 如你所见，现在这个循环被取消了。isActive 是一个可通过 CoroutineScope 对象在协程内部使用的扩展属性
     *
     * >>
     * job: I'm sleeping 0 ...
     * job: I'm sleeping 1 ...
     * job: I'm sleeping 2 ...
     * main: I'm tired of waiting!
     * main: Now I can quit.
     */
}

/**
 * 4.用finally关闭资源(Closing resources with finally)
 * 可取消的挂起函数在取消时会抛出CancellationException，可以用常用的方式来处理这种情况。例如，try{}finally{}表达式和kotlin的use函数
 * 都可用于在取消协程时执行回收操作。
 */
fun main4() = runBlocking {
    val job = launch {
        try {
            repeat(1000) { i ->
                println("job: I'm sleeping $i ...")
                delay(500L)
            }
        } finally {
            println("job: I'm running finally")
        }
    }
    delay(1300L)
    println("main: I'm tired of waiting!")
    job.cancelAndJoin()
    println("main: Now I can quit.")

    /**
     *
     * join() 和 cancelAndJoin() 两个函数都会等待所有回收操作完成后再继续执行之后的代码，因此上面的示例生成以下输出
     *
     * >>
     * job: I'm sleeping 0 ...
     * job: I'm sleeping 1 ...
     * job: I'm sleeping 2 ...
     * main: I'm tired of waiting!
     * job: I'm running finally
     * main: Now I can quit.
     */
}


/**
 * 5.运行不可取消的代码块(Run non-cancellable block)
 * 如果在上个示例中的finally块中使用挂起函数，将会导致抛出CancellationException，因为此时协程已经被取消了（例如：在finally中先调用
 * delay(1000L)函数，将导致之后的语句不执行）。通常这并不是什么问题，因为所有性能良好的关闭操作(关闭文件，取消作业，关闭任何类型的通信通道等)
 * 通常都是非阻塞的，且不涉及任何挂起函数。但是，在极少情况下，当需要在取消的协程中调用挂起函数时，可以使用withContext函数和NonCancellable上下文
 * 将相应的代码包装在withContext(NonCancellable){...}代码块中，如下所示：
 */
fun main5() = runBlocking {
    val job = launch {
        try {
            repeat(1000) {
                println("job: I'm sleeping $it ...")
                delay(500L)
            }
        } finally {
            withContext(NonCancellable) {
                println("job: I'm running finally")
                delay(1000L)
                println("job: And I've just delayed for 1 sec because I'm non-cancellable")
            }
        }
    }
    delay(1300L)
    println("main: I'm tired of waiting!")
//    job.cancel()// cancels will not wait job completion but job also run
    job.cancelAndJoin()// cancels the job and waits for its completion
    println("main: Now I can quit.")

    /**
     * 此时，即使在 finally 代码块中调用了挂起函数，其也将正常生效，且之后的输出语句也会正常输出
     * >>
     * job: I'm sleeping 0 ...
     * job: I'm sleeping 1 ...
     * job: I'm sleeping 2 ...
     * main: I'm tired of waiting!
     * job: I'm running finally
     * job: And I've just delayed for 1 sec because I'm non-cancellable
     * main: Now I can quit.
     */
}

/**
 * 6.超时(Timeout)
 * 大多情况下，我们会主动取消协程的原因是由于其执行时间已超出预估的最长时间。虽然可以手动跟踪对相应的job的引用，并在超时后取消job。
 * 但官方也提供了withTimeout函数来完成此类操作。示例如下：
 */
fun main() = runBlocking {
//    withTimeout(1300L) {
//        repeat(1000) { i ->
//            println("I'm sleeping $i ...")
//            delay(500L)
//        }
//    }

    /**
     * >>
     * I'm sleeping 0 ...
     * I'm sleeping 1 ...
     * I'm sleeping 2 ...
     * Exception in thread "main" kotlinx.coroutines.TimeoutCancellationException: Timed out waiting for 1300 ms
     *
     * withTimeout引发的TimeoutCancellationException是CancellationException的子类。之前从未看过CancellationExcetion这类
     * 异常堆栈信息，是因为对于一个取消的协程来说，CancellationExcetion被认为是触发协程结束的正常原因。但是这里，在主函数使用了
     * withTimeout函数，该函数会主动抛出TimeoutCancellationException。
     *
     * 可以通过使用 ```try{...}catch（e:TimeoutCancellationException）{...}``` 代码块来对任何情况下的超时操作执行某些特定的附加操作，
     * 或者通过使用 ```withTimeoutOrNull``` 函数以便在超时时返回 null 而不是抛出异常
     */
    val result = withTimeoutOrNull(1300L) {
        repeat(1000) { i ->
            println("I'm sleeping $i ...")
            delay(500L)
        }
    }
    println("result is $result")
}

