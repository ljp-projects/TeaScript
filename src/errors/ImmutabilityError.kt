package errors

open class ImmutabilityError(message: String, file: String = ""): Error<Nothing> ("ImmutabilityError: $message", file) {
    override fun toString(): String {
        return "ImmutabilityError: $message"
    }
}