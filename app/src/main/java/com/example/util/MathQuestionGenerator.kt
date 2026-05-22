package com.example.util

import kotlin.random.Random

data class MathQuestion(
    val expression: String,
    val answer: Int
)

object MathQuestionGenerator {

    fun generateQuestion(difficulty: String): MathQuestion {
        return when (difficulty.uppercase()) {
            "EASY" -> generateEasyQuestion()
            "MEDIUM" -> generateMediumQuestion()
            "HARD" -> generateHardQuestion()
            else -> generateMediumQuestion()
        }
    }

    private fun generateEasyQuestion(): MathQuestion {
        // Simple a + b or a - b
        val isAddition = Random.nextBoolean()
        val num1 = Random.nextInt(10, 99)
        val num2 = Random.nextInt(10, 99)
        
        return if (isAddition) {
            MathQuestion("$num1 + $num2", num1 + num2)
        } else {
            // Keep answer positive for simplicity
            val large = maxOf(num1, num2)
            val small = minOf(num1, num2)
            MathQuestion("$large - $small", large - small)
        }
    }

    private fun generateMediumQuestion(): MathQuestion {
        // e.g., (a * b) + c or (a * b) - c
        val operator = Random.nextInt(0, 3)
        when (operator) {
            0 -> { // Multiplication & Addition: a * b + c
                val a = Random.nextInt(6, 15)
                val b = Random.nextInt(4, 12)
                val c = Random.nextInt(10, 50)
                return MathQuestion("$a x $b + $c", (a * b) + c)
            }
            1 -> { // Multiplication & Subtraction: a * b - c
                val a = Random.nextInt(6, 15)
                val b = Random.nextInt(4, 12)
                val c = Random.nextInt(10, 50)
                return MathQuestion("$a x $b - $c", (a * b) - c)
            }
            else -> { // Division & Addition: a / b + c
                val b = Random.nextInt(3, 10)
                val answerMult = Random.nextInt(8, 20)
                val a = b * answerMult // Ensure clean integer division
                val c = Random.nextInt(15, 60)
                return MathQuestion("$a ÷ $b + $c", (a / b) + c)
            }
        }
    }

    private fun generateHardQuestion(): MathQuestion {
        // complex equations
        val type = Random.nextInt(0, 4)
        when (type) {
            0 -> { // (a + b) x (c - d)
                val a = Random.nextInt(12, 35)
                val b = Random.nextInt(8, 25)
                val c = Random.nextInt(15, 30)
                val d = Random.nextInt(4, 12) // c - d is positive
                val term1 = a + b
                val term2 = c - d
                return MathQuestion("($a + $b) x ($c - $d)", term1 * term2)
            }
            1 -> { // a * b - c * d
                val a = Random.nextInt(12, 22)
                val b = Random.nextInt(4, 12)
                val c = Random.nextInt(6, 15)
                val d = Random.nextInt(3, 9)
                return MathQuestion("($a x $b) - ($c x $d)", (a * b) - (c * d))
            }
            2 -> { // (a x b) + (c x d)
                val a = Random.nextInt(11, 23)
                val b = Random.nextInt(5, 13)
                val c = Random.nextInt(8, 18)
                val d = Random.nextInt(4, 10)
                return MathQuestion("($a x $b) + ($c x $d)", (a * b) + (c * d))
            }
            else -> { // (a - b) * c - d
                val a = Random.nextInt(150, 400)
                val b = Random.nextInt(40, 140)
                val c = Random.nextInt(2, 5)
                val d = Random.nextInt(20, 80)
                return MathQuestion("($a - $b) x $c - $d", (a - b) * c - d)
            }
        }
    }
}
