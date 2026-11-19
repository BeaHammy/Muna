package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.ChecklistRepository
import com.example.data.UserProfile
import com.example.service.ChecklistSyncService
import com.example.ui.AuthScreen
import com.example.ui.MainChecklistScreen
import com.example.ui.PermissionsHelper
import com.example.ui.PermissionsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.WarmBackground
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { ChecklistRepository(context) }
    val scope = rememberCoroutineScope()

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showPermissions by remember { mutableStateOf(!com.example.data.AppSettings.arePermissionsCompleted(context)) }
    var isLoadingProfile by remember { mutableStateOf(currentUser != null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser == null) {
                userProfile = null
                isLoadingProfile = false
                ChecklistSyncService.stop(context)
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(currentUser?.uid) {
        val uid = currentUser?.uid
        if (uid.isNullOrBlank()) {
            userProfile = null
            isLoadingProfile = false
        } else {
            isLoadingProfile = true
            userProfile = repository.getUserProfile(uid)
            isLoadingProfile = false
        }
    }

    LaunchedEffect(currentUser?.uid, userProfile?.listCode) {
        if (currentUser != null && userProfile != null && userProfile!!.listCode.isNotBlank()) {
            ChecklistSyncService.start(context)
        }
    }

    if (isLoadingProfile) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WarmBackground),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
    } else if (currentUser == null || userProfile == null || userProfile!!.listCode.isBlank()) {
        AuthScreen(
            onAuthComplete = { profile ->
                userProfile = profile
                showPermissions = !com.example.data.AppSettings.arePermissionsCompleted(context)
            }
        )
    } else {
        key(currentUser!!.uid) {
            if (showPermissions) {
                PermissionsScreen(
                    onPermissionsGranted = {
                        com.example.data.AppSettings.setPermissionsCompleted(context, true)
                        showPermissions = false
                    }
                )
            } else {
                MainChecklistScreen(
                    userProfile = userProfile!!,
                    onActiveListChanged = { newCode ->
                        userProfile = userProfile?.copy(listCode = newCode)
                    },
                    onOpenPermissions = { showPermissions = true },
                    onLogout = {
                        scope.launch {
                            withContext(NonCancellable) {
                                ChecklistSyncService.stop(context)
                                Firebase.auth.signOut()
                                userProfile = null
                            }
                        }
                    }
                )
            }
        }
    }
}
