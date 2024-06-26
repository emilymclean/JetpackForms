package cl.emilym.form.compose

import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class FormConfig(
    val errorMessage: @Composable RowScope.(String) -> Unit = {
        Text(it, style = MaterialTheme.typography.bodyMedium)
    }
)

val LocalFormConfig = staticCompositionLocalOf { FormConfig() }
