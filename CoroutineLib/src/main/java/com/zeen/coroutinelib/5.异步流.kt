package com.zeen.coroutinelib

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.system.measureTimeMillis

/**
 * 挂起函数可以异步返回单个值，但如何返回多个异步计算值呢？这就是kotlin Flows(流)的用处了。
 */

/**
 * 1.表示多个值(Representing multiple values)
 * 可以使用集合在kotlin中标示多个值。例如，有一个函数foo()，它返回包含三个数字的List，然后使用forEach打印
 */
fun foo51(): List<Int> = listOf(1, 2, 3)
fun main51() {
    foo51().forEach { value -> println(value) }

    /**
     * >>
     * 1
     * 2
     * 3
     */
}

/**
 * 1.1序列(Sequences)
 * 如果我们使用一些CPU消耗性的阻塞代码(每次计算100毫秒)来计算数字，那么可以使用一个序列(Sequence)来表示数字
 */
fun foo511(): Sequence<Int> = sequence {
    for (i in 1..3) {
        Thread.sleep(100)//pretend we are computing it
        yield(i)//yield next value
    }
}

fun main511() {
    foo511().forEach { value -> println(value) }

    /**
     * >>
     * 1
     * 2
     * 3
     * 这段代码输出相同的数字列表，但每打印一个数字前都需要等待100毫秒
     */
}

/**
 * 1.2挂起函数(Suspending functions)
 * 上一节的代码的计算操作会阻塞运行代码的主线程。当这些值由异步代码计算时，可以用suspend修饰符标记函数foo，
 * 以便它可以在不阻塞的情况下执行其工作，并将结果作为列表返回。
 */
suspend fun foo512(): List<Int> {
    delay(1000)//pretend we are doing something asynchronous here
    return listOf(1, 2, 3)
}

fun main512() = runBlocking<Unit> {
    foo512().forEach { value -> println(value) }
    /**
     * >>
     * 这段代码在等待一秒后输出数字
     */
}

/**
 * 1.3 Flows
 * 使用List<int>作为返回值类型，意味着只能同时返回所有值。为了表示异步计算的值流，可以使用Flow<Int>类型，就像同步计算值的Sequence<Int>类型一样
 */
fun foo513(): Flow<Int> = flow {//flow builder
    for (i in 1..3) {
        delay(100)//pretend we are doing something useful here
        emit(i)//emit next value
    }
}

fun main513() = runBlocking<Unit> {
    launch {
        for (k in 1..3) {
            println("I'm not blocked $k")
            delay(100)
        }
    }
    foo513().collect { value -> println(value) }

    /**
     * 此代码在打印每个数字前等待100毫秒，但不会阻塞主线程。通过从主线程中运行的单独协程中每隔100毫秒打印了一次 “I'm not blocked”，
     * 可以验证这一点：
     *
     * >>
     * I'm not blocked 1
     * 1
     * I'm not blocked 2
     * 2
     * I'm not blocked 3
     * 3
     *
     * 请注意，代码与前面示例中的 Flow 有以下不同：

    - Flow 类型的构造器函数名为 flow
    - flow{...} 中的代码块可以挂起
    - foo 函数不再标记 suspend 修饰符
    - 值通过 emit 函数从流中发出
    - 通过 collect 函数从 flow 中取值

    > 我们可以用 Thread.sleep 来代替 flow{...} 中的 delay，可以看到在这种情况下主线程被阻塞住了
     */
}

/**
 * 2. 流是冷的(FLows are cold)
 * Flows是冷流(cold streams)，类似于序列(sequences),flow builder中的代码在开始收集流值之前不会运行。下面示例可以看出这一点：
 */
fun foo52(): Flow<Int> = flow {
    println("FLow started")
    for (i in 1..3) {
        delay(100)
        emit(i)
    }
}

fun main52() = runBlocking<Unit> {
    println("Calling foo...")
    val flow = foo52()
    println("Calling collect...")
    flow.collect { value -> println(value) }
    println("Calling collect again...")
    flow.collect { value -> println(value) }

    /**
     * >>
     * Calling foo...
     * Calling collect...
     * Flow started
     * 1
     * 2
     * 3
     * Calling collect again...
     * Flow started
     * 1
     * 2
     * 3
     *
     * 这是 foo() 函数（返回了 flow）未标记 suspend 修饰符的一个关键原因。
     * foo() 本身返回很快，不会进行任何等待。flow 每次收集时都会启动，这就是我们再次调用 collect 时会看到“flow started”的原因
     */
}

