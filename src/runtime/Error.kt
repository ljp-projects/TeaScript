package runtime

import kotlin.system.exitProcess

data class Error(val msg: String, val file: String) {
    fun raise(): Nothing {
        System.err.println("${if (file != "") "Error in Tea source file $file: " else ""}$msg")

        exitProcess(1)
    }
}