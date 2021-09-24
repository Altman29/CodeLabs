package com.zeen.coroutinelib

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.channels.produce


/**
 * Deferred值提供了在协程之间传递单个值的方便方法，而通道Channels提供了一种传输值流的方法。
 */

/**
 * 1.通道基础(Channel basics)
 * 通道在概念上非常类似于BlockingQueue，它们之间的一个关键区别是：通道有一个挂起的send函数和
 * 一个挂起的receive函数，而不是一个阻塞的put操作和一个阻塞的take操作。
 */
fun main601() = runBlocking {
    val channel = Channel<Int>()
    launch {
        // this might be heavy CPU-consuming computation or async logic, we'll just send five squares
        for (x in 1..5) channel.send(x * x)
    }
    // here we print five received integers:
    repeat(5) { println(channel.receive()) }
    println("Done!")

    /**
     * >>
     * 1
     * 4
     * 9
     * 16
     * 25
     * Done!
     */
}

/**
 * 2.关闭和迭代通道(Closing and iteration over channels)
 * 与队列不同，通道可以关闭，以此来表明元素已发送完成。在接收方，使用常规的for循环从通道接收元素是比较方便的。
 *
 * 从概念上讲，close类似于向通道发送一个特殊的close标记。一旦接收到这个close标记，迭代就会停止，因此可以保证接收到close之前发送的所有元素：
 */
fun main602() = runBlocking {
    val channel = Channel<Int>()
    launch {
        for (x in 1..5) channel.send(x * x)
        channel.close() // we're done sending
    }
    // here we print received values using `for` loop (until the channel is closed)
    for (y in channel) println(y)
    println("Done!")
    /**
     * >>
     * 1
     * 4
     * 9
     * 16
     * 25
     * Done!
     */
}

/**
 * 3. 构建通道生产者(Building channel producers)
 * 协程生成元素序列(sequence)的模式非常常见。这是可以经常在并发编程中发现的生产者-消费者模式的一部分。你可以将这样一个生产力抽象为
 * 一个以channel为参数的函数，但这与必须从函数返回结果的常识相反。
 *
 * 有一个方便的名为product的协程构造器，它使得producer端执行该操作变得很容易；还有一个扩展函数consumeEach，它替换了consumer端的for循环：
 */
fun CoroutineScope.produceSquares(): ReceiveChannel<Int> = produce {
    for (x in 1..5) send(x * x)
}

fun main603() = runBlocking {
    val squares = produceSquares()
    squares.consumeEach { println(it) }
    println("Done!")

    /**
     * >>
     * 1
     * 4
     * 9
     * 16
     * 25
     * Done!
     *
     * CoroutineScope.produceSquares()
     * ReceiveChannel
     * produce
     * consumeEach
     */
}

/**
 * 4. 管道 (Pipelines)
 * 管道是一种模式，是一个协程正在生成的可能是无穷多个元素的值流。
 */

fun CoroutineScope.produceNumbers() = produce<Int> {
    var x = 1
    while (true) send(x++)// infinite stream of integers starting from 1
}

/**
 * 存在一个或多个协程对值流进行取值，进行一些处理并产生一些其他结果。在下面，每个返回值也是入参值（数字）的平方值
 */

fun CoroutineScope.square(numbers: ReceiveChannel<Int>): ReceiveChannel<Int> = produce {
    for (x in numbers) send(x * x)
}

/**
 * 启动并链接整个管道：
 */
fun main604() = runBlocking {
    val numbers = produceNumbers() // produces integers form 1 and on
    val squares = square(numbers) // squares integers
    repeat(5) {
        println(squares.receive()) //print first five
    }
    println("Done!") //we are done
    coroutineContext.cancelChildren() //cancel children coroutines

    /**
     * >>
     * 1
     * 4
     * 9
     * 16
     * 25
     * Done!
     *
     * 创建协程的所有函数都被定义为CoroutineScope的扩展，因此可以依赖结构化并发来确保应用程序中没有延迟的全局协程。
     */
}

/**
 * 5. 使用管道的素数(Prime numbers with pipeline)
 * 以一个使用协程管道生成素数的栗子，将管道发挥到极致。从一个无线的数字序列开始
 */
