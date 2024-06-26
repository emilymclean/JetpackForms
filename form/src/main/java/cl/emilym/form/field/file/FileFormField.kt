package cl.emilym.form.field.file

import android.content.ContentResolver
import android.net.Uri
import cl.emilym.form.FormField
import cl.emilym.form.ValidationResult
import cl.emilym.form.Validator
import cl.emilym.form.field.base.BaseFormField
import cl.emilym.form.validator.file.FileCountValidator
import cl.emilym.form.validator.file.FileMimeTypeValidator
import cl.emilym.form.validator.file.FileSizeValidator
import cl.emilym.form.validator.file.FileStateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.UnsupportedOperationException

/**
 * Represents a form field for files.
 *
 * @param T The type of file information this form field operates on.
 */
interface FileFormField<T: FileInfo>: FormField<List<Uri>> {

    /**
     * The current state of the form field.
     */
    val currentState: List<FileState<T>>
    /**
     * A flow representing the current state of the form field.
     */
    val liveState: Flow<List<FileState<T>>>

    /**
     * Adds a file to the form field.
     *
     * @param file The file to add.
     */
    fun addFile(file: T)

    /**
     * Adds a file to the form field.
     *
     * @param uri The URI of the file to add.
     * @param contentResolver The content resolver to use for file operations.
     */
    fun addFile(uri: Uri, contentResolver: ContentResolver)

    /**
     * Removes a file from the form field.
     *
     * @param file The file to remove.
     */
    fun removeFile(file: T)

    val fileCountRequired: IntRange?
    val fileSize: LongRange?
    val acceptableMimeTypes: List<String>?

}

/**
 * Represents a form field for retryable file operations.
 *
 * @param T The type of file information this form field operates on.
 */
interface RetryableFileFormField<T: FileInfo> {

    /**
     * Retries a file operation.
     *
     * @param file The file to retry.
     */
    fun retryFile(file: FileInfo)

}

/**
 * Represents a base form field for files.
 *
 * @param T The type of file information this form field operates on.
 */
abstract class BaseFileFormField<T: FileInfo>: BaseFormField<List<Uri>>(), FileFormField<T> {

    /**
     * The list of validators for individual files.
     */
    abstract val fileValidators: List<Validator<T>>
    /**
     * The list of validators for the entire file selection.
     */
    abstract val filesValidators: List<Validator<List<T>>>
    /**
     * The list of validators for the state of files.
     */
    open val stateValidators: List<Validator<List<FileState<T>>>> = listOf(FileStateValidator())

    final override val validators: List<Validator<List<Uri>>>
        get() = listOf()

    protected var _currentState: List<FileState<T>> = listOf()
        set(value) {
            field = value
            _liveState.tryEmit(field)
            doValidation(errorMessage.value == null)
        }
    override val currentState: List<FileState<T>> get() = _currentState

    private val _liveState = MutableStateFlow<List<FileState<T>>>(listOf())
    final override val liveState: Flow<List<FileState<T>>> = _liveState

    override var currentValue: List<Uri>?
        get() = currentState.mapNotNull { toUri(it.file) }
        set(value) {
            throw UnsupportedOperationException("Cannot directly set file form field")
        }
    final override val liveValue: Flow<List<Uri>?> = liveState.map { it.mapNotNull { toUri(it.file) } }

    override fun doValidation(silent: Boolean): Boolean {
        val fileValidationResults = currentState.map { fileState ->
            fileValidators.map { it.validate(fileState.file) }
        }.flatten().filterIsInstance<ValidationResult.Invalid>()
        val filesValidationResults = filesValidators.map { it.validate(currentState.map { it.file }) }
            .filterIsInstance<ValidationResult.Invalid>()
        val stateValidationResults = stateValidators.map { it.validate(currentState) }
            .filterIsInstance<ValidationResult.Invalid>()

        return presentValidation(fileValidationResults + filesValidationResults + stateValidationResults, silent)
    }

    /**
     * Validates a file and returns an error message if invalid.
     *
     * @param file The file to validate.
     * @return The error message if invalid, null otherwise.
     */
    protected fun fileValid(file: T): String? {
        return fileValidators.map { it.validate(file) }
            .filterIsInstance<ValidationResult.Invalid>()
            .firstOrNull()?.message
    }

    override val fileCountRequired: IntRange? by lazy {
        val validator = filesValidators.filterIsInstance<FileCountValidator<*>>().firstOrNull()
        validator ?: return@lazy null
        return@lazy IntRange(validator.minimum ?: 0, (validator.maximum ?: Int.MAX_VALUE))
    }
    override val fileSize: LongRange? by lazy {
        val validator = fileValidators.filterIsInstance<FileSizeValidator<*>>().firstOrNull()
        validator ?: return@lazy null
        return@lazy LongRange(0, validator.maximumSize)
    }
    override val acceptableMimeTypes: List<String>? by lazy {
        val validator = fileValidators.filterIsInstance<FileMimeTypeValidator<*>>().firstOrNull()
        validator ?: return@lazy null
        return@lazy validator.acceptableMimeTypes
    }

    protected abstract fun toUri(file: T): Uri?

}

/**
 * Represents a concurrent-safe base form field for files.
 *
 * @param T The type of file information this form field operates on.
 */
abstract class ConcurrentBaseFileFormField<T: FileInfo>(): BaseFileFormField<T>() {

    /**
     * Indicates whether file operations should be performed on a single thread.
     */
    protected open val singleThread: Boolean = false

    /**
     * The coroutine scope for concurrent operations.
     */
    protected open val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private val stateLock = Mutex()

    /**
     * Updates the state of the form field.
     *
     * @param afterUpdate Callback function to be executed after the update.
     * @param operation The operation to perform on the state.
     */
    protected fun updateState(afterUpdate: () -> Unit = {}, operation: (List<FileState<T>>) -> List<FileState<T>>) {
        val task = {
            _currentState = operation(_currentState)
            afterUpdate()
        }
        if (singleThread) {
            task()
        } else {
            scope.launch {
                stateLock.withLock {
                    task()
                }
            }
        }
    }

}