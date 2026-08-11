package com.example.applemaps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.applemaps.R
import com.example.applemaps.ui.components.AppleAppearOnce
import com.example.applemaps.ui.theme.LocalAppleColors

/**
 * Home / front page — the sheet's default content before searching. Traced from maps.apple.com
 * (docs/trace/home-front-page): a "Find Nearby" 20sp/600 header over a 2-column grid of white r16
 * category pills (173×50, 30dp icon + 17sp label), a horizontal row of editorial "bricks" (154×196 r16
 * with a bottom gradient + publisher + 16sp/800 white title), a "Recently searched" list (17sp title +
 * secondary subtitle + xmark.circle.fill clear), and the legal footer (11.7sp #8E8E93 + 11sp/600 links).
 * UI shell driven by sample data; the look/values are from the trace.
 */
private data class NearbyCat(val label: String, val icon: Int)
private data class HomeBrick(val title: String, val publisher: String, val color: Color)
private data class RecentSearch(val title: String, val subtitle: String)

private val findNearby = listOf(
    NearbyCat("Restaurants", R.drawable.ic_fork_knife),
    NearbyCat("Fast Food", R.drawable.ic_takeoutbag_and_cup_and_straw_fill),
    NearbyCat("Coffee", R.drawable.ic_cup_and_saucer_fill),
    NearbyCat("Groceries", R.drawable.ic_shippingbox_fill),
    NearbyCat("Shopping", R.drawable.ic_bag_fill),
    NearbyCat("Bars", R.drawable.ic_wineglass_fill),
    NearbyCat("Parks", R.drawable.ic_leaf_fill),
    NearbyCat("Hotels", R.drawable.ic_building_2_fill),
    NearbyCat("Things to Do", R.drawable.ic_binoculars_fill),
)
private val bricks = listOf(
    HomeBrick("Karl-Anthony Towns' New York City Spots", "The Infatuation", Color(0xFF4A5A6A)),
    HomeBrick("Best Coffee in Brooklyn", "The Infatuation", Color(0xFF6A4A3A)),
    HomeBrick("A Perfect Day in Manhattan", "Lonely Planet", Color(0xFF3A5A4A)),
)
private val recent = listOf(
    RecentSearch("14 Street–Union Square Station", "New York"),
    RecentSearch("Empire State Building", "New York"),
)

@Composable
fun HomeFrontPage(onCategory: (String) -> Unit = {}, onRecent: (String) -> Unit = {}) {
    val c = LocalAppleColors.current
    // NOTE: no verticalScroll here — the bottom-sheet body already scrolls; a nested vertical scroll gets
    // infinite-height constraints from the sheet and crashes (IllegalStateException). Plain Column, like PlaceCardBody.
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // P1 4.1: staggered entrance (extracted moveIn+fadeIn, 500ms CurveB, 40ms/index) — was only in the
        // unused SheetContent.kt; wired here on the LIVE front page via the existing AppleAppearOnce helper.
        AppleAppearOnce(0) {
        Text("Find Nearby", color = c.glyphDefault, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 9.dp))
        }
        findNearby.chunked(2).forEachIndexed { gi, row ->
            AppleAppearOnce(gi + 1) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cat -> NearbyPill(cat, Modifier.weight(1f)) { onCategory(cat.label) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            }
        }
        Spacer(Modifier.height(12.dp))
        AppleAppearOnce(6) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            bricks.forEach { BrickCard(it) }
        }
        }
        Spacer(Modifier.height(20.dp))
        recent.forEachIndexed { ri, r ->
            AppleAppearOnce(7 + ri) {
            Column {
            Row(Modifier.fillMaxWidth().applePressScale { onRecent(r.title) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.title, color = c.glyphDefault, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(r.subtitle, color = c.glyphMuted, fontSize = 17.sp, modifier = Modifier.padding(end = 16.dp))
                Image(painterResource(R.drawable.ic_xmark_circle_fill), "Clear", Modifier.size(16.dp))
            }
            HorizontalDivider(color = c.borderMuted, thickness = 1.dp)
            }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Copyright © 2026 Apple Inc.", color = Color(0xFF8E8E93), fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row {
            listOf("Privacy", "Terms", "Imagery").forEachIndexed { i, l ->
                if (i > 0) Spacer(Modifier.width(12.dp))
                Text(l, color = c.glyphMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NearbyPill(cat: NearbyCat, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalAppleColors.current
    Row(
        modifier.height(50.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).applePressScale(onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(cat.icon), cat.label, Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text(cat.label, color = c.glyphDefault, fontSize = 17.sp, maxLines = 1)
    }
}

@Composable
private fun BrickCard(b: HomeBrick) {
    Box(Modifier.size(width = 154.dp, height = 196.dp).clip(RoundedCornerShape(16.dp)).background(b.color)) {
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xA6000000)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(b.publisher, color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(b.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 20.sp)
        }
    }
}
