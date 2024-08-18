package org.ljpprojects.teascript.errors

import org.ljpprojects.teascript.runtime.types.RuntimeVal
import org.ljpprojects.teascript.runtime.types.Type

data class IncorrectTypeError(val expected: Type, val got: RuntimeVal): Error<Nothing>("IncorrectTypeError: expected $expected, but got $got.", "") {
    override val message: String
        get() =
            "IncorrectTypeError: expected $expected, but got $got."

    override fun toString(): String = message
}