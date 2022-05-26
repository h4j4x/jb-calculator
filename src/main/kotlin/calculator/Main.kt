package calculator

import java.math.BigInteger

const val HELP_CMD = "/help"
const val EXIT_CMD = "/exit"

val variables = mutableMapOf<String, BigInteger>()
val variableRegex = "[+-]?[a-zA-Z]+".toRegex()
val intValueRegex = "[+-]?\\d+".toRegex()
val assignRegex = ".*\\s*=\\s*.*".toRegex()

fun main() {
    var cmd: String
    do {
        cmd = readln()
        processCommand(cmd)
    } while (cmd != EXIT_CMD)
    println("Bye!")
}

fun processCommand(cmd: String) {
    try {
        val toPrint: String = when (cmd) {
            HELP_CMD -> "The program calculates operations with integer numbers and variables (+ - * / ^)."
            EXIT_CMD -> ""
            else -> {
                if (cmd.startsWith("/")) {
                    throw CalculatorException.unknownCommand()
                } else {
                    calculate(cmd.trim())?.toString() ?: ""
                }
            }
        }
        if (toPrint.isNotBlank()) {
            println(toPrint)
        }
    } catch (e: Exception) {
        println(e.message)
    }
}

fun calculate(expression: String): BigInteger? {
    if (expression.isBlank()) {
        return null
    }
    if (assignRegex.matches(expression)) {
        val parts = expression.split("=")
        assign(parts[0].trim(), parts[1].trim())
        return null
    }
    val tokens = Lexer.parse(expression)
    val postfix = Parser.toPostfix(tokens)
    return calculate(postfix)
}

fun calculate(postfix: List<Token>): BigInteger? {
    val stack = mutableListOf<String>()
    for (token in postfix) {
        if (token.isValueOrVariable()) {
            stack.add(token.value)
        } else if (token.isOperator()) {
            when (token.type) {
                TokenType.PLUS_OP -> {
                    val a = evaluateValueOrVariable(stack.removeLast())
                    val b = if (stack.isNotEmpty()) evaluateValueOrVariable(stack.removeLast()) else BigInteger.ZERO
                    stack.add(a.plus(b).toString())
                }
                TokenType.MINUS_OP -> {
                    val a = evaluateValueOrVariable(stack.removeLast())
                    val b = if (stack.isNotEmpty()) evaluateValueOrVariable(stack.removeLast()) else BigInteger.ZERO
                    stack.add(b.minus(a).toString())
                }
                TokenType.MULTIPLY_OP -> {
                    val a = evaluateValueOrVariable(stack.removeLast())
                    val b = evaluateValueOrVariable(stack.removeLast())
                    stack.add(a.multiply(b).toString())
                }
                TokenType.DIVIDE_OP -> {
                    val a = evaluateValueOrVariable(stack.removeLast())
                    val b = evaluateValueOrVariable(stack.removeLast())
                    stack.add(b.divide(a).toString())
                }
                TokenType.POWER_OP -> {
                    val a = evaluateValueOrVariable(stack.removeLast())
                    val b = evaluateValueOrVariable(stack.removeLast())
                    stack.add(b.pow(a.toInt()).toString())
                }
                else -> {}
            }
        }
    }
    if (stack.isEmpty()) {
        return null
    }
    return evaluateValueOrVariable(stack.removeLast())
}

fun assign(variable: String, valueOrVariable: String) {
    if (!variableRegex.matches(variable)) {
        throw CalculatorException.invalidIdentifier()
    }
    val value = evaluateValueOrVariable(valueOrVariable)
    variables[variable] = value
}

fun evaluateValueOrVariable(string: String): BigInteger {
    if (variableRegex.matches(string)) {
        if (variables[string] != null) {
            return variables[string]!!
        }
        throw CalculatorException.unknownVariable()
    }
    if (intValueRegex.matches(string)) {
        val value = string.toBigIntegerOrNull()
        if (value != null) {
            return value
        }
    }
    throw CalculatorException.invalidExpression()
}

enum class TokenType(val priority: Int = -1) {
    NONE,
    INT,
    PLUS_OP(1),
    MINUS_OP(1),
    MULTIPLY_OP(2),
    DIVIDE_OP(2),
    POWER_OP(3),
    VARIABLE,
    GROUP_START,
    GROUP_END;

