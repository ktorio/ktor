package io.ktor.utils.io.tests

import kotlinx.cinterop.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import io.ktor.utils.io.internal.utils.*
import io.ktor.utils.io.internal.utils.test.*
import io.ktor.utils.io.streams.*
import platform.posix.*
import kotlin.test.*

class PosixIoTest {
    private val filename = "build/test.tmp"
    private lateinit var buffer: Buffer

    @BeforeTest
    fun setup() {
        buffer = Buffer(DefaultAllocator.alloc(4096))
        buffer.resetForWrite()
        buffer.append("test")

        unlink(filename)
    }

    @AfterTest
    fun cleanup() {
        DefaultAllocator.free(buffer.memory)
        unlink(filename)
    }

    @Test
    fun testFFunctions() {
        val file = fopen(filename, "w") ?: return

        file.use { file ->
            assertEquals(4, fwrite(buffer, file).convert(), "Expected all bytes to be written")
        }
        buffer.resetForWrite()
        fopen(filename, "r")!!.use { file ->
            assertEquals(4, fread(buffer, file).convert(), "Expected all bytes to be read")
            assertEquals("test", buffer.readText())
            assertEquals(0, fread(buffer, file).convert(), "Expected EOF")
        }
    }

    @Test
    fun testFunctions() {
        val file = open(filename, O_WRONLY or O_CREAT, 420)
        if (file < 0) {
            return
        }

        file.use { file ->
            assertEquals(4, write(file, buffer).toInt(), "Expected all bytes to be written")
        }
        buffer.resetForWrite()
        open(filename, O_RDONLY).use { file ->
            assertEquals(4, read(file, buffer).toInt(), "Expected all bytes to be read")
            assertEquals("test", buffer.readText())
            assertEquals(0, read(file, buffer).toInt(), "Expected EOF")
        }
    }

    @Test
    fun testInputOutputForFileDescriptor() {
        val file = open(filename, O_WRONLY or O_CREAT, 420)
        if (file < 0) {
            return
        }

        Output(file.checkError("open(C|W)")).use { out ->
            out.append("test")
        }

        Input(open(filename, O_RDONLY).checkError("open(R)")).use { input ->
            assertEquals("test", input.readText())
        }
    }

    @Test
    fun testInputOutputForFileInstance() {
        val file = fopen(filename, "w") ?: return

        Output(file).use { out ->
            out.append("test")
        }

        Input(fopen(filename, "r")!!).use { input ->
            assertEquals("test", input.readText())
        }
    }

