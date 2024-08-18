package errors

open class MalpracticeError(message: String, file: String = ""): errors.Error<Nothing> (message, file) {
    override fun toString(): String {
        return message
    }
}