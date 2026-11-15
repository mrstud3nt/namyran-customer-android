package ir.namyranservice.customer

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import ir.namyranservice.customer.data.Clone2HostAccess

@Composable
internal fun Clone2AccessDialog(onDismiss: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("دسترسی محیط آزمایشی Clone2") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("نام کاربری و رمز Directory Privacy کلون۲ را وارد کنید. اطلاعات فقط تا بسته‌شدن برنامه در حافظه می‌مانند.")
            OutlinedTextField(username, { username = it; invalid = false }, singleLine = true, label = { Text("نام کاربری محافظ") })
            OutlinedTextField(password, { password = it; invalid = false }, singleLine = true,
                label = { Text("رمز محافظ محیط") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            if (invalid) Text("مشخصات محافظ را بررسی کنید.", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            try { Clone2HostAccess.configure(username.trim(), password); password = ""; onDismiss() }
            catch (_: IllegalArgumentException) { invalid = true }
        }) { Text("ثبت دسترسی") } },
        dismissButton = { TextButton(onClick = { Clone2HostAccess.clear(); password = ""; onDismiss() }) { Text("پاک‌کردن و بستن") } })
}
