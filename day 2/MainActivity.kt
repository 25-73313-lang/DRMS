package com.example.deliveryroutesystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private val HUB = Hub("Barangay 2 Hall", LatLng(14.07575, 120.63223))

private const val STREET_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val SATELLITE_STYLE_JSON = """
{
  "version": 8,
  "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
  "sources": {
    "imagery": {
      "type": "raster",
      "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "Imagery © Esri, Maxar, Earthstar Geographics"
    }
  },
  "layers": [ { "id": "imagery", "type": "raster", "source": "imagery" } ]
}
"""

private const val SECTION_NONE = -1
private const val SECTION_ORDERS = 0
private const val SECTION_SETUP = 1
private const val SECTION_SUMMARY = 2

private val Navy = ComposeColor(0xFF0B2B3B)
private val Teal = ComposeColor(0xFF0E7C7B)
private val Mint = ComposeColor(0xFF19B38A)
private val ScreenBg = ComposeColor(0xFFF2F5F8)
private val Ink = ComposeColor(0xFF132733)
private val Muted = ComposeColor(0xFF6B7C88)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Teal,
                    secondary = Mint,
                    background = ScreenBg,
                    surface = ComposeColor.White
                )
            ) {
                DeliveryRouteApp()
            }
        }
    }
}

private fun applyMapStyle(context: Context, map: MapLibreMap, satellite: Boolean) {
    val builder = if (satellite) Style.Builder().fromJson(SATELLITE_STYLE_JSON)
    else Style.Builder().fromUri(STREET_STYLE_URL)

    map.setStyle(builder) { style ->
        val text = try {
            context.resources.openRawResource(R.raw.nasugbu_barangays)
                .bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }

        if (text.isNotEmpty()) {
            style.addSource(GeoJsonSource("nasugbu-source", text))
            val onlyBarangays = Expression.eq(
                Expression.get("admin_type:PH"), Expression.literal("barangay")
            )

            style.addLayer(CircleLayer("barangay-dots", "nasugbu-source").apply {
                setFilter(onlyBarangays)
                setProperties(
                    PropertyFactory.circleRadius(4f),
                    PropertyFactory.circleColor(AndroidColor.parseColor("#19B38A")),
                    PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                    PropertyFactory.circleStrokeWidth(1.5f)
                )
            })
            style.addLayer(SymbolLayer("barangay-labels", "nasugbu-source").apply {
                setFilter(onlyBarangays)
                setProperties(
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textColor(AndroidColor.WHITE),
                    PropertyFactory.textHaloColor(AndroidColor.parseColor("#0B2B3B")),
                    PropertyFactory.textHaloWidth(1.6f),
                    PropertyFactory.textOffset(arrayOf(0f, 1.1f))
                )
            })
        }
    }
}

