package com.example.applemaps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.applemaps.R
import com.example.applemaps.map.AppleBrowsePlace
import com.example.applemaps.map.AppleCategoryResults
import com.example.applemaps.map.AppleGuide
import com.example.applemaps.ui.theme.LocalAppleColors

sealed interface AppleBrowseState {
    val title: String
    data class Loading(override val title: String) : AppleBrowseState
    data class Category(val content: AppleCategoryResults) : AppleBrowseState {
        override val title: String get() = content.title
    }
    data class Guide(val content: AppleGuide) : AppleBrowseState {
        override val title: String get() = content.title
    }
    data class Error(override val title: String, val message: String) : AppleBrowseState
}

/** Native result/Guide tray fed by [com.example.applemaps.map.AppleBrowseClient]. */
@Composable
fun AppleBrowseHeader(state: AppleBrowseState, onClose: () -> Unit) {
    val colors = LocalAppleColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(state.title, color = colors.glyphDefault, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2,
            modifier = Modifier.weight(1f))
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(Color(0x14000000)).applePressScale(onClose),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_xmark), "Close", Modifier.size(14.dp),
                colorFilter = ColorFilter.tint(colors.glyphMuted))
        }
    }
}

@Composable
fun AppleBrowseBody(state: AppleBrowseState, onPlace: (AppleBrowsePlace) -> Unit) {
    when (state) {
        is AppleBrowseState.Loading -> Box(
            Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = Color(0xFF0A84FF), strokeWidth = 3.dp) }
        is AppleBrowseState.Error -> Text(
            state.message,
            color = LocalAppleColors.current.glyphMuted,
            fontSize = 16.sp,
            modifier = Modifier.padding(20.dp),
        )
        is AppleBrowseState.Category -> PlaceRows(state.content.places, onPlace)
        is AppleBrowseState.Guide -> GuideBody(state.content, onPlace)
    }
}

@Composable
private fun GuideBody(guide: AppleGuide, onPlace: (AppleBrowsePlace) -> Unit) {
    guide.heroUrl?.let { url ->
        AsyncImage(
            model = url,
            contentDescription = guide.title,
            modifier = Modifier.fillMaxWidth().height(190.dp).padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Spacer(Modifier.height(12.dp))
    }
    Column(Modifier.padding(horizontal = 20.dp)) {
        if (guide.publisher.isNotBlank()) {
            Text(guide.publisher, color = Color(0xFF0A84FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
        }
        guide.description?.let {
            Text(it, color = LocalAppleColors.current.glyphDefault, fontSize = 15.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(16.dp))
        }
        Text("${guide.places.size} Places", color = LocalAppleColors.current.glyphMuted,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(6.dp))
    PlaceRows(guide.places, onPlace)
}

@Composable
private fun PlaceRows(places: List<AppleBrowsePlace>, onPlace: (AppleBrowsePlace) -> Unit) {
    val colors = LocalAppleColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        places.forEach { result ->
            Row(
                Modifier.fillMaxWidth().applePressScale { onPlace(result) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE5E5EA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(painterResource(R.drawable.ic_mappin_and_ellipse), null, Modifier.size(19.dp),
                        colorFilter = ColorFilter.tint(Color(0xFF0A84FF)))
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(result.place.name, color = colors.glyphDefault, fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2)
                    val subtitle = listOf(result.place.category, result.place.locality)
                        .filter(String::isNotBlank).joinToString(" · ")
                    if (subtitle.isNotBlank()) Text(subtitle, color = colors.glyphMuted, fontSize = 14.sp, maxLines = 2)
                    result.note?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = colors.glyphMuted, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3)
                    }
                }
            }
            HorizontalDivider(color = colors.borderMuted, thickness = 0.5.dp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
