package errors

import kotlin.system.exitProcess

open class Error<E>(open val message: String, open val file: String) {
    open fun raise(): E {
        System.err.println("${if (this.file != "") "Error in Tea source file ${this.file}: " else ""}${this.message}")

        exitProcess(1)
    }
}