    fun isOperator() = this == PLUS_OP || this == MINUS_OP ||
            this == MULTIPLY_OP || this == DIVIDE_OP ||
            this == POWER_OP

    fun isGroup() = this == GROUP_START || this == GROUP_END

    fun isValueOrVariable() = this == INT || this == VARIABLE

    fun cannotMerge(other: TokenType): Boolean {
        if (other.isGroup() || this.isGroup()) {
            return true
        }
        if (priority == PLUS_OP.priority && other.priority == PLUS_OP.priority) {
            return false
        }
        return this != other
    }

    fun merge(other: TokenType): TokenType {
        val equals = other == this
        val otherIsSub = other == MINUS_OP
        if (equals && otherIsSub) {
            return PLUS_OP
        }
        if (otherIsSub || this == MINUS_OP) {
            return MINUS_OP
        }
        if (this == PLUS_OP || this.isValueOrVariable()) {
            return this
        }
        throw CalculatorException.invalidExpression()
    }
}

class Token(var value: String, var type: TokenType) {
    fun isNotBlank() = value.isNotBlank()

    fun isValueOrVariable() = type.isValueOrVariable()

    fun isType(type: TokenType) = this.type == type


    fun isNotType(type: TokenType) = this.type != type

    fun isGroup() = type.isGroup()


    fun isOperator() = type.isOperator()

    override fun toString(): String {
        return "('$value':$type)"
    }
}

object Lexer {
    fun parse(expression: String): List<Token> {
        if (expression.isBlank()) {
            return emptyList()
        }
        val list = mutableListOf<Token>()
        var current = Token("", TokenType.NONE)
        for (char in expression.toCharArray()) {
            val charType = when (char) {
                in '0'..'9' -> TokenType.INT
                in 'a'..'z' -> TokenType.VARIABLE
                in 'A'..'Z' -> TokenType.VARIABLE
                '+' -> TokenType.PLUS_OP
                '-' -> TokenType.MINUS_OP
                '*' -> TokenType.MULTIPLY_OP
                '/' -> TokenType.DIVIDE_OP
                '^' -> TokenType.POWER_OP
                '(' -> TokenType.GROUP_START
                ')' -> TokenType.GROUP_END
                else -> TokenType.NONE
            }
            if (charType == TokenType.NONE) {
                continue
            }
            if (charType.cannotMerge(current.type)) {
                if (current.isNotBlank()) {
                    list.add(current)
                }
                current = Token("$char", charType)
            } else {
                current.value += "$char"
                current.type = current.type.merge(charType)
            }
        }
        if (current.isNotBlank()) {
            list.add(current)
        }
        return list
    }
}

object Parser {
    fun toPostfix(tokens: List<Token>): List<Token> {
        val postfix = mutableListOf<Token>()
        val stack = mutableListOf<Token>()
        for (token in tokens) {
            if (token.isValueOrVariable()) {
                postfix.add(token)
            } else if (token.isType(TokenType.GROUP_START)) {
                stack.add(token)
            } else if (token.isType(TokenType.GROUP_END)) {
                while (stack.isNotEmpty() && stack.last().isNotType(TokenType.GROUP_START)) {
                    postfix.add(stack.removeLast())
                }
                if (stack.isEmpty()) {
                    throw CalculatorException.invalidExpression()
                }
                stack.removeLast()
            } else if (token.isOperator()) {
                while (stack.isNotEmpty() && stack.last().type.priority >= token.type.priority) {
                    postfix.add(stack.removeLast())
                }
                stack.add(token)
            }
        }
        while (stack.isNotEmpty()) {
            val token = stack.removeLast()
            if (token.isGroup()) {
                throw CalculatorException.invalidExpression()
            }
            postfix.add(token)
        }
        return postfix
    }
}

class CalculatorException(message: String?) : RuntimeException(message) {
    companion object {
        fun unknownVariable() = CalculatorException("Unknown variable")

        fun unknownCommand() = CalculatorException("Unknown command")

        fun invalidIdentifier() = CalculatorException("Invalid identifier")

        fun invalidExpression() = CalculatorException("Invalid expression")
    }
}
