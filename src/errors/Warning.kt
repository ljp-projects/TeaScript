package errors

open class Warning(message: String, file: String): Error<Unit>(message, file) {
    override fun raise() {
        System.err.println("${if (this.file != "") "Warning in Tea source file ${this.file}: " else ""}${this.message}")
    }
}