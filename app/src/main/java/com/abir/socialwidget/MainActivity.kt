package com.abir.socialwidget

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- Palette (matches the widget) ----
private val Bg = Color(0xFF0D0E15)
private val Panel1 = Color(0xFF161926)
private val Panel2 = Color(0xFF1F2333)
private val Line = Color(0xFF2C3147)
private val Accent = Color(0xFF8C9EFF)
private val YtRed = Color(0xFFFF5A4D)
private val FbBlue = Color(0xFF5AA2FF)
private val TextHi = Color(0xFFF2F3F8)
private val TextLo = Color(0xFFA9AEC2)
private val Good = Color(0xFF59D68C)
private val Warn = Color(0xFFFFB454)

private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF10132A),
    background = Bg,
    surface = Bg,
    onSurface = TextHi,
    onBackground = TextHi,
    surfaceVariant = Panel2,
    onSurfaceVariant = TextLo,
    outline = Line,
    error = Color(0xFFFF8A80),
)

class MainActivity : FragmentActivity() {
    // The settings screen is only shown after the user passes the device lock (fingerprint, face or PIN).
    private var unlocked by mutableStateOf(false)
    private var lastStopAt = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unlocked = savedInstanceState?.getBoolean(STATE_UNLOCKED) ?: false
        lastStopAt = savedInstanceState?.getLong(STATE_STOPPED_AT) ?: 0L
        // The app is dark in every system theme, so keep the system bar icons light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        Scheduler.schedule(applicationContext)
        setContent {
            MaterialTheme(colorScheme = AppColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
                    if (unlocked) SettingsScreen() else LockScreen(onUnlock = { authenticate() })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val away = SystemClock.elapsedRealtime() - lastStopAt
        if (lastStopAt == 0L || away > RELOCK_AFTER_MS) unlocked = false
        if (!unlocked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        lastStopAt = SystemClock.elapsedRealtime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_UNLOCKED, unlocked)
        outState.putLong(STATE_STOPPED_AT, lastStopAt)
    }

    private fun authenticate() {
        if (prompting) return
        val manager = BiometricManager.from(this)
        val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val credential = BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val allowed = listOf(strong or credential, credential, strong)
            .firstOrNull { manager.canAuthenticate(it) == BiometricManager.BIOMETRIC_SUCCESS }
        if (allowed == null) {
            // The phone has no screen lock at all, so there is nothing to ask for.
            unlocked = true
            return
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Social Stats")
            .setSubtitle("Your connections are protected")
            .setAllowedAuthenticators(allowed)
            .apply { if (allowed and credential == 0) setNegativeButtonText("Cancel") }
            .build()
        prompting = true
        BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false // stay locked; the lock screen has an Unlock button
                }
            },
        ).authenticate(info)
    }

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
        const val STATE_UNLOCKED = "unlocked"
        const val STATE_STOPPED_AT = "stopped_at"
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(R.drawable.ic_launcher), contentDescription = null, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(20.dp))
        Text("Social Stats is locked", color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use your fingerprint, face or screen lock to see your connections.",
            color = TextLo,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onUnlock,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF10132A)),
            modifier = Modifier.height(52.dp),
        ) { Text("Unlock", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { Store.loadSettings(context) }

    // Saved secrets are never loaded into the UI: the fields only ever hold a NEW value the user is typing.
    var ytKey by remember { mutableStateOf("") }
    var ytSaved by remember { mutableStateOf(initial.ytKey.isNotBlank()) }
    var ytReplacing by remember { mutableStateOf(false) }
    var ytChannel by remember { mutableStateOf(initial.ytChannel) }
    var fbPageId by remember { mutableStateOf(initial.fbPageId) }
    var fbToken by remember { mutableStateOf("") }
    var fbSaved by remember { mutableStateOf(initial.fbToken.isNotBlank()) }
    var fbReplacing by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(Store.loadStats(context)) }
    var busy by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    var helpOpen by remember { mutableStateOf(false) }
    var widgetGuideOpen by remember { mutableStateOf(false) }
    var fbGuideOpen by remember { mutableStateOf(false) }
    var ytGuideOpen by remember { mutableStateOf(false) }

    val ytReady = (ytSaved || ytKey.isNotBlank()) && ytChannel.isNotBlank()
    val fbReady = fbPageId.isNotBlank() && (fbSaved || fbToken.isNotBlank())
    val hasError = stats.ytError.isNotBlank() || stats.fbError.isNotBlank()

    val uriHandler = LocalUriHandler.current
    val openUrl: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    // Pull down to refresh with the saved connections; same fetch as Save & refresh.
    val refresh: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                stats = Repo.refreshAll(context)
                busy = false
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = busy,
        onRefresh = refresh,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---- Hero ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(Color(0xFF262B52), Color(0xFF121422))),
                    RoundedCornerShape(24.dp),
                )
                .border(1.dp, Line, RoundedCornerShape(24.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Social Stats", color = TextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("YouTube and Facebook at a glance", color = TextLo, fontSize = 13.sp)
                }
                StatusChip(
                    text = when {
                        hasError -> "Attention"
                        ytReady && fbReady -> "Connected"
                        else -> "Setup needed"
                    },
                    color = when {
                        hasError -> Warn
                        ytReady && fbReady -> Good
                        else -> TextLo
                    },
                )
            }
        }

        // ---- Error banners ----
        if (stats.ytError.isNotBlank()) {
            ErrorCard(
                title = "YouTube needs attention",
                message = stats.ytError,
                hint = "Check your API key and channel handle.",
                action = "Open YouTube guide",
                onAction = { ytGuideOpen = true; helpOpen = true },
            )
        }
        if (stats.fbError.isNotBlank()) {
            ErrorCard(
                title = "Facebook needs attention",
                message = stats.fbError,
                hint = "Your Page token has probably expired. Follow the renewal guide below.",
                action = "Open renewal guide",
                onAction = { fbGuideOpen = true; helpOpen = true },
            )
        }

        // ---- Live stats ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                modifier = Modifier.weight(1f),
                name = "YouTube",
                iconRes = R.drawable.ic_youtube,
                accent = YtRed,
                gradient = listOf(Color(0xFF3A1B21), Color(0xFF171822)),
                configured = ytReady,
                big = stats.ytSubs,
                bigLabel = "subscribers",
                views = stats.ytVideoViews,
                comments = stats.ytComments,
                title = stats.ytVideoTitle,
                onClick = { if (ytReady) openUrl(youtubeDashboardUrl(stats.ytChannelId)) },
            )
            StatTile(
                modifier = Modifier.weight(1f),
                name = "Facebook",
                iconRes = R.drawable.ic_facebook,
                accent = FbBlue,
                gradient = listOf(Color(0xFF17315A), Color(0xFF171822)),
                configured = fbReady,
                big = stats.fbFollowers,
                bigLabel = "followers",
                views = stats.fbVideoViews,
                comments = stats.fbComments,
                title = stats.fbVideoTitle,
                onClick = { if (fbPageId.isNotBlank()) openUrl("https://www.facebook.com/${fbPageId.trim()}") },
            )
        }
        Text(
            text = if (stats.updatedAt == 0L) "Not refreshed yet"
            else "Updated " + SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(stats.updatedAt)),
            color = TextLo,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp),
        )

        // ---- Connections ----
        SectionLabel("CONNECTIONS")

        Card2 {
            CardHeader(R.drawable.ic_youtube, "YouTube", "Data API v3")
            Spacer(Modifier.height(14.dp))
            SecretField(
                saved = ytSaved,
                replacing = ytReplacing,
                onReplacingChange = { ytReplacing = it },
                value = ytKey, onValueChange = { ytKey = it },
                label = "API key",
            )
            Spacer(Modifier.height(10.dp))
            AppField(
                value = ytChannel, onValueChange = { ytChannel = it },
                label = "Channel @handle or ID",
            )
        }

        Card2 {
            CardHeader(R.drawable.ic_facebook, "Facebook Page", "Graph API")
            Spacer(Modifier.height(14.dp))
            AppField(
                value = fbPageId, onValueChange = { fbPageId = it },
                label = "Page ID",
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(10.dp))
            SecretField(
                saved = fbSaved,
                replacing = fbReplacing,
                onReplacingChange = { fbReplacing = it },
                value = fbToken, onValueChange = { fbToken = it },
                label = "Page access token",
            )
        }

        Button(
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF10132A)),
            onClick = {
                busy = true
                // A blank secret field means "keep the saved one".
                val current = Store.loadSettings(context)
                saveFailed = !Store.saveSettings(
                    context,
                    Settings(ytKey.ifBlank { current.ytKey }, ytChannel, fbPageId, fbToken.ifBlank { current.fbToken }),
                )
                val saved = Store.loadSettings(context)
                ytSaved = saved.ytKey.isNotBlank()
                fbSaved = saved.fbToken.isNotBlank()
                ytKey = ""
                fbToken = ""
                ytReplacing = false
                fbReplacing = false
                Scheduler.schedule(context)
                scope.launch {
                    stats = Repo.refreshAll(context)
                    busy = false
                }
            },
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF10132A),
                )
                Spacer(Modifier.width(10.dp))
                Text("Refreshing…", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            } else {
                Text("Save & refresh", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }

        if (saveFailed) {
            Text(
                "Your phone could not encrypt the key, so it was not saved. Please try again.",
                color = Warn,
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // ---- Help ----
        HelpHeader(expanded = helpOpen, onToggle = { helpOpen = !helpOpen })

        if (helpOpen) {

        GuideCard(
            iconRes = R.drawable.ic_launcher,
            title = "Add the widget",
            subtitle = "Put Social Stats on your home screen.",
            expanded = widgetGuideOpen,
            onToggle = { widgetGuideOpen = !widgetGuideOpen },
        ) {
            Step(1, "Long-press an empty spot on your home screen.")
            Step(2, "Tap Widgets and find \"Social Stats\".")
            Step(3, "Drag it onto the home screen. You can resize it.")
            Step(4, "Tap the widget to open this app, or tap Refresh on the widget to update now. It also refreshes about every 15 minutes.")
        }

        GuideCard(
            iconRes = R.drawable.ic_facebook,
            title = "Renew Facebook token",
            subtitle = "About 3 minutes. Only needed if Facebook stops updating.",
            expanded = fbGuideOpen,
            onToggle = { fbGuideOpen = !fbGuideOpen },
        ) {
            Text(
                "Your Meta app \"social-widget\" is already set up. You only need a new token.",
                color = TextLo,
                fontSize = 13.sp,
            )
            Step(
                1,
                "Open the Graph API Explorer. Meta App: social-widget. User or Page: User Token. " +
                    "Add the permissions pages_show_list, pages_read_engagement and business_management, " +
                    "then tap Generate Access Token and approve your Page and business.",
                linkLabel = "Open Graph API Explorer",
                linkUrl = "https://developers.facebook.com/tools/explorer/",
            )
            Step(
                2,
                "Copy the token with the copy icon next to Access Token. Open the Token Debugger, paste it, " +
                    "tap Debug, then tap Extend Access Token and copy the new long-lived token.",
                linkLabel = "Open Token Debugger",
                linkUrl = "https://developers.facebook.com/tools/debug/accesstoken/",
            )
            Step(
                3,
                "Back in the Graph API Explorer, paste the long token into the Access Token box. " +
                    "Type this in the request bar and tap Submit:",
                code = "me/accounts?fields=id,name,access_token",
            )
            Step(
                4,
                "In the result, find your Page. Its id is your Page ID and its access_token is your Page token. " +
                    "Copy the access_token value without the quotes.",
            )
            Step(5, "Paste it into \"Page access token\" above and tap Save & refresh.")
        }

        GuideCard(
            iconRes = R.drawable.ic_youtube,
            title = "Get a YouTube API key",
            subtitle = "Where to find the key and your channel handle.",
            expanded = ytGuideOpen,
            onToggle = { ytGuideOpen = !ytGuideOpen },
        ) {
            Step(
                1,
                "Open Google Cloud Console and create or select a project.",
                linkLabel = "Open Google Cloud Console",
                linkUrl = "https://console.cloud.google.com/",
            )
            Step(
                2,
                "Enable \"YouTube Data API v3\" for that project.",
                linkLabel = "Open YouTube Data API page",
                linkUrl = "https://console.cloud.google.com/apis/library/youtube.googleapis.com",
            )
            Step(
                3,
                "Go to Credentials, tap Create credentials, then API key. Restrict it to YouTube Data API v3 " +
                    "and leave application restrictions on None.",
                linkLabel = "Open Credentials",
                linkUrl = "https://console.cloud.google.com/apis/credentials",
            )
            Step(
                4,
                "Your channel handle starts with @ and is shown on your channel page and in YouTube Studio " +
                    "under Customization, Profile.",
                linkLabel = "Open YouTube Studio",
                linkUrl = "https://studio.youtube.com/",
            )
        }
        }

        Text(
            "Your keys stay on this device, are never shown again after saving, and the app is protected by your screen lock. Android backup is off, and the app talks directly to Google and Meta.",
            color = TextLo,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 18.dp),
        )
    }
    }
}