private fun makePin(context: Context, colorInt: Int, label: String, sizeDp: Int = 32): Icon {
    val density = context.resources.displayMetrics.density
    val size = (sizeDp * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = AndroidColor.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = colorInt
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, paint)

    paint.color = AndroidColor.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = size * 0.45f
    val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(label, size / 2f, y, paint)

    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

private class RouteGeometry(val cum: DoubleArray, val stopAt: DoubleArray)

private class RiderSim(val plan: RiderPlan, val geo: RouteGeometry) {
    var progress = 0.0
    var waitLeft = 0.0
    var arrivedStopId: String? = null
}

private fun metersBetween(a: LatLng, b: LatLng): Double =
    RouteSequencer.calculateDistance(a, b) * 1000.0

private fun buildGeometry(plan: RiderPlan): RouteGeometry {
    val pts = plan.routePoints
    val cum = DoubleArray(pts.size)
    for (i in 1 until pts.size) cum[i] = cum[i - 1] + metersBetween(pts[i - 1], pts[i])
    val total = cum.last()

    val legTotal = plan.legMeters.sum()
    val stopAt = DoubleArray(plan.stops.size)
    var acc = 0.0
    for (i in plan.stops.indices) {
        acc += plan.legMeters.getOrElse(i) { 0.0 }
        stopAt[i] = if (legTotal > 0) acc / legTotal * total
        else total * (i + 1) / (plan.stops.size + 1)
    }
    return RouteGeometry(cum, stopAt)
}

private fun pointAt(pts: List<LatLng>, cum: DoubleArray, d: Double): LatLng {
    if (d <= 0.0) return pts.first()
    if (d >= cum.last()) return pts.last()
    var lo = 0
    var hi = cum.size - 1
    while (hi - lo > 1) {
        val mid = (lo + hi) / 2
        if (cum[mid] <= d) lo = mid else hi = mid
    }
    val seg = cum[hi] - cum[lo]
    val t = if (seg > 0) (d - cum[lo]) / seg else 0.0
    return LatLng(
        pts[lo].latitude + (pts[hi].latitude - pts[lo].latitude) * t,
        pts[lo].longitude + (pts[hi].longitude - pts[lo].longitude) * t
    )
}

private fun ridersJson(items: List<Triple<Int, String, LatLng>>): String {
    val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
    items.forEachIndexed { i, (id, color, pos) ->
        if (i > 0) sb.append(",")
        sb.append("{\"type\":\"Feature\",\"properties\":{\"color\":\"$color\",\"label\":\"R$id\"},")
        sb.append("\"geometry\":{\"type\":\"Point\",\"coordinates\":[${pos.longitude},${pos.latitude}]}}")
    }
    sb.append("]}")
    return sb.toString()
}

private fun ensureRiderLayers(style: Style) {
    if (style.getSource("riders-source") != null) return
    style.addSource(GeoJsonSource("riders-source", "{\"type\":\"FeatureCollection\",\"features\":[]}"))
    style.addLayer(CircleLayer("riders-circle", "riders-source").apply {
        setProperties(
            PropertyFactory.circleRadius(13f),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
            PropertyFactory.circleStrokeWidth(3f)
        )
    })
    style.addLayer(SymbolLayer("riders-label", "riders-source").apply {
        setProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(11f),
            PropertyFactory.textColor(AndroidColor.WHITE),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true)
        )
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryRouteApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val barangays = remember { BarangayLoader.load(context) }
    val orders = remember { mutableStateListOf<DeliveryOrder>() }
    var plans by remember { mutableStateOf<List<RiderPlan>>(emptyList()) }

    var riderCount by remember { mutableStateOf(2) }
    var serviceMinutes by remember { mutableStateOf(3) }

    var customerName by remember { mutableStateOf("") }
    var addressDetails by remember { mutableStateOf("") }
    var codText by remember { mutableStateOf("") }
    var selectedBarangay by remember { mutableStateOf<BarangayPoint?>(null) }
    var orderCounter by remember { mutableStateOf(1) }

    var deliveredCount by remember { mutableStateOf(0) }
    var collectedCod by remember { mutableStateOf(0.0) }

    var openSection by remember { mutableStateOf(SECTION_NONE) }
    var isPlanning by remember { mutableStateOf(false) }
    var useSatellite by remember { mutableStateOf(true) }
    var focusRider by remember { mutableStateOf(0) }
    var simRunning by remember { mutableStateOf(false) }
    var simSpeed by remember { mutableStateOf(30) }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                val nasugbuBounds = LatLngBounds.Builder()
                    .include(LatLng(14.0000, 120.5300))
                    .include(LatLng(14.1900, 120.7300))
                    .build()
                map.setLatLngBoundsForCameraTarget(nasugbuBounds)
                map.setMinZoomPreference(10.5)
                map.setMaxZoomPreference(18.0)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(HUB.latLng, 12.5))
                map.uiSettings.isRotateGesturesEnabled = false
                mapInstance = map
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapInstance, useSatellite) {
        val map = mapInstance ?: return@LaunchedEffect
        applyMapStyle(context, map, useSatellite)
    }

    LaunchedEffect(mapInstance, orders.toList(), plans, selectedBarangay, focusRider, useSatellite) {
        val map = mapInstance ?: return@LaunchedEffect
        map.clear()

        map.addMarker(
            MarkerOptions()
                .position(HUB.latLng)
                .icon(makePin(context, AndroidColor.parseColor("#0B2B3B"), "H", 40))
                .title("Hub - ${HUB.name}")
                .snippet("All riders start and end here")
        )

        if (plans.isEmpty()) {
            orders.forEachIndexed { i, o ->
                map.addMarker(
                    MarkerOptions()
                        .position(LatLng(o.latitude, o.longitude))
                        .icon(makePin(context, AndroidColor.parseColor("#0E7C7B"), "${i + 1}"))
                        .title(o.customerName)
                        .snippet("${o.barangay} - ${o.addressDetails}")
                )
            }
        } else {
            plans.filter { focusRider == 0 || it.riderId == focusRider }.forEach { plan ->
                val color = AndroidColor.parseColor(plan.colorHex)
                if (plan.routePoints.isNotEmpty()) {
                    map.addPolyline(
                        PolylineOptions().addAll(plan.routePoints).color(AndroidColor.WHITE).width(9f)
                    )
                    map.addPolyline(
                        PolylineOptions().addAll(plan.routePoints).color(color).width(5f)
                    )
                }
                plan.stops.forEach { s ->
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(s.latitude, s.longitude))
                            .icon(makePin(context, color, "${s.sequenceNumber}"))
                            .title("${plan.name} - Stop ${s.sequenceNumber}: ${s.customerName}")
                            .snippet("ETA ${DeliveryPlanner.clockTime(plan.departureMillis, s.etaMinutes)}")
                    )
                }
            }
        }

        selectedBarangay?.let {
            map.addMarker(
                MarkerOptions()
                    .position(it.latLng)
                    .icon(makePin(context, AndroidColor.parseColor("#FFB020"), "+"))
                    .title("New customer: ${it.name}")
            )
        }
    }

    LaunchedEffect(mapInstance, plans, focusRider) {
        val map = mapInstance ?: return@LaunchedEffect
        if (simRunning) return@LaunchedEffect
        val pts = plans.filter { focusRider == 0 || it.riderId == focusRider }
            .flatMap { it.routePoints }
        if (pts.size < 2) return@LaunchedEffect
        try {
            val b = LatLngBounds.Builder()
            pts.forEach { b.include(it) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 110))
        } catch (e: Exception) {  }
    }

    fun addOrder(name: String, details: String, b: BarangayPoint, cod: Double) {
        val n = orderCounter++

        val angle = Math.toRadians(n * 137.5)
        val r = 0.0004 + (n % 3) * 0.0003
        orders.add(
            DeliveryOrder(
                id = "ORD-%03d".format(n),
                customerName = name,
                barangay = b.name,
                addressDetails = details,
                latitude = b.latLng.latitude + r * Math.sin(angle),
                longitude = b.latLng.longitude + r * Math.cos(angle),
                cashOnDelivery = cod
            )
        )
        plans = emptyList()
        focusRider = 0
    }

    fun addCustomerFromForm() {
        val b = selectedBarangay ?: return
        addOrder(
            customerName.trim(),
            addressDetails.trim().ifBlank { "No details" },
            b,
            codText.toDoubleOrNull() ?: 0.0
        )
        customerName = ""
        addressDetails = ""
        codText = ""
        selectedBarangay = null
    }

    fun addSamples() {
        if (barangays.isEmpty()) {
            Toast.makeText(context, "Barangay list is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        val names = listOf(
            "Maria Santos", "Juan Dela Cruz", "Ana Reyes", "Pedro Garcia",
            "Liza Mendoza", "Carlo Ramos", "Joy Bautista", "Mark Villanueva"
        )
        barangays.shuffled().take(8).forEachIndexed { i, b ->
            addOrder(names[i % names.size], "Near the barangay hall", b, (3..24).random() * 50.0)
        }
    }

    fun planRoutes() {
        if (orders.isEmpty()) {
            Toast.makeText(context, "Add at least one customer first.", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            simRunning = false
            isPlanning = true
            val result = DeliveryPlanner.plan(HUB, riderCount, serviceMinutes, orders.toList())
            result.flatMap { it.stops }.forEach { planned ->
                val i = orders.indexOfFirst { it.id == planned.id }
                if (i >= 0) orders[i] = planned
            }
            plans = result
            focusRider = 0
            isPlanning = false
            openSection = SECTION_SUMMARY
            if (result.any { it.isEstimated }) {
                Toast.makeText(
                    context,
                    "Road service unreachable for some riders - times are estimates.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun markDelivered(id: String) {
        val order = orders.find { it.id == id } ?: return
        val plan = plans.find { p -> p.stops.any { it.id == id } } ?: return

        orders.remove(order)
        deliveredCount++
        collectedCod += order.cashOnDelivery

        val remaining = plan.stops.filter { s -> s.id != id && orders.any { it.id == s.id } }

        scope.launch {
            isPlanning = true
            val updated = DeliveryPlanner.replanRider(
                HUB, plan, LatLng(order.latitude, order.longitude), remaining, serviceMinutes
            )
            updated.stops.forEach { s ->
                val i = orders.indexOfFirst { it.id == s.id }
                if (i >= 0) orders[i] = s
            }
            plans = plans.map { if (it.riderId == updated.riderId) updated else it }
            if (updated.stops.isEmpty() && focusRider == updated.riderId) focusRider = 0
            isPlanning = false
        }
    }

    LaunchedEffect(simRunning, mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect

        fun showRiders(items: List<Triple<Int, String, LatLng>>) {
            val style = map.style
            if (style == null || !style.isFullyLoaded) return
            ensureRiderLayers(style)
            style.getSourceAs<GeoJsonSource>("riders-source")?.setGeoJson(ridersJson(items))
        }

        if (!simRunning) {
            showRiders(emptyList())
            return@LaunchedEffect
        }

        val sims = mutableMapOf<Int, RiderSim>()
        val shown = mutableMapOf<Int, Triple<Int, String, LatLng>>()
        var last = System.nanoTime()

        while (true) {
            delay(100)
            val now = System.nanoTime()
            val dtSim = ((now - last) / 1e9).coerceAtMost(0.5) * simSpeed
            last = now

            if (isPlanning) continue

            val current = plans
            if (current.isEmpty()) {
                simRunning = false
                break
            }

            for (plan in current) {
                if (plan.stops.isEmpty() || plan.routePoints.size < 2) continue

                var sim = sims[plan.riderId]
                if (sim == null || sim.plan !== plan) {
                    sim = RiderSim(plan, buildGeometry(plan))
                    sims[plan.riderId] = sim
                }

                if (sim.waitLeft > 0) {
                    sim.waitLeft -= dtSim
                    if (sim.waitLeft <= 0) {
                        sim.waitLeft = 0.0
                        sim.arrivedStopId?.let { markDelivered(it) }
                    }
                } else if (sim.arrivedStopId == null) {
                    val speed = (plan.distanceKm * 1000.0 / (plan.driveMinutes * 60.0).coerceAtLeast(1.0))
                        .coerceIn(3.0, 25.0)
                    sim.progress += speed * dtSim
                    val target = sim.geo.stopAt[0]
                    if (sim.progress >= target) {
                        sim.progress = target
                        sim.waitLeft = serviceMinutes * 60.0
                        sim.arrivedStopId = plan.stops.first().id
                    }
                }

                shown[plan.riderId] = Triple(
                    plan.riderId,
                    plan.colorHex,
                    pointAt(plan.routePoints, sim.geo.cum, sim.progress)
                )
            }
            showRiders(shown.values.toList())
        }
    }

    fun toggleSection(section: Int) {
        openSection = if (openSection == section) SECTION_NONE else section
    }

    val anyOpen = openSection != SECTION_NONE

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        Header(orderCount = orders.size, riderCount = riderCount)

        Card(
            modifier = Modifier
                .weight(if (anyOpen) 0.40f else 1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

                FilledTonalButton(
                    onClick = { useSatellite = !useSatellite },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text(if (useSatellite) "Street map" else "Satellite", fontSize = 12.sp)
                }

                val activePlans = plans.filter { it.stops.isNotEmpty() }
                if (activePlans.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = focusRider == 0,
                            onClick = { focusRider = 0 },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(containerColor = ComposeColor.White)
                        )
                        activePlans.forEach { p ->
                            FilterChip(
                                selected = focusRider == p.riderId,
                                onClick = { focusRider = p.riderId },
                                label = { Text("R${p.riderId}") },
                                colors = FilterChipDefaults.filterChipColors(containerColor = ComposeColor.White)
                            )
                        }
                    }
                }

                if (isPlanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Mint
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .then(if (anyOpen) Modifier.weight(0.60f) else Modifier)
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            shadowElevation = 10.dp,
            color = ComposeColor.White
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "button-orders") {
                    SectionButton(
                        title = "📦  Orders",
                        subtitle = "Add customers  |  ${orders.size} pending",
                        expanded = openSection == SECTION_ORDERS,
                        onClick = { toggleSection(SECTION_ORDERS) }
                    )
                }
                if (openSection == SECTION_ORDERS) {
                    ordersSection(
                        orders = orders,
                        barangays = barangays,
                        customerName = customerName,
                        onNameChange = { customerName = it },
                        addressDetails = addressDetails,
                        onAddressChange = { addressDetails = it },
                        codText = codText,
                        onCodChange = { codText = it.filter { c -> c.isDigit() || c == '.' } },
                        selectedBarangay = selectedBarangay,
                        onBarangayPicked = { selectedBarangay = it },
                        onAdd = { addCustomerFromForm() },
                        onDelete = { o ->
                            orders.remove(o)
                            plans = emptyList()
                        },
                        onSamples = { addSamples() },
                        onClear = {
                            orders.clear()
                            plans = emptyList()
                            selectedBarangay = null
                            deliveredCount = 0
                            collectedCod = 0.0
                        }
                    )
                }

                item(key = "button-setup") {
                    SectionButton(
                        title = "⚙️  Setup",
                        subtitle = "Hub  |  $riderCount riders  |  $serviceMinutes min per stop",
                        expanded = openSection == SECTION_SETUP,
                        onClick = { toggleSection(SECTION_SETUP) }
                    )
                }
                if (openSection == SECTION_SETUP) {
                    setupSection(
                        riderCount = riderCount,
                        onRiderCountChange = { riderCount = it; plans = emptyList() },
                        serviceMinutes = serviceMinutes,
                        onServiceMinutesChange = { serviceMinutes = it; plans = emptyList() }
                    )
                }

                item(key = "button-summary") {
                    SectionButton(
                        title = "📋  Summary",
                        subtitle = if (plans.isEmpty()) "Plan routes and track deliveries"
                        else "${plans.sumOf { it.stops.size }} stops left  |  $deliveredCount delivered",
                        expanded = openSection == SECTION_SUMMARY,
                        onClick = { toggleSection(SECTION_SUMMARY) }
                    )
                }
                if (openSection == SECTION_SUMMARY) {
                    summarySection(
                        plans = plans,
                        deliveredCount = deliveredCount,
                        collectedCod = collectedCod,
                        isPlanning = isPlanning,
                        onPlan = { planRoutes() },
                        simRunning = simRunning,
                        simSpeed = simSpeed,
                        onToggleSim = { simRunning = !simRunning },
                        onSpeedChange = { simSpeed = it },
                        onDelivered = { markDelivered(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun Header(orderCount: Int, riderCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.linearGradient(listOf(Navy, Teal)))
            .drawBehind {
                drawCircle(
                    color = ComposeColor.White.copy(alpha = 0.07f),
                    radius = size.width * 0.35f,
                    center = Offset(size.width * 0.95f, 0f)
                )
                drawCircle(
                    color = ComposeColor.White.copy(alpha = 0.05f),
                    radius = size.width * 0.25f,
                    center = Offset(size.width * 0.08f, size.height)
                )
            }
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                "CHRISVEN ROUTING",
                color = Mint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Delivery Route Management System",
                color = ComposeColor.White,
                fontSize = 21.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HeaderPill("📍 Hub: Barangay 2")
                HeaderPill("$orderCount orders")
                HeaderPill("$riderCount riders")
            }
        }
    }
}

@Composable
fun HeaderPill(text: String) {
    Surface(color = ComposeColor.White.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = ComposeColor.White,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionButton(title: String, subtitle: String, expanded: Boolean, onClick: () -> Unit) {
    val titleColor = if (expanded) ComposeColor.White else Ink
    val subtitleColor = if (expanded) ComposeColor.White.copy(alpha = 0.8f) else Muted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (expanded) Teal else ScreenBg)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = titleColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = subtitleColor, fontSize = 12.sp)
        }
        Text(if (expanded) "▲" else "▼", color = titleColor, fontSize = 14.sp)
    }
}

@Composable
fun StatTile(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = ComposeColor.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = ComposeColor.White.copy(alpha = 0.7f), fontSize = 11.sp)
    }
}

fun LazyListScope.ordersSection(
    orders: List<DeliveryOrder>,
    barangays: List<BarangayPoint>,
    customerName: String,
    onNameChange: (String) -> Unit,
    addressDetails: String,
    onAddressChange: (String) -> Unit,
    codText: String,
    onCodChange: (String) -> Unit,
    selectedBarangay: BarangayPoint?,
    onBarangayPicked: (BarangayPoint) -> Unit,
    onAdd: () -> Unit,
    onDelete: (DeliveryOrder) -> Unit,
    onSamples: () -> Unit,
    onClear: () -> Unit
) {
    item { Text("New delivery", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink) }
    item {
        OutlinedTextField(
            value = customerName,
            onValueChange = onNameChange,
            label = { Text("Customer name") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        BarangayDropdown(
            label = "Customer barangay",
            options = barangays,
            selected = selectedBarangay?.name ?: "",
            onSelected = onBarangayPicked,
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        OutlinedTextField(
            value = addressDetails,
            onValueChange = onAddressChange,
            label = { Text("Address details (street, landmark)") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        OutlinedTextField(
            value = codText,
            onValueChange = onCodChange,
            label = { Text("Cash on delivery (optional, PHP)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
    item {
        Button(
            onClick = onAdd,
            enabled = selectedBarangay != null && customerName.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) { Text("Add customer", fontWeight = FontWeight.SemiBold) }
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pending orders (${orders.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
            Row {
                TextButton(onClick = onSamples) { Text("Add 8 samples") }
                TextButton(onClick = onClear, enabled = orders.isNotEmpty()) { Text("Clear all") }
            }
        }
    }

    if (orders.isEmpty()) {
        item {
            Text(
                "No pending orders. Add a customer above, or tap \"Add 8 samples\" to try the app quickly.",
                fontSize = 13.sp, color = Muted
            )
        }
    }

    items(orders, key = { it.id }) { o ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ScreenBg)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Teal.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        o.customerName.take(1).uppercase(),
                        color = Teal, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(o.customerName, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text("${o.id} | ${o.barangay} | ${o.addressDetails}", fontSize = 12.sp, color = Muted)
                    if (o.cashOnDelivery > 0) {
                        Text(
                            "COD P%,.0f".format(o.cashOnDelivery),
                            fontSize = 12.sp, color = Teal, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                TextButton(onClick = { onDelete(o) }) { Text("Remove") }
            }
        }
    }
}

fun LazyListScope.setupSection(
    riderCount: Int,
    onRiderCountChange: (Int) -> Unit,
    serviceMinutes: Int,
    onServiceMinutesChange: (Int) -> Unit
) {
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ScreenBg)
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Navy),
                    contentAlignment = Alignment.Center
                ) { Text("H", color = ComposeColor.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Delivery hub", fontWeight = FontWeight.Bold, color = Ink)
                    Text("Barangay 2 Hall, Nasugbu", color = Ink)
                    Text("Fixed location. Every rider starts and ends here.", fontSize = 12.sp, color = Muted)
                }
            }
        }
    }
    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ScreenBg)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Stepper("Number of riders", riderCount, 1..5, onRiderCountChange)
                Stepper("Minutes per delivery stop", serviceMinutes, 1..15, onServiceMinutesChange)
            }
        }
    }
    item {
        Text(
            "Orders are split between riders by area. Each rider always goes to the nearest remaining stop first, using the shortest road to get there.",
            fontSize = 12.sp, color = Muted
        )
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { if (value > range.first) onChange(value - 1) }) { Text("-") }
            Text("$value", modifier = Modifier.padding(horizontal = 14.dp), fontWeight = FontWeight.Bold, color = Ink)
            OutlinedButton(onClick = { if (value < range.last) onChange(value + 1) }) { Text("+") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
fun LazyListScope.summarySection(
    plans: List<RiderPlan>,
    deliveredCount: Int,
    collectedCod: Double,
    isPlanning: Boolean,
    onPlan: () -> Unit,
    simRunning: Boolean,
    simSpeed: Int,
    onToggleSim: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onDelivered: (String) -> Unit
) {
    item {
        Button(
            onClick = onPlan,
            enabled = !isPlanning,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text(if (isPlanning) "Working..." else "Assign riders & plan routes", fontWeight = FontWeight.SemiBold)
        }
    }

    if (plans.isEmpty()) {
        item {
            Text("No plan yet. Add customers, then press the button above.", fontSize = 13.sp, color = Muted)
        }
        return
    }

    item {
        val remaining = plans.sumOf { it.stops.size }
        val totalKm = plans.sumOf { it.distanceKm }
        val totalCod = plans.sumOf { it.codTotal }
        val finish = plans.maxOfOrNull { it.totalMinutes } ?: 0.0
        val all = deliveredCount + remaining
        val fraction = if (all == 0) 0f else (deliveredCount.toFloat() / all).coerceIn(0f, 1f)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Navy)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Delivery summary", color = ComposeColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatTile("%.1f km".format(totalKm), "Distance left")
                    StatTile(DeliveryPlanner.formatDuration(finish), "Time left")
                    StatTile("P%,.0f".format(totalCod), "To collect")
                }
                Text(
                    "Delivered $deliveredCount   |   Remaining $remaining   |   Collected P%,.0f".format(collectedCod),
                    color = ComposeColor.White.copy(alpha = 0.9f), fontSize = 12.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ComposeColor.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(Mint)
                    )
                }
            }
        }
    }

    item {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ScreenBg)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Live rider simulation", fontWeight = FontWeight.Bold, color = Ink)
                Text(
                    "Riders drive along their routes and deliver each stop automatically.",
                    fontSize = 12.sp, color = Muted
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Speed", fontSize = 12.sp, color = Muted)
                    listOf(1, 10, 30, 60).forEach { s ->
                        FilterChip(
                            selected = simSpeed == s,
                            onClick = { onSpeedChange(s) },
                            label = { Text("${s}x") }
                        )
                    }
                }
                Button(
                    onClick = onToggleSim,
                    enabled = !isPlanning || simRunning,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (simRunning) "■  Stop riders" else "▶  Start riders", fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    items(plans, key = { "rider-${it.riderId}" }) { plan ->
        val color = ComposeColor(AndroidColor.parseColor(plan.colorHex))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = ScreenBg)
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .background(color)
                )
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(plan.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                        Text("${plan.stops.size} stops left", fontSize = 12.sp, color = Muted)
                    }

                    if (plan.stops.isEmpty()) {
                        Text("No pending deliveries.", fontSize = 13.sp, color = Muted)
                    } else {
                        Text(
                            "${"%.1f".format(plan.distanceKm)} km | drive ${DeliveryPlanner.formatDuration(plan.driveMinutes)} | total ${DeliveryPlanner.formatDuration(plan.totalMinutes)}",
                            fontSize = 12.sp, color = Muted
                        )
                        if (plan.codTotal > 0) {
                            Text(
                                "Cash to collect: P%,.0f".format(plan.codTotal),
                                fontSize = 12.sp, color = Teal, fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (plan.isEstimated) {
                            Text("Estimated (road service unavailable)", fontSize = 11.sp, color = ComposeColor(0xFFB71C1C))
                        }
                        plan.stops.forEach { stop ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${stop.sequenceNumber}",
                                        color = ComposeColor.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stop.customerName, fontWeight = FontWeight.SemiBold, color = Ink)
                                    Text(
                                        "${stop.barangay} | ETA ${DeliveryPlanner.clockTime(plan.departureMillis, stop.etaMinutes)} (+${DeliveryPlanner.formatDuration(stop.etaMinutes)})",
                                        fontSize = 12.sp, color = Muted
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { onDelivered(stop.id) },
                                    enabled = !isPlanning && !simRunning,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                                ) { Text("Delivered", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarangayDropdown(
    label: String,
    options: List<BarangayPoint>,
    selected: String,
    onSelected: (BarangayPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}