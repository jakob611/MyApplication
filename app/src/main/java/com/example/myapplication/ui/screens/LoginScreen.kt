package com.example.myapplication.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R

// Enum za različne načine prikaza
enum class AuthMode {
    SIGN_UP,
    SIGN_IN,
    FORGOT_PASSWORD
}

@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit = { _, _ -> },
    onSignup: (String, String, String) -> Unit = { _, _, _ -> },
    onBackToHome: () -> Unit = {},
    errorMessage: String? = null,
    onGoogleSignInClick: () -> Unit = {},
    onForgotPassword: (String) -> Unit = {}
) {
    var authMode by remember { mutableStateOf(AuthMode.SIGN_UP) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var hasTriedLogin by remember { mutableStateOf(false) }

    // Reset hasTriedLogin when switching modes
    LaunchedEffect(authMode) {
        hasTriedLogin = false
    }

    // Figma colors
    val primaryBlue = Color(0xFF4285F4)
    val lightGray = Color(0xFFF5F5F5)
    val textGray = Color(0xFF666666)
    val borderGray = Color(0xFFE0E0E0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Removed top navigation bar completely
            Spacer(modifier = Modifier.height(60.dp))

            // Main content based on mode
            when (authMode) {
                AuthMode.SIGN_UP -> SignUpContent(
                    email = email,
                    password = password,
                    confirmPassword = confirmPassword,
                    showPassword = showPassword,
                    showConfirmPassword = showConfirmPassword,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onConfirmPasswordChange = { confirmPassword = it },
                    onShowPasswordToggle = { showPassword = !showPassword },
                    onShowConfirmPasswordToggle = { showConfirmPassword = !showConfirmPassword },
                    onContinueClick = { onSignup(email.trim(), password, confirmPassword) },
                    onGoogleSignInClick = onGoogleSignInClick,
                    onSwitchToSignIn = { authMode = AuthMode.SIGN_IN },
                    errorMessage = errorMessage,
                    primaryBlue = primaryBlue,
                    lightGray = lightGray,
                    textGray = textGray,
                    borderGray = borderGray
                )

                AuthMode.SIGN_IN -> SignInContent(
                    email = email,
                    password = password,
                    showPassword = showPassword,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onShowPasswordToggle = { showPassword = !showPassword },
                    onLoginClick = {
                        hasTriedLogin = true
                        onLogin(email.trim(), password)
                    },
                    onGoogleSignInClick = onGoogleSignInClick,
                    onForgotPasswordClick = { authMode = AuthMode.FORGOT_PASSWORD },
                    onSwitchToSignUp = { authMode = AuthMode.SIGN_UP }, // Added this line
                    errorMessage = errorMessage,
                    hasTriedLogin = hasTriedLogin,
                    primaryBlue = primaryBlue,
                    lightGray = lightGray,
                    textGray = textGray,
                    borderGray = borderGray
                )

                AuthMode.FORGOT_PASSWORD -> ForgotPasswordContent(
                    email = email,
                    onEmailChange = { email = it },
                    onResetPasswordClick = { onForgotPassword(email.trim()) },
                    onBackToSignIn = { authMode = AuthMode.SIGN_IN },
                    errorMessage = errorMessage,
                    primaryBlue = primaryBlue,
                    lightGray = lightGray,
                    textGray = textGray,
                    borderGray = borderGray
                )
            }
        }
    }
}

