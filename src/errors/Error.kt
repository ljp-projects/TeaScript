package errors

import kotlin.system.exitProcess

open class Error<E>(open val message: String, open val file: String) {
    open fun raise(): E {
        println("\u001B[38;2;220;50;47m${if (this.file != "") "Error in Tea source file ${this.file}: " else ""}${this.message}\u001B[0m")

        exitProcess(1)
    }
}