/**
 * 3.取消流 (FLow cancellation)
 * Flow采用和协程取消同样的协作取消。但是Flow实现基础并没有引入额外的取消点，它对于取消操作是完全透明的。通常，流的收集操作可以在当流在
 * 一个可取消的挂起函数(如delay)中挂起的时候取消，否则不能取消。
 *
 * 一下示例展示了在withTimeoutOrNull块中流如何在超时时被取消并停止执行。
 */
fun foo53(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)
        println("Emitting $i")
        emit(i)
    }
}

fun main53() = runBlocking<Unit> {
    withTimeoutOrNull(250) {
        // Timeout after 250ms
        foo53().collect { value -> println(value) }
    }
    println("Done")

    /**
     * 注意，foo() 函数中的 Flow 只传出两个数字，得到以下输出：
     * >>
     *
     * Emitting 1
     * 1
     * Emitting 2
     * 2
     * Done
     *
     * 相对应的，可以注释掉 flow 中的 delay 函数，并增大 for 循环的循环范围，此时可以发现 flow 没有被取消，因为 flow 中没有引入额外的挂起点
     */
}

/**
 * 4.流构建器 (FLow builders)
 * 前面栗子中的flow{...}是最基础的流构建器，还有其他的构建器可以更容易的声明流：
 * a.flowOf()定义了一个发出固定值集的流构建器
 * b.可以使用扩展函数.asFlow()将各种集合和序列转换成流。
 *
 * 因此，从流中打印1到3的数字的栗子可以写成：
 */
fun main54() = runBlocking<Unit> {
    (1..3).asFlow().collect { value -> println(value) }
}

/**
 * 5.中间流运算符(Intermediate flow operators)
 * 可以使用运算符来转换流，就像使用集合和序列一样。中间运算符应用于上游流并返回下游流。
 * 这些操作符是冷操作符，和流流一样。此类运算符本身不是挂起函数，它工作的很快，其返回一个新的转换后的流，但引用近包含对新流的操作定义，并不马上转换。
 *
 * 基础运算符有着熟悉的名称，例如map和 filter。流运算符的序列的重要区别在于流运算符中的代码可以调用挂起函数。
 *
 * 例如，使用map运算符将传入请求流映射为结果值，即使执行请求是由挂起函数实现的长时间运行操作：
 */
suspend fun performRequest(request: Int): String {
    delay(1000)
    return "response $request"
}

fun main55() = runBlocking {
    (1..3).asFlow()// a flow of requests
        .map { performRequest(it) }
        .collect { response -> println(response) }

    /**
     * >>
     * response 1
     * response 2
     * response 3
     */
}

/**
 * 5.1 转换操作符(Transform operator)
 * 在流的转换运算中，最常用的一个成为transform。它可以用来模拟简单的数据转换（就像map和filter），以及实现更复杂的转换。使用transform运算符，
 * 可以发出任意次的任意值。
 *
 * 例如，通过使用transform，可以执行长时间运行的异步请求之前发出一个字符串，并在该字符串后面跟随一个相响应：
 *
 * 依然使用performRequest(request:Int)
 */
fun main551() = runBlocking {
    (1..3).asFlow()
        .transform { request ->
            emit("Making request $request")
            emit(performRequest(request))
        }
        .collect { response -> println(response) }

    /**
     * >>
     * Making request 1
     * response 1
     * Making request 2
     * response 2
     * Making request 3
     * response 3
     */
}

/**
 * 5.2 限长运算符(Size-limiting operators)
 * 限长中间运算符在达到相应限制时取消流的执行。协程中的取消总是通过爆抛出异常来实现，这样所有的资源管理函数例如(try{},finally{}) 就可以
 * 在取消时候政策执行。
 */
fun numbers(): Flow<Int> = flow {
    try {
        emit(1)
        emit(2)
        println("THis line will not execute")
        emit(3)
    } finally {
        println("Finally in numbers")
    }
}

fun main552() = runBlocking {
    numbers()
        .take(2)//take only the first two
        .collect { value -> println(value) }
}

/**
 * 6.流运算符(Terminal flow operators)
 * 终端流运算符是用于启动流的挂起函数。collect是最基本的终端流运算符，但还有其他终端运算符，可以使操作更加简单：
 * 转换各种集合，如toList和toSet函数
 * first运算符用于获取第一个值，single运算符用于确保流发出单个值
 * 使用reduce和fold将流还原为某个值
 * 例如：
 */
fun main56() = runBlocking {
    val sum = (1..5).asFlow()
        .map { it * it }//squares of numbers from 1 to 5
        .reduce { a, b -> a + b }//sum them(terminal operator)
    println(sum)

    /**
     * >>
     * 55
     */
}