    @Test
    fun testSendRecvFunctions(): Unit = memScoped {
        kx_init_sockets().let { rc ->
            if (rc == 0) {
                fail("WSAStartup failed with $rc")
            }
        }

        val port: UShort = 50352u
        val serverAddr = alloc<sockaddr_in>()
        val clientAddr = alloc<sockaddr_in>()

        with(serverAddr) {
            memset(this.ptr, 0, sockaddr_in.size.convert())
            sin_family = AF_INET.convert()
            sin_port = 0u //my_htons(port)
        }

        with(clientAddr) {
            memset(this.ptr, 0, sockaddr_in.size.convert())
            sin_family = AF_INET.convert()
            sin_port = my_htons(port)
            set_loopback(ptr)
        }

        val acceptor = socket(AF_INET, SOCK_STREAM, 0).checkError("socket()")
        acceptor.makeNonBlocking()
        bind(acceptor, serverAddr.ptr.reinterpret(), sockaddr_in.size.convert()).let { rc ->
            if (rc != 0) {
                val error = socket_get_error()
                throw PosixException.forErrno(errno = error, posixFunctionName = "bind()")
            }
        }
        listen(acceptor, 10).checkError("listen()")

        val addrSizeResult = alloc<UIntVar>()
        addrSizeResult.value = sockaddr_in.size.convert()
        getsockname(
            acceptor, serverAddr.ptr.reinterpret(),
            addrSizeResult.ptr.reinterpret()
        ).checkError("getsockname()")

        clientAddr.sin_port = serverAddr.sin_port

        val connected: KX_SOCKET = socket(AF_INET, SOCK_STREAM, 0).checkError("socket()")
        val zero: KX_SOCKET = 0.convert()
        var accepted: KX_SOCKET = zero
        var connectedFlag = false

        connected.makeNonBlocking()
        val one = alloc<IntVar>()
        one.value = 1
        set_no_delay(connected)

        var attempts = 100000L

        while (!connectedFlag || accepted == zero) {
            if (attempts-- <= 0) {
                fail("Too many attempts to accept/connect")
            }

            if (accepted == zero) {
                val result = accept(acceptor, null, null)
                if (result < zero || result == KX_SOCKET.MAX_VALUE) {
                    val error = socket_get_error()
                    if (error != EAGAIN && error != EWOULDBLOCK) {
                        throw PosixException.forErrno(error, "accept()")
                    }
                } else {
                    accepted = result
                    accepted.makeNonBlocking()
                }
            }

            if (!connectedFlag) {
                val result = connect(connected, clientAddr.ptr.reinterpret(), sockaddr_in.size.convert())
                if (result != 0) {
                    val error = socket_get_error()
                    if (error == EINPROGRESS || error == EISCONN) {
                    } else if (error != EAGAIN && error != EWOULDBLOCK) {
                        throw PosixException.forErrno(error, "connect()")
                    } else {
                        continue
                    }
                }
                connectedFlag = true
            }
        }

        // send
        while (true) {
            if (attempts-- <= 0) {
                fail("Too many attempts to send")
            }

            val result = send(connected, buffer, 0)
            if (result < 0) {
                val error = socket_get_error()
                if (error == EAGAIN) continue
                throw PosixException.forErrno(error, "send()")
            }
            assertEquals(4, result.convert())
            break
        }

        buffer.resetForWrite()

        // receive

        while (true) {
            if (attempts-- <= 0) {
                fail("Too many attempts to receive")
            }

            val result = recv(accepted, buffer, 0)
            if (result < 0) {
                val error = socket_get_error()
                if (error == EAGAIN) continue
                throw PosixException.forErrno(error, "recv()")
            }
            assertEquals(4, result.convert())
            break
        }

        close_socket(accepted)
        close_socket(connected)
        close_socket(acceptor)
    }

    @Test
    fun testInputDoubleCloseFD() {
        val fd = open(filename, O_WRONLY or O_CREAT, 420)

        if (fd < 0) {
            return
        }

        val input = Input(fd)
        close(fd)
        input.close()
    }

    @Test
    fun testInputDoubleCloseFD2() {
        val fd = open(filename, O_WRONLY or O_CREAT, 420)
        if (fd < 0) {
            return
        }

        val input = Input(fd)
        input.close()
        input.close()
    }

    @Test
    fun testInputDoubleCloseFILE() {
        val fopen = fopen(filename, "w") ?: return
        val input = Input(fopen)
        input.close()
        input.close()
    }

    @Test
    fun testOutputDoubleCloseFD() {
        val fd = open(filename, O_WRONLY or O_CREAT, 420)
        if (fd < 0) {
            return
        }

        val output = Output(fd)
        close(fd)
        output.close()
    }

    @Test
    fun testOutputDoubleCloseFD2() {
        val fd = open(filename, O_WRONLY or O_CREAT, 420)
        if (fd < 0) {
            return
        }

        val output = Output(fd)
        output.close()
        output.close()
    }

    @Test
    fun testOutputDoubleCloseFILE() {
        val file = fopen(filename, "w") ?: return

        val output = Output(file)
        output.close()
        output.close()
    }

    private inline fun Int.use(block: (Int) -> Unit) {
        checkError()
        try {
            block(this)
        } finally {
            close(this)
        }
    }

    private fun KX_SOCKET.makeNonBlocking() {
        make_socket_non_blocking(this)
    }

    private fun my_htons(value: UShort): uint16_t = when {
        ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN -> value
        else -> value.toShort().reverseByteOrder().toUShort()
    }

    @Suppress("unused")
    internal fun Int.checkError(action: String = ""): Int = when {
        this < 0 -> memScoped { throw PosixException.forErrno(posixFunctionName = action) }
        else -> this
    }

    @Suppress("unused")
    internal fun Long.checkError(action: String = ""): Long = when {
        this < 0 -> memScoped { throw PosixException.forErrno(posixFunctionName = action) }
        else -> this
    }

    private val ZERO: size_t = 0u

    @Suppress("unused")
    internal fun size_t.checkError(action: String = ""): size_t = when (this) {
        ZERO -> errno.let { errno ->
            when (errno) {
                0 -> this
                else -> memScoped { throw PosixException.forErrno(posixFunctionName = action) }
            }
        }
        else -> this
    }

}
