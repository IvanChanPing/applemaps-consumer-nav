package com.example.applemaps.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.applemaps.R
import com.example.applemaps.ui.theme.LocalAppleColors

/**
 * Transit station place-card — traced from maps.apple.com (docs/trace/station-place-card): the #F2F2F2
 * card (r12) with a 28sp/700 title + #0A7BFF locality + 30dp close circle, then a "Departures" 20sp/600
 * section whose white r16 platter lists departure rows — line name 17sp/600 with a green
 * dot.radiowaves.up.forward live badge (#34C759), direction 17sp, and a right-aligned "N min" countdown —
 * closed by a "Transit information provided by <provider>" attribution (13sp #8E8E93). Shell driven by
 * sample data; the look/values are from the trace.
 */
data class Departure(val line: String, val direction: String, val minutes: String, val live: Boolean = true)
data class StationInfo(val name: String, val locality: String, val departures: List<Departure>, val provider: String = "Foursquare")

// Header slot (pinned in the sheet) — title / locality / close.
@Composable
fun StationCardHeader(station: StationInfo, onClose: () -> Unit = {}) {
    val c = LocalAppleColors.current
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(station.name, color = c.glyphDefault, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
            if (station.locality.isNotEmpty()) Text(station.locality, color = Color(0xFF0A7BFF), fontSize = 14.sp)
        }
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x5CC7C7C7)).clickable { onClose() }, contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_xmark), "Close", Modifier.size(12.dp), colorFilter = ColorFilter.tint(c.glyphMuted))
        }
    }
}

// Body slot (scrollable) — Departures platter + transit attribution.
@Composable
fun StationCardBody(station: StationInfo) {
    val c = LocalAppleColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Departures", color = c.glyphDefault, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
            station.departures.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(color = c.borderMuted, thickness = 1.dp, modifier = Modifier.padding(start = 20.dp))
                DepartureRow(d)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Transit information provided by ${station.provider}", color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp))
    }
}

@Composable
private fun DepartureRow(d: Departure) {
    val c = LocalAppleColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.line, color = c.glyphDefault, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (d.live) {
                    Spacer(Modifier.width(5.dp))
                    Image(painterResource(R.drawable.ic_dot_radiowaves_up_forward), "Live", Modifier.size(11.dp), colorFilter = ColorFilter.tint(Color(0xFF34C759)))
                }
            }
            Text(d.direction, color = c.glyphDefault, fontSize = 17.sp)
        }
        Text(d.minutes, color = c.glyphDefault, fontSize = 17.sp)
        Spacer(Modifier.width(4.dp))
        Text("min", color = c.glyphMuted, fontSize = 17.sp)
    }
}
