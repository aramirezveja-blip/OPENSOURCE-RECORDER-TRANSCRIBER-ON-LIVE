package com.s1trok.voznota

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ==========================================
// PALETA DE COLORES Y TEMA NOTHING PHONE (DARK MINIMALIST)
// ==========================================
private val NothingBlack = Color(0xFF080808)
private val NothingSurface = Color(0xFF141414)
private val NothingSurfaceBorder = Color(0xFF242424)
private val NothingRed = Color(0xFFFF2A2A)
private val NothingWhite = Color(0xFFFFFFFF)
private val NothingGray = Color(0xFF8E8E93)
private val NothingDarkGray = Color(0xFF1E1E1E)

private val NothingColorScheme = darkColorScheme(
    primary = NothingWhite,
    onPrimary = NothingBlack,
    secondary = NothingGray,
    background = NothingBlack,
    surface = NothingSurface,
    onBackground = NothingWhite,
    onSurface = NothingWhite,
    error = NothingRed,
    outline = NothingSurfaceBorder,
)

class MainActivity : ComponentActivity() {

    private val vm: TranscriptorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = NothingColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TranscriptorScreen(vm)
                }
            }
        }
    }
}

@Composable
fun TranscriptorScreen(vm: TranscriptorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Transcribir, 1: Grabaciones
    var searchQuery by remember { mutableStateOf("") }

    // Animación de pulso para el indicador REC
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        vm.startListening()
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.startListening()
            }
        } else {
            vm.showMessage("Sin permiso de micrófono no se puede transcribir. Puedes activarlo en Ajustes.")
        }
    }

    fun onMicClick() {
        when {
            state.listening -> vm.stopListening()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.startListening()
                }
            }
            else -> audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Cabecera Minimalista estilo Nothing con Badge Profesional del Logo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = NothingSurface,
                    border = BorderStroke(1.dp, NothingSurfaceBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo),
                            contentDescription = "Logo VozNota",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "VOZNOTA",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.5.sp,
                        color = NothingWhite
                    )
                    Text(
                        text = "DESARROLLADO POR ANGEL RAMIREZ VEGA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = NothingGray,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            if (state.listening) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = NothingRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, NothingRed.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(alphaPulse)
                                .background(NothingRed, CircleShape)
                        )
                        Text(
                            text = "REC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = NothingRed
                        )
                    }
                }
            }
        }

        // Pestañas Segmentadas (TRANSCRIBIR / GRABACIONES)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NothingSurface, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val tabNames = listOf("TRANSCRIBIR", "GRABACIONES (${state.notes.size})")
            tabNames.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) NothingWhite else Color.Transparent)
                        .clickable { selectedTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isSelected) NothingBlack else NothingGray
                    )
                }
            }
        }

        // Mensaje de Error / Ajustes
        state.message?.let { msg ->
            Card(
                colors = CardDefaults.cardColors(containerColor = NothingDarkGray),
                border = BorderStroke(1.dp, NothingRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(msg, color = NothingWhite, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                            )
                        }) {
                            Text("Abrir Ajustes", color = NothingRed, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { vm.showMessage(null) }) {
                            Text("Cerrar", color = NothingGray)
                        }
                    }
                }
            }
        }

        if (selectedTab == 0) {
            // PESTAÑA 1: PANTALLA DE TRANSCRIPCIÓN EN DIRECTO
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = NothingSurface),
                    border = BorderStroke(1.dp, if (state.listening) NothingRed.copy(alpha = 0.6f) else NothingSurfaceBorder),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        val displayText = (state.draft + " " + state.partial).trim()
                        if (displayText.isEmpty()) {
                            Text(
                                text = "Pulsa «ESCUCHAR» y habla. La transcripción se procesará continuamente en segundo plano...",
                                color = NothingGray,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item {
                                    Text(
                                        text = state.draft,
                                        color = NothingWhite,
                                        fontSize = 18.sp,
                                        lineHeight = 26.sp
                                    )
                                    if (state.partial.isNotEmpty()) {
                                        Text(
                                            text = if (state.draft.isNotEmpty()) " " + state.partial else state.partial,
                                            color = NothingRed,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium,
                                            lineHeight = 26.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Botones de Control Principal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = ::onMicClick,
                        modifier = Modifier.weight(1.3f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.listening) NothingRed else NothingWhite,
                            contentColor = if (state.listening) NothingWhite else NothingBlack
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = if (state.listening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.listening) "DETENER" else "ESCUCHAR",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    OutlinedButton(
                        onClick = vm::saveNote,
                        enabled = (state.draft + state.partial).isNotBlank(),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if ((state.draft + state.partial).isNotBlank()) NothingWhite else NothingSurfaceBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingWhite),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = vm::clearDraft,
                        enabled = (state.draft + state.partial).isNotBlank(),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, if ((state.draft + state.partial).isNotBlank()) NothingSurfaceBorder else NothingDarkGray),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NothingGray),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar", fontSize = 13.sp)
                    }
                }
            }
        } else {
            // PESTAÑA 2: SECCIÓN DE GRABACIONES ORDENADAS POR FECHA
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Buscador de Grabaciones
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar en grabaciones...", color = NothingGray, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NothingGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NothingSurface,
                        unfocusedContainerColor = NothingSurface,
                        focusedBorderColor = NothingWhite,
                        unfocusedBorderColor = NothingSurfaceBorder,
                        focusedTextColor = NothingWhite,
                        unfocusedTextColor = NothingWhite
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                val filteredNotes = state.notes.filter {
                    it.text.contains(searchQuery, ignoreCase = true)
                }

                if (filteredNotes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No tienes grabaciones guardadas." else "No se encontraron coincidencias.",
                            color = NothingGray,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    // Agrupado ordenado por fecha
                    val groupedNotes = filteredNotes.groupBy { getRelativeDateGroup(it.createdAt) }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        groupedNotes.forEach { (groupTitle, notesInGroup) ->
                            item {
                                Text(
                                    text = groupTitle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.5.sp,
                                    color = NothingRed,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                )
                            }

                            items(notesInGroup, key = { it.id }) { note ->
                                RecordingCard(
                                    note = note,
                                    onShare = { text ->
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Compartir nota"))
                                    },
                                    onCopy = { text ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Nota", text)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { vm.deleteNote(note.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingCard(
    note: Note,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm - dd MMM yyyy", Locale.getDefault()) }
    val formattedDate = remember(note.createdAt) { dateFormat.format(Date(note.createdAt)) }
    val wordCount = remember(note.text) { note.text.trim().split("\\s+".toRegex()).size }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NothingSurface),
        border = BorderStroke(1.dp, NothingSurfaceBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedDate,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NothingGray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = NothingDarkGray,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "$wordCount palabras",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = NothingWhite
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = { onCopy(note.text) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar",
                            tint = NothingWhite,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = { onShare(note.text) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartir",
                            tint = NothingWhite,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Borrar",
                            tint = NothingRed,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.text,
                color = NothingWhite,
                fontSize = 14.sp,
                lineHeight = 21.sp
            )
        }
    }
}

private fun getRelativeDateGroup(timestamp: Long): String {
    val now = Calendar.getInstance()
    val noteCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    val isSameDay = now.get(Calendar.YEAR) == noteCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == noteCal.get(Calendar.DAY_OF_YEAR)

    if (isSameDay) return "HOY"

    now.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = now.get(Calendar.YEAR) == noteCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == noteCal.get(Calendar.DAY_OF_YEAR)

    if (isYesterday) return "AYER"

    now.add(Calendar.DAY_OF_YEAR, -6)
    if (noteCal.after(now)) return "ESTA SEMANA"

    val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp)).uppercase(Locale.getDefault())
}