/** Opens the channel's YouTube Studio dashboard; falls back to Studio's start page until the ID is known. */
private fun youtubeDashboardUrl(channelId: String) =
    if (channelId.isNotBlank()) "https://studio.youtube.com/channel/$channelId" else "https://studio.youtube.com/"

// ---------- Building blocks ----------

@Composable
private fun HelpHeader(expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel1, RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .clickable(onClick = onToggle)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Accent.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, Accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("?", color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Help & guides", color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Add the widget, get keys, renew the Facebook token", color = TextLo, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (expanded) "Hide" else "Show",
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextLo,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun Card2(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel1, RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun CardHeader(iconRes: Int, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextLo, fontSize = 12.sp)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = Line,
    focusedLabelColor = Accent,
    unfocusedLabelColor = TextLo,
    focusedTextColor = TextHi,
    unfocusedTextColor = TextHi,
    cursorColor = Accent,
    focusedContainerColor = Panel2.copy(alpha = 0.5f),
    unfocusedContainerColor = Panel2.copy(alpha = 0.5f),
)

@Composable
private fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Write-only secret field. A saved secret is never shown or copyable: it appears as "Saved" and can only be
 * replaced. The text field only ever holds a new value the user is typing or pasting right now.
 */
@Composable
private fun SecretField(
    saved: Boolean,
    replacing: Boolean,
    onReplacingChange: (Boolean) -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    if (saved && !replacing) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Panel2.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .border(1.dp, Line, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = TextLo, fontSize = 12.sp)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Good, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text("Saved on this device", color = TextHi, fontSize = 15.sp)
                }
            }
            Text(
                "Replace",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onReplacingChange(true) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            )
        }
    } else {
        var visible by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(if (saved) "New $label" else label) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (visible) "Hide" else "Show",
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { visible = !visible }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                    if (saved) {
                        Text(
                            "Cancel",
                            color = TextLo,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable {
                                    onValueChange("")
                                    onReplacingChange(false)
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        )
                    }
                }
            },
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    name: String,
    iconRes: Int,
    accent: Color,
    gradient: List<Color>,
    configured: Boolean,
    big: Long,
    bigLabel: String,
    views: Long,
    comments: Long,
    title: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient), RoundedCornerShape(20.dp))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(enabled = configured, onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(iconRes), contentDescription = name, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(name, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (configured) {
                Spacer(Modifier.weight(1f))
                Text("Open ›", color = accent.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!configured) {
            Text("Not set up", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Add your keys below", color = TextLo, fontSize = 12.sp)
        } else {
            Text(formatCount(big), color = TextHi, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(bigLabel, color = TextLo, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painterResource(R.drawable.ic_eye),
                    contentDescription = "Views",
                    modifier = Modifier.size(14.dp),
                    colorFilter = ColorFilter.tint(TextLo),
                )
                Spacer(Modifier.width(5.dp))
                Text(formatCount(views), color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(14.dp))
                Image(
                    painterResource(R.drawable.ic_comment),
                    contentDescription = "Comments",
                    modifier = Modifier.size(13.dp),
                    colorFilter = ColorFilter.tint(TextLo),
                )
                Spacer(Modifier.width(5.dp))
                Text(formatCount(comments), color = TextHi, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            if (title.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(title, color = TextLo, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ErrorCard(title: String, message: String, hint: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Warn.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .border(1.dp, Warn.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = Warn, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(message, color = TextHi, fontSize = 13.sp)
        Text(hint, color = TextLo, fontSize = 12.sp)
        OutlinedButton(
            onClick = onAction,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Warn.copy(alpha = 0.6f)),
        ) { Text(action, color = Warn) }
    }
}

@Composable
private fun GuideCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel1, RoundedCornerShape(20.dp))
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextHi, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextLo, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (expanded) "Hide" else "Show",
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}

@Composable
private fun Step(
    number: Int,
    text: String,
    linkLabel: String? = null,
    linkUrl: String? = null,
    code: String? = null,
) {
    val uriHandler = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Accent.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, Accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = TextHi, fontSize = 14.sp, lineHeight = 20.sp)
            if (code != null) {
                SelectionContainer {
                    Text(
                        code,
                        color = TextHi,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel2, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    )
                }
            }
            if (linkLabel != null && linkUrl != null) {
                OutlinedButton(
                    onClick = { uriHandler.openUri(linkUrl) },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                ) { Text(linkLabel, color = Accent, fontSize = 13.sp) }
            }
        }
    }
}
