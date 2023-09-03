@file:OptIn(ExperimentalForeignApi::class)

import com.monkopedia.jni.*
import com.monkopedia.jni.jmethodID
import com.monkopedia.jni.jobject
import com.monkopedia.jni.jvalue
import com.monkopedia.jnitest.com.monkopedia.ksrpc.jni.JNIDispatcher
import com.monkopedia.jnitest.com.monkopedia.ksrpc.jni.JavaJniContinuationConverter
import com.monkopedia.jnitest.initThread
import com.monkopedia.jnitest.threadEnv
import com.monkopedia.jnitest.threadJni
import com.monkopedia.ksrpc.jni.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.posix.usleep

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_NativeHost_serializeDeserialize")
fun serializeDeserialize(env: CPointer<JNIEnvVar>, clazz: jobject, input: jobject): jobject? {
    initThread(env)
    try {
        val jniSerialized = JniSerialized.fromJvm(threadEnv, input)
        val obj = JniSer.decodeFromJni<ComplexClass>(jniSerialized)
        val output = obj.copy(intValue = (obj.intValue ?: 0) + 1)
        return JniSer.encodeToJni(output).toJvm(threadEnv)
    } catch (t: Throwable) {
        t.printStackTrace()
        usleep(1000000u)
        return null
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_NativeHost_createContinuations")
fun createContinuations(env: CPointer<JNIEnvVar>, clazz: jobject, input: jobject, list: jobject) {
    initThread(env)
    try {
        memScoped {
            val ref = threadJni.NewGlobalRef!!(threadEnv, input)
            val receiver: suspend (String) -> Unit = createReceiver(ref!!)
            val jni = threadJni
            val listClass = jni.FindClass!!.invoke(env, "java/util/List".cstr.ptr)
            val addMethod =
                jni.GetMethodID!!.invoke(
                    env,
                    listClass,
                    "add".cstr.ptr,
                    "(Ljava/lang/Object;)Z".cstr.ptr
                )
            val context = newSingleThreadContext("context")
            val scope = CoroutineScope(context)
            val continuation = createContinuation(scope) {
                receiver("Result: $it")
            }
            val completion = CompletableDeferred<Int>()
            val chained1 = createContinuation(scope) {
                val firstValue = completion.await()
                receiver("$it + $firstValue = ${it + firstValue}")
                println("Delete ref")
            }
            val chained2 = createContinuation(scope) {
                completion.complete(it)
            }
            add(jni, env, list, addMethod, continuation)
            add(jni, env, list, addMethod, chained1)
            add(jni, env, list, addMethod, chained2)
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun MemScope.add(
    jni: JNINativeInterface_,
    env: CPointer<JNIEnvVar>,
    list: jobject,
    addMethod: jmethodID?,
    continuation: Continuation<Int>
) {
    val converter = NativeJniContinuationConverter<Int>(env)
    jni.CallBooleanMethodA!!.invoke(
        env,
        list,
        addMethod,
        cValue<jvalue> {
            this.l =
                converter.convertFrom(
                    NativeJniContinuation(continuation, newTypeConverter<jobject?>().int)
                )
        }.ptr
    )
}

private fun createContinuation(
    scope: CoroutineScope,
    resultHandler: suspend (Int) -> Unit
): Continuation<Int> {
    val result = CompletableDeferred<Continuation<Int>>()
    scope.launch(JNIDispatcher) {
        val v = suspendCoroutine {
            result.complete(it)
        }
        resultHandler(v)
    }
    while (!result.isCompleted) {
        usleep(100u)
    }
    return result.getCompleted()
}

@OptIn(ExperimentalForeignApi::class)
fun createReceiver(input: jobject): suspend (String) -> Unit {
    val method = memScoped {
        val cls = threadJni.FindClass!!.invoke(threadEnv, "com/monkopedia/ksrpc/Receiver".cstr.ptr)
        val messagePtr = "message".cstr.ptr
        val sig = "(Ljava/lang/String;)V".cstr.ptr
        threadJni.GetMethodID!!.invoke(threadEnv, cls, messagePtr, sig)
    }
    return { v: String ->
        memScoped {
            val str = threadJni.NewString!!.invoke(threadEnv, v.wcstr.ptr, v.length)
            threadJni.CallVoidMethodA!!.invoke(
                threadEnv,
                input,
                method,
                cValue<jvalue> {
                    this.l = str
                }.ptr
            )
            threadJni.DeleteLocalRef!!.invoke(threadEnv, str)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@CName("Java_com_monkopedia_ksrpc_NativeHost_createContinuationRelay")
fun createContinuationRelay(env: CPointer<JNIEnvVar>, clazz: jobject, output: jobject): jobject? {
    initThread(env)
    val javaContinuation = JavaJniContinuationConverter<Int>(env).convertTo(output)
    val typeConverter = newTypeConverter<jobject>()
    val continuation = createContinuation(GlobalScope) {
        javaContinuation.resumeWith(typeConverter.int, Result.success(it + 1))
    }
    return NativeJniContinuationConverter<Int>(env).convertFrom(
        continuation.withConverter(typeConverter.int)
    )
}
