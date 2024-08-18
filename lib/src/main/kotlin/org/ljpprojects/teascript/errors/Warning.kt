package org.ljpprojects.teascript.errors

import org.ljpprojects.teascript.errors.Error

open class Warning(message: String, file: String): Error<Unit>(message, file) {
    override fun raise() {
        println("\u001B[38;2;181;137;0m${if (this.file != "") "Warning in Tea source file ${this.file}: " else ""}${this.message}\u001b[0m")
    }
}