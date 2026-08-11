package com.example.applemaps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.tween
import com.example.applemaps.R
import com.example.applemaps.map.Place
import com.example.applemaps.ui.anim.AppleDuration
import com.example.applemaps.ui.anim.AppleEasing

/**
 * Full-screen Apple-style search: a rounded search field (auto-focused, keyboard up) + a live results list
 * from PlaceRepository.searchPlaces (keyless Nominatim). Tapping a result selects that place. Stateless —
 * the caller owns `query`/`results` and handles the debounced fetch + selection.
 */
@Composable
fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Place>,
    onSelect: (Place) -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFF2F2F7)).statusBarsPadding().padding(horizontal = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE3E3E8))
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_magnifyingglass), null, Modifier.size(15.dp), colorFilter = ColorFilter.tint(Color(0xFF8E8E93)))
                Spacer(Modifier.width(7.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search Maps", color = Color(0xFF8E8E93), fontSize = 17.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = Color(0xFF1C1C1E), fontSize = 17.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "Cancel", color = Color(0xFF007AFF), fontSize = 17.sp,
                modifier = Modifier.clickable { keyboard?.hide(); onClose() },
            )
        }

        LazyColumn(Modifier.fillMaxWidth()) {
            items(results, key = { "${it.name}:${it.lat}:${it.lon}" }) { p ->
                // P1 1.2: result rows fade/slide in as they populate (extracted .14s SearchResult ease-out)
                Column(Modifier.animateItem(
                    fadeInSpec = tween(AppleDuration.SearchResult, easing = AppleEasing.EaseOutStd),
                    placementSpec = tween(AppleDuration.SearchResult, easing = AppleEasing.Standard),
                    fadeOutSpec = tween(AppleDuration.SearchResult, easing = AppleEasing.EaseOutStd),
                )) {
                Row(
                    Modifier.fillMaxWidth().clickable { keyboard?.hide(); onSelect(p) }.padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0xFFD1D1D6)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(painterResource(R.drawable.ic_mappin_and_ellipse), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color(0xFF8E8E93)))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(p.name, color = Color(0xFF1C1C1E), fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        val sub = listOf(p.category, p.locality).filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { p.address }
                        if (sub.isNotBlank()) Text(sub, color = Color(0xFF8E8E93), fontSize = 13.sp, maxLines = 1)
                    }
                }
                Box(Modifier.fillMaxWidth().padding(start = 45.dp).height(0.5.dp).background(Color(0x14000000)))
                }
            }
        }
    }
}
