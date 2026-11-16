package ir.namyranservice.customer

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import ir.namyranservice.customer.data.Clone2HostAccess
import ir.namyranservice.customer.data.Clone2HostStore

@Composable
internal fun Clone2AccessDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { Clone2HostStore(context) }
    var rememberAccess by remember { mutableStateOf(store.isSaved()) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("دسترسی محیط آزمایشی Clone2") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("نام کاربری و رمز Directory Privacy کلون۲ را وارد کنید. با انتخاب ذخیره، دسترسی پس از بستن و بازکردن اپ هم باقی می‌ماند.")
            OutlinedTextField(username, { username = it; invalid = false }, singleLine = true, label = { Text("نام کاربری محافظ") })
            OutlinedTextField(password, { password = it; invalid = false }, singleLine = true,
                label = { Text("رمز محافظ محیط") }, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Row {
                Checkbox(rememberAccess, { rememberAccess = it })
                Text("ذخیره دسترسی Clone2 در این دستگاه")
            }
            if (store.isSaved()) Text("دسترسی رمزگذاری‌شده در این دستگاه ذخیره شده است.")
            if (invalid) Text("ثبت یا پاک‌کردن دسترسی انجام نشد؛ دوباره بررسی کنید.", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            try {
                if (username.isBlank() && password.isEmpty() && store.isSaved()) {
                    if (!rememberAccess) store.removeSaved()
                } else {
                    Clone2HostAccess.validate(username.trim(), password)
                    if (rememberAccess) store.save(username.trim(), password)
                    else { store.removeSaved(); Clone2HostAccess.configure(username.trim(), password) }
                }
                password = ""; onDismiss()
            } catch (_: Exception) { invalid = true }
        }) { Text("ثبت دسترسی") } },
        dismissButton = { TextButton(onClick = { try { store.forget(); password = ""; onDismiss() } catch (_: Exception) { invalid = true } }) { Text("پاک‌کردن و بستن") } })
}
