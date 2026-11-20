package com.example.mindtrack_app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mindtrack_app.AccountMode
import com.example.mindtrack_app.MindTrackViewModel

/** Create an account or sign in to turn on cloud backup. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(vm: MindTrackViewModel) {
    val cs = MaterialTheme.colorScheme
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val signUp = vm.accountMode == AccountMode.SIGN_UP

    val emailOk = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    val passwordOk = password.length >= 6
    val confirmOk = !signUp || confirm == password
    val canSubmit = emailOk && passwordOk && confirmOk && !vm.cloudBusy

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        TopBar(if (vm.accountFromWelcome) "Restore from backup" else "Cloud backup", onBack = { vm.back() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(MtIcons.Lock, tint = cs.primary, background = cs.primaryContainer, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    "Your check-in results are saved to your own private space. Your individual answers never leave this phone.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            }

            if (!vm.accountFromWelcome) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = signUp, onClick = { vm.accountMode = AccountMode.SIGN_UP; vm.cloudMessage = null },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Create account") }
                    SegmentedButton(
                        selected = !signUp, onClick = { vm.accountMode = AccountMode.SIGN_IN; vm.cloudMessage = null },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Sign in") }
                }
            }

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                isError = email.isNotBlank() && !emailOk,
                supportingText = { if (email.isNotBlank() && !emailOk) Text("Enter a valid email address") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showPassword = !showPassword }) { Text(if (showPassword) "Hide" else "Show") } },
                supportingText = { if (signUp) Text("At least 6 characters") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (signUp) ImeAction.Next else ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) vm.submitAccount(email, password) }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (signUp) {
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it },
                    label = { Text("Confirm password") }, singleLine = true, shape = MaterialTheme.shapes.medium,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = confirm.isNotEmpty() && confirm != password,
                    supportingText = { if (confirm.isNotEmpty() && confirm != password) Text("Passwords don't match") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSubmit) vm.submitAccount(email, password) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            vm.cloudMessage?.let {
                AppCard(container = cs.surfaceContainer) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (!signUp) {
                TextButton(onClick = { vm.sendPasswordReset(email) }) { Text("Forgot password?") }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(24.dp)) {
            PrimaryButton(
                text = if (signUp) "Create account and back up" else "Sign in",
                onClick = { vm.submitAccount(email, password) },
                enabled = canSubmit,
                loading = vm.cloudBusy,
            )
        }
    }
}
