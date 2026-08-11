package com.example.applemaps.map

/** Consumer-renderer route boundary; geometry and traffic remain provider-neutral. */
object RouteLayer {
    fun drawRoutes(
        controller: ConsumerMapController,
        routes: List<Route>,
        fitAndReveal: Boolean = true,
        labels: List<Pair<String, String>>? = null,
    ) = controller.drawRoutes(routes, labels, fitAndReveal)

    fun setProgress(controller: ConsumerMapController, progress: Float) = controller.setRouteProgress(progress)

    fun clear(controller: ConsumerMapController) = controller.clearRoutes()
}

internal data class TrafficColorStop(val progress: Double, val level: TrafficLevel)

internal fun trafficColorStops(points: List<MapCoordinate>, intervals: List<TrafficInterval>): List<TrafficColorStop> {
    if (points.size < 2 || intervals.isEmpty()) return listOf(TrafficColorStop(0.0, TrafficLevel.NORMAL))
    val cumulative = DoubleArray(points.size)
    for (i in 1 until points.size) cumulative[i] = cumulative[i - 1] + points[i - 1].distanceTo(points[i])
    val total = cumulative.last()
    if (total <= 0.0) return listOf(TrafficColorStop(0.0, TrafficLevel.NORMAL))
    val out = ArrayList<TrafficColorStop>()
    intervals.sortedBy { it.startPointIndex }.forEach { interval ->
        val index = interval.startPointIndex.coerceIn(0, points.lastIndex)
        val progress = (cumulative[index] / total).coerceIn(0.0, 1.0)
        if (out.lastOrNull()?.progress == progress) out[out.lastIndex] = TrafficColorStop(progress, interval.level)
        else out.add(TrafficColorStop(progress, interval.level))
    }
    if (out.firstOrNull()?.progress != 0.0) out.add(0, TrafficColorStop(0.0, TrafficLevel.NORMAL))
    return out
}