/**
 * 7.流是连续的(Flows are sequential)
 * 触发使用对多个流进行操作的特殊运算符，否则每个流的单独集合都是按顺序执行的。集合之间在调用终端运算符的协程中工作，默认情况下，不会启动新
 * 的协程。每个发出的值都由所有中间运算符从上游到下游进行处理，然后在之后传递给终端运算符。
 *
 * 参阅以下示例，该示例过滤偶数并将其映射到字符串：
 *
 */
fun main57() = runBlocking {
    (1..5).asFlow()
        .filter {
            println("Filter $it")
            it % 2 == 0
        }
        .map {
            println("Map $it")
            "string $it"
        }
        .collect {
            println("Collect $it")
        }

    /**
     * >>
     * Filter 1
     * Filter 2
     * Map 2
     * Collect string 2
     * Filter 3
     * Filter 4
     * Map 4
     * Collect string 4
     * Filter 5
     */
}

/**
 * 8. 流上下文(FLow context)
 * 流的收集总是在调用协程的上下文中执行。例如，如果存在foo流，则无论foo流的实现详细信息如何，以下代码都将在该开发者指定的上下文中执行：
 *
 * withContext(context){
 *      foo.collect{value->
 *          println(value)//run in the specified context
 *      }
 * }
 *
 * 流的这个特性成为上下文保留，所以，默认情况下，flow{...}中的代码在相应流的收集器提供的上下文中运行。例如，观察foo的实现，
 * 它打印调用它的线程并发出三个数字：
 *
 */
fun log58(msg: String) = println("[${Thread.currentThread().name}] $msg")
fun foo58(): Flow<Int> = flow {
    log("Started foo flow")
    for (i in 1..3) {
        emit(i)
    }
}

fun main58() = runBlocking {
    foo58().collect { value -> log58("collected $value") }
    /**
     * >>
    [main @coroutine#1] Started foo flow
    [main @coroutine#1] collected 1
    [main @coroutine#1] collected 2
    [main @coroutine#1] collected 3
     *
     * 由于foo().collect是在主线程调用的，所以foo流也是在主线程中调用。对于不关心执行上下文且不阻塞调用方法的快速返回代码或者异步代码，
     * 这是完美的默认设置。
     */
}

/**
 * 8.1 错误的使用withContext(Wrong emission withContext)
 * 可能需要在Dispatchers的上下文中执行长时间运行的占用CPU的代码。可能需要在Dispatchers.Main的上下文中执行默认代码和UI更新。通常，
 * withContext用于在使用kotlin协程时更改代码中的上下文，但flow{...}中的代码必须遵守上下文保留属性，兵器不允许从其他上下文中触发。
 */
fun foo581(): Flow<Int> = flow {
    kotlinx.coroutines.withContext(Dispatchers.Default) {
        for (i in 1..3) {
            Thread.sleep(100)
            emit(i)
        }
    }
}

fun main581() = runBlocking {
    foo581().collect { value -> println(value) }
    /**
     * 代码会生成以下异常
     *
     * >>
     * Exception in thread "main" java.lang.IllegalStateException: Flow invariant is violated:
     * Flow was collected in [CoroutineId(1), "coroutine#1":BlockingCoroutine{Active}@5511c7f8, BlockingEventLoop@2eac3323],
     * but emission happened in [CoroutineId(1), "coroutine#1":DispatchedCoroutine{Active}@2dae0000, DefaultDispatcher].
     * Please refer to 'flow' documentation or use 'flowOn' instead
     * at ...
     */
}

/**
 * 8.2 flowOn 运算符(flowOn operator)
 * 有个例外的情况，flowOn函数能用于改变流发送值时的上下文。改变流上下文的正确方式如下，该示例还打印了相应线程的名称，以显式所有线程的工作方式：
 */
fun foo582(): Flow<Int> = flow {
    for (i in 1..3) {
        Thread.sleep(100) // pretend we are computing it in CPU-consuming way
        log("Emitting $i")
        emit(i) // emit next value
    }
}.flowOn(Dispatchers.Default) // RIGHT way to change context for CPU-consuming code in flow builder

fun main582() = runBlocking<Unit> {
    foo582().collect { value ->
        log("Collected $value")
    }

    /**
     * 注意，flow{...}在后台线程中工作，而在主线程中进行取值
     * >>
     *
     * [DefaultDispatcher-worker-1 @coroutine#2] Emitting 1
     * [main @coroutine#1] Collected 1
     * [DefaultDispatcher-worker-1 @coroutine#2] Emitting 2
     * [main @coroutine#1] Collected 2
     * [DefaultDispatcher-worker-1 @coroutine#2] Emitting 3
     * [main @coroutine#1] Collected 3
     *
     * 这里要注意的另一件事是flowOn操作符改变了流的默认顺寻性质。现在取值操作发生在协程"coroutine#1"中，而发射值的操作同时运行在另一个线程
     * 中的协程"coroutine#2"上。当必须在上游流的上下文中更改CoroutineDispatcher时，flowOn运算符将为该上游流创建另一个协程。
     */
}

