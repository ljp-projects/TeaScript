package runtime

data class Error(val msg: String, val file: String) {
    fun raise() {
        System.err.println("${if (file != "") "Error in Tea source file $file: " else ""}$msg")
    }
}