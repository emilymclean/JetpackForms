package cl.emilym.form.validator

import cl.emilym.form.ValidationResult
import cl.emilym.form.Validator

/**
 * Validator implementation for validating numbers within a specified range.
 *
 * Either `maximum` and/or `minimum` must be set.
 *
 * @param T The type of number to validate (must be a Number and Comparable<T>).
 * @param message The error message to use for invalid numbers.
 * @param maximum The maximum allowed number (optional, defaults to null).
 * @param minimum The minimum allowed number (optional, defaults to null).
 * @throws IllegalArgumentException if both maximum and minimum are null.
 */
class NumberPeriodValidator<T>(
    private val message: String,
    private val maximum: T? = null,
    private val minimum: T? = null
): Validator<T> where T: Number, T: Comparable<T> {

    init {
        if (maximum == null && minimum == null)
            throw java.lang.IllegalArgumentException("Must provide either a minimum and/or a maximum number")
    }

    override fun validate(value: T?): ValidationResult {
        if (value == null) return ValidationResult.Valid
        if (minimum != null && value < minimum) return ValidationResult.Invalid(message)
        if (maximum != null && value >= maximum) return ValidationResult.Invalid(message)
        return ValidationResult.Valid
    }
}