/**
 * 9. 缓冲 (Buffering)
 * 从收集流所需的总时间的角度来看，在不同的协程中运行流的不同部分可能会有所帮助，特别是当涉及到长时间运行的异步操作时。例如：假设foo（）流的发射
 * 很慢，生成元素需要100毫秒；收集器也很慢，处理元素需要300毫秒。看看用三个数字来收集这样的流需要多长时间。
 */
fun foo59(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100)//pretend we are asynchronously waiting 100ms
        emit(i)//emit next value
    }
}

fun main59() = runBlocking {
    val time = measureTimeMillis {
        foo59().collect { value ->
            delay(300)
            println(value)
        }
    }
    println("Collected in $time ms")

    /**
     * >>
     * 1
     * 2
     * 3
     * Collected in 1266 ms
     *
     * 整个收集过程大约需要1200毫秒(三个数字，每个400毫秒)
     *
     * 可以在流上使用buffer运算符，在运行取集代码的同时运行foo()发值代码，而不是按顺顺序运行他们
     */
}

fun main590() = runBlocking<Unit> {
    val time = measureTimeMillis {
        foo59()
            .buffer() // buffer emissions, don't wait
            .collect { value ->
                delay(300) // pretend we are processing it for 300 ms
                println(value)
            }
    }
    println("Collected in $time ms")

    /**
     * >>
     * 1
     * 2
     * 3
     * Collected in 1071 ms
     *
     * 可以得到相同的结果，但运行速度更快，因为已经有效的创建了一个处理管道，第一个数字只需要等待100毫秒，然后只需要300毫秒来处理每个数字，
     * 这样运行大约需要1000毫秒即可。
     *
     * 请注意，flowOn运算符在必须更改CoroutineDispatcher时使用相同的缓冲机制，但这里显式的请求缓冲而不更改执行上下文。
     */
}

/**
 * 9.1 合并 (Conflation)
 * 当流用于表示操作或操作状态更新的部分结果时，可能不需要处理每一个值，而是只处理最近的值。在这种情况下，当取值处理中间值太慢时，可以使用
 * 合并运算符跳过中间值。在前面的栗子的基础上再来修改下：
 */
fun foo591(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100) // pretend we are asynchronously waiting 100 ms
        emit(i) // emit next value
    }
}

fun main591() = runBlocking<Unit> {
    val time = measureTimeMillis {
        foo591()
            .conflate() // conflate emissions, don't process each one
            .collect { value ->
                delay(300) // pretend we are processing it for 300 ms
                println(value)
            }
    }
    println("Collected in $time ms")

    /**
     * >>
     * 1
     * 3
     * Collected in 787 ms
     *
     * 可以看到，虽然第一个数字仍在处理中，但第二个数字和第三个数字已经生成，因此第二个数字被合并(丢弃)，只有最近的一个数字(第三个)被交付给取值器
     */
}

/**
 * 9.2 处理最新值 (Processing the latest value)
 * 在发射端和处理端都很慢的情况下，合并是加快处理速度的一种方法。它通过丢弃发射的值实现。另以各种方法取消慢速收集器，并在每次发出新值时重新启动它。
 * 有一系列xxxLatest运算符于xxx运算符执行相同的基本逻辑，但是在新值产生的时候取消执行其中的代码。在前面的示例中，尝试将conflate更改为collectLatest
 *
 */
fun foo592(): Flow<Int> = flow {
    for (i in 1..3) {
        delay(100) // pretend we are asynchronously waiting 100 ms
        emit(i) // emit next value
    }
}

fun main592() = runBlocking<Unit> {
    val time = measureTimeMillis {
        foo592()
            .collectLatest { value -> // cancel & restart on the latest value
                println("Collecting $value")
                delay(300) // pretend we are processing it for 300 ms
                println("Done $value")
            }
    }
    println("Collected in $time ms")

    /**
     * >>
     *
     * Collecting 1
     * Collecting 2
     * Collecting 3
     * Done 3
     * Collected in 741 ms
     *
     * 由于collectLatest主体需要延迟300毫秒，而每100毫秒会发出一个新值，因此我们可以看到collectLatest代码块得到了每一个发射值，
     * 但是最终只完成了最后的一个值。
     */
}

