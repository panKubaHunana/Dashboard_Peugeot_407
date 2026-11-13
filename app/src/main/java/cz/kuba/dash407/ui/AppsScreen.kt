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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.kuba.dash407.R
import cz.kuba.dash407.data.AppsRepo
import cz.kuba.dash407.data.Prefs

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val apps by AppsRepo.apps.collectAsState()
    val favorites by Prefs.favorites.collectAsState()
    val dockFull = stringResource(R.string.apps_dock_full, Prefs.MAX_FAVORITES)

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.apps_hint),
            color = Palette.Muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(apps, key = { it.packageName }) { app ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { AppsRepo.launch(context, app.packageName) },
                            onLongClick = {
                                if (!Prefs.toggleFavorite(app.packageName)) {
                                    Toast.makeText(context, dockFull, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        val icon = app.icon
                        if (icon != null) {
                            Image(icon, contentDescription = null, modifier = Modifier.size(72.dp))
                        } else {
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                Text(app.label.take(2), color = Palette.Text, fontSize = 24.sp)
                            }
                        }
                        if (app.packageName in favorites) {
                            Icon(
                                Icons.Filled.Star,
                                stringResource(R.string.apps_in_dock),
                                Modifier.size(22.dp),
                                tint = Palette.Amber
                            )
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