fun CoroutineScope.numbersFrom(start: Int) = produce<Int> {
    var x = start
    while (true)
        send(x++)
}

/**
 * 以下管道过滤传入的数字流，删除所有可被定素数整除的数字：
 */
fun CoroutineScope.filter(numbers: ReceiveChannel<Int>, prime: Int) = produce<Int> {
    for (x in numbers) if (x % prime != 0) send(x)
}

/**
 * 现在，通过从2开始一个数字流，从当前通道获取一个质数，并为找到的每个质数启动新的管道：
 * numbersFrom(2) -> filter(2) -> filter(3) -> filter(5) -> filter(7) ...
 *
 * 下面的示例代码打印了前十个质数，在主线程的上下文中运行整个管道。因为所有的协程都是在主 runBlocking 协程的范围内启动的，
 * 所以我们不必保留所有已启动的协程的显式引用。我们使用扩展函数 cancelChildren 来取消打印前十个质数后的所有子协程。
 */
fun main605() = runBlocking {
    var cur = numbersFrom(2)
    repeat(5) {
        val prime = cur.receive()
        println(prime)
        cur = filter(cur, prime)
    }
    coroutineContext.cancelChildren() // cancel all children to let main finish

    /**
     *
     * >>
     * 2
     * 3
     * 5
     * 7
     * 11
     *
     * 注意，可以使用标准库中的 iterator 协程构造器来构建相同的管道。将 product 替换为 iterator，send 替换为 yield，receive
     * 替换为 next，ReceiveChannel 替换为 iterator，并去掉协程作用域。你也不需要再使用 runBlocking 。但是，使用如上所示的通道的管道的好处是，
     * 如果在 Dispatchers.Default 上下文中运行它，它实际上可以利用多个 CPU 来执行代码
     *
     * 但无论如何，如上所述的替代方案也是一个非常不切实际的来寻找素数的方法。实际上，管道确实涉及一些其他挂起调用（如对远程服务的异步调用），
     * 并且这些管道不能使用 sequence/iterator 来构建，因为它们不允许任意挂起，而 product 是完全异步的。
     */
}

/**
 * 6. 扇出(Fan - out)
 * 多个协程可以从同一个通道接收数据，在它们之间分配任务。让我们从一个周期性地生成整数(每秒10个数)地producer协程开始：
 */
fun CoroutineScope.produceNumbers606() = produce<Int> {
    var x = 1 //start from 1
    while (true) {
        send(x++)// produce next
        delay(100) //wait 0.1s
    }
}

/**
 * 然后可以有多个处理器(processor)协程。在本例中，它们只需要打印它们的id和接收的数字：
 */
fun CoroutineScope.launchProcessor606(id: Int, channel: ReceiveChannel<Int>) = launch {
    for (msg in channel){
        println("Processor #$id received $msg")
    }
}
/**
 * 现在启动5个处理器，让它们工作几乎一秒钟，看看会发生什么：
 */
fun main606() = runBlocking<Unit> {
    val producer = produceNumbers606()
    repeat(5) { launchProcessor606(it, producer) }
    delay(950)
    producer.cancel() // cancel producer coroutine and thus kill them all
    /**
     * >>
     * Processor #0 received 1
     * Processor #0 received 2
     * Processor #1 received 3
     * Processor #2 received 4
     * Processor #3 received 5
     * Processor #4 received 6
     * Processor #0 received 7
     * Processor #1 received 8
     * Processor #2 received 9
     *
     * 注意，取消 producer 协程会关闭其通道，从而最终终止 processor  协程正在执行的通道上的迭代。
     *
     * 另外，请注意我们如何使用 for 循环在通道上显式迭代以在 launchProcessor 代码中执行 fan-out。
     * 与 consumeEach 不同，这个 for 循环模式在多个协程中使用是完全安全的。如果其中一个 processor  协程失败，
     * 则其他处理器仍将处理通道，而通过 consumeEach  写入的处理器总是在正常或异常完成时消费（取消）底层通道
     */
}

/**
 * 7.扇入(Fan - in)
 * 多个协程可以发送到同一个通道。例如，有一个字符串通道和一个挂起函数，函数以指定的延迟将指定的字符串重复发送到此通道：
 *
 */