package cz.kuba.dash407.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.Prefs

/**
 * Všechny aplikace. Klepnutí spustí, podržení přidá do doku.
 * V režimu výběru do doku (favoritesMode) klepnutí přidává a odebírá.
 */
@Composable
fun AppsScreen(favoritesMode: Boolean = false) {
    val context = LocalContext.current
    val apps by AppsRepo.apps.collectAsState()
    val favorites by Prefs.favorites.collectAsState()
    var query by remember { mutableStateOf("") }
    val dockFull = "Dok je plný (nejvýš ${Prefs.MAX_FAVORITES} aplikací)"
    val shown = if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }

    fun toggle(pkg: String) {
        if (!Prefs.toggleFavorite(pkg)) Toast.makeText(context, dockFull, Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Hledat aplikaci") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)
        )
        Text(
            if (favoritesMode) "Klepnutím aplikaci přidáš do doku nebo ji z něj odebereš (hvězdička)."
            else "Klepnutím aplikaci spustíš. Podržením ji přidáš do doku nebo z něj odebereš.",
            color = Palette.Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, end = 20.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(shown, key = { it.packageName }) { app ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { if (favoritesMode) toggle(app.packageName) else AppsRepo.launch(context, app.packageName) },
                            onLongClick = { toggle(app.packageName) }
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(icon, contentDescription = null, modifier = Modifier.size(68.dp))
                        } else {
                            Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                                Text(app.label.take(2), color = Palette.Text, fontSize = 24.sp)
                            }
                        }
                        if (app.packageName in favorites) {
                            Icon(Icons.Filled.Star, "V doku", Modifier.size(22.dp), tint = Palette.Amber)
                        }
                    }
                    Text(
                        app.label,
                        color = Palette.Text,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
                    )
                }
            }
        }
    }
}