/**
 * 10.组合多个流(Composing multiple flows)
 * 有许多方法可以组合多个流。
 */

/**
 * 10.1 zip
 * 于kotlin标准库中的Sequence.zip扩展函数一样，流有一个zip运算符，用于合并俩个流的相应值。
 * 与kotlin标准库中的Sequence.Zip扩展函数一样，流一个zip运算符，用于组合俩个流的相应值：
 */
fun main5101() = runBlocking {
    val nums = (1..3).asFlow() //numbers 1..3
    val strs = flowOf("one", "two", "three") //strings
    nums.zip(strs) { a, b -> "$a -> $b" }// compose a single string
        .collect { println(it) }  //collect and print

    /**
     * >>
     * 1 -> one
     * 2 -> two
     * 3 -> three
     */
}

/**
 * 10.2 Combine
 * 当flow表示变量或操作的最新值时，可能需要执行依赖于相应流的最新值的计算，并在任何上游发出值时重新计算它。
 * 相应的运算符族称为combine。
 *
 * 例如，如果上例中的数字每300毫秒更新一次，但字符串每400毫秒更新一次，则使用zip运算符压缩它们仍会产生相同的结果，
 * 尽管结果是每400毫秒打印一次。
 *
 * 在本例中，使用中间运算符onEach来延迟每个元素，并使发出样本流的代码更具声明性，更加简单。
 */
fun main5102() = runBlocking {
    val nums = (1..3).asFlow().onEach { delay(300) }
    val strs = flowOf("one", "two", "three").onEach { delay(400) }
    val startTime = System.currentTimeMillis()
    nums.combine(strs) { A, B -> "$A -> $B" }
        .collect { value -> println("$value at ${System.currentTimeMillis() - startTime} ms from start") }

    /**
     * zip:>>
     * 1 -> one at 427 ms from start
     * 2 -> two at 827 ms from start
     * 3 -> three at 1234 ms from start
     *
     * combine:>>
     * 1 -> one at 447 ms from start
     * 2 -> one at 649 ms from start
     * 2 -> two at 852 ms from start
     * 3 -> two at 960 ms from start
     * 3 -> three at 1267 ms from start
     */
}

/**
 * 11. 展平流(Flattening flows)
 * 流表示异步接收到的值序列，因此在每个值触发对另一个值序列的请求的情况下，很容易获取新值。
 * 例如，我们可以使用以下函数，该函数返回相隔500毫秒的俩个字符串流
 *
 * 流表示异步接收的值序列，因此在每个值触发对另一个值序列的请求的情况下很容易获取新值。例如，我们可以使用以下函数，该函数返回相隔500毫秒的两个字符串流：
 *
 * fun requestFlow(i: Int): Flow<String> = flow {
 * emit("$i: First")
 * delay(500) // wait 500 ms
 * emit("$i: Second")
 * }

 * 现在，如果我们有一个包含三个整数的流，并为每个整数调用 requestFlow，如下所示：
 * (1..3).asFlow().map { requestFlow(it) }
 * 然后我们最终得到一个流（flow< flow< String >>），需要将其展平为单独一个流以进行进一步处理。
 * 集合和序列对此提供了 flatten 和 flatMap 运算符。然而，由于流的异步特性，它们需要不同的展开模式，因此流上有一系列 flattening 运算符。
 */

/**
 * 11.1 flatMapConcat
 * flatMapConcat和flattencat运算符实现了Concatenating模式，它们是与序列运算符最直接的类比。它们等待内部流完成，然后开始收集下一个流，如下：
 *
 */
fun requestFlow(i: Int): Flow<String> = flow {
    emit("$i: First")
    delay(500)//wait 500 ms
    emit("$i: Second")
}

fun main5111() = runBlocking {
    val startTime = System.currentTimeMillis() // remember the start time
    (1..3).asFlow().onEach { delay(100) }// a number every 100 ms
        .flatMapConcat { requestFlow(it) }
        .collect { value -> println("$value at ${System.currentTimeMillis() - startTime} ms from start") }

    /**
     * flatMapConcat 的顺序特性在输出结果中清晰可见：
     * >>
     * 1: First at 132 ms from start
     * 1: Second at 633 ms from start
     * 2: First at 744 ms from start
     * 2: Second at 1255 ms from start
     * 3: First at 1365 ms from start
     * 3: Second at 1869 ms from start
     */
}

/**
 * 11.2 flatMapMerge
 * 另一种flattening模式是同时收集所有传入流并将其值合并到单个流中，以便尽快发出值。它由flatMapMerge和flattenMerge运算符实现。
 * 它们
 */