@Composable
private fun SignUpContent(
    email: String,
    password: String,
    confirmPassword: String,
    showPassword: Boolean,
    showConfirmPassword: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onShowPasswordToggle: () -> Unit,
    onShowConfirmPasswordToggle: () -> Unit,
    onContinueClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onSwitchToSignIn: () -> Unit,
    errorMessage: String?,
    primaryBlue: Color,
    lightGray: Color,
    textGray: Color,
    borderGray: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Title
        Text(
            text = "Create an account",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter your email to sign up for this app",
            fontSize = 14.sp,
            color = textGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Error message
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFD32F2F),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Email field
        CustomTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = "email@domain.com",
            keyboardType = KeyboardType.Email,
            lightGray = lightGray,
            borderGray = borderGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        CustomPasswordField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "Password...",
            showPassword = showPassword,
            onShowPasswordToggle = onShowPasswordToggle,
            lightGray = lightGray,
            borderGray = borderGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Confirm password field
        CustomPasswordField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = "Confirm password...",
            showPassword = showConfirmPassword,
            onShowPasswordToggle = onShowConfirmPasswordToggle,
            lightGray = lightGray,
            borderGray = borderGray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Continue button
        Button(
            onClick = onContinueClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Continue",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "or",
            color = textGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Google sign in button
        OutlinedButton(
            onClick = onGoogleSignInClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
            border = BorderStroke(width = 1.dp, color = borderGray),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.google_icon),
                contentDescription = "Google",
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Continue with Google",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Terms and privacy
        Text(
            text = "By clicking continue, you agree to our Terms of Service\nand Privacy Policy",
            fontSize = 12.sp,
            color = textGray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Already have account
        Text(
            text = "Already have an account?",
            fontSize = 16.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onSwitchToSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Log in",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SignInContent(
    email: String,
    password: String,
    showPassword: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onShowPasswordToggle: () -> Unit,
    onLoginClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onSwitchToSignUp: () -> Unit, // Added this parameter
    errorMessage: String?,
    hasTriedLogin: Boolean, // Added this parameter
    primaryBlue: Color,
    lightGray: Color,
    textGray: Color,
    borderGray: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Title
        Text(
            text = "Welcome back",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter your credentials to sign in",
            fontSize = 14.sp,
            color = textGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Error message
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFD32F2F),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Email field
        CustomTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = "email@domain.com",
            keyboardType = KeyboardType.Email,
            lightGray = lightGray,
            borderGray = borderGray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password field
        CustomPasswordField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "Password...",
            showPassword = showPassword,
            onShowPasswordToggle = onShowPasswordToggle,
            lightGray = lightGray,
            borderGray = borderGray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Login button
        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Continue",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "or",
            color = textGray,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Google sign in button
        OutlinedButton(
            onClick = onGoogleSignInClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
            border = BorderStroke(width = 1.dp, color = borderGray),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.google_icon),
                contentDescription = "Google",
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Continue with Google",
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Terms and privacy
        Text(
            text = "By clicking continue, you agree to our Terms of Service\nand Privacy Policy",
            fontSize = 12.sp,
            color = textGray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Show forgot password only if there was a login attempt with error
        if (hasTriedLogin && errorMessage != null && password.isNotEmpty()) {
            Text(
                text = "Forgot Password?",
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onForgotPasswordClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Reset password",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Don't have an account? (Added this section)
        Text(
            text = "Don't have an account?",
            fontSize = 16.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSwitchToSignUp,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryBlue),
            border = BorderStroke(width = 1.dp, color = primaryBlue),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Create account",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ForgotPasswordContent(
    email: String,
    onEmailChange: (String) -> Unit,
    onResetPasswordClick: () -> Unit,
    onBackToSignIn: () -> Unit,
    errorMessage: String?,
    primaryBlue: Color,
    lightGray: Color,
    textGray: Color,
    borderGray: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Title
        Text(
            text = "Reset Password",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter your email to reset your password",
            fontSize = 14.sp,
            color = textGray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Error message
        if (errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = Color(0xFFD32F2F),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Email field
        CustomTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = "email@domain.com",
            keyboardType = KeyboardType.Email,
            lightGray = lightGray,
            borderGray = borderGray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Reset password button
        Button(
            onClick = onResetPasswordClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = "Reset Password",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Back to sign in
        TextButton(onClick = onBackToSignIn) {
            Text(
                text = "← Back to Sign In",
                color = primaryBlue,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    lightGray: Color,
    borderGray: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = Color(0xFF999999),
                fontSize = 16.sp
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = lightGray,
            unfocusedContainerColor = lightGray,
            focusedBorderColor = borderGray,
            unfocusedBorderColor = borderGray,
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black
        ),
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true
    )
}

@Composable
private fun CustomPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    showPassword: Boolean,
    onShowPasswordToggle: () -> Unit,
    lightGray: Color,
    borderGray: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = Color(0xFF999999),
                fontSize = 16.sp
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = lightGray,
            unfocusedContainerColor = lightGray,
            focusedBorderColor = borderGray,
            unfocusedBorderColor = borderGray,
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black
        ),
        shape = RoundedCornerShape(8.dp),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onShowPasswordToggle) {
                Icon(
                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                    tint = Color(0xFF999999)
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true
    )
}