package org.ljpprojects.teascript.utils

class Tuple<A, B, C>(val first: A, val second: B, val third: C) {
    operator fun component1(): A = first
    operator fun component2(): B = second
    operator fun component3(): C = third
}

fun <A, B, C> Pair<Pair<A, B>, C>.toTuple(): Tuple<A, B, C> {
    return Tuple(this.first.first, this.first.second, this.second)
}

fun main() {
    val tuple = (1 to 3 to 4).toTuple()

    val (x, y, z) = tuple

    println(x)
    println(y)
    println(z)
}
