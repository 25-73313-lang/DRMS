package com.example.deliveryroutesystem

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import kotlin.math.roundToInt

/** The delivery hub: Barangay 2 Hall, Nasugbu. The rider starts and ends here. */
private val HUB = Hub("Barangay 2 Hall", LatLng(14.07575, 120.63223))

private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        setContent {
            MaterialTheme {
                DeliveryApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryApp() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // ---------- Data ----------
    val barangays = remember { BarangayLoader.load(context) }
    val orders = remember { mutableStateListOf<DeliveryOrder>() }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var summary by remember { mutableStateOf("") }
    var orderCounter by remember { mutableStateOf(1) }

    // ---------- Form ----------
    var customerName by remember { mutableStateOf("") }
    var selectedBarangay by remember { mutableStateOf<BarangayPoint?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    // ---------- Map ----------
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.setStyle(MAP_STYLE_URL)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(HUB.latLng, 12.5))
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

    // Redraw the hub, the customers and the route whenever something changes
    LaunchedEffect(mapInstance, orders.toList(), routePoints) {
        val map = mapInstance ?: return@LaunchedEffect
        map.clear()

        map.addMarker(
            MarkerOptions().position(HUB.latLng).title("Hub: ${HUB.name}")
        )

        orders.forEach { o ->
            val label = if (o.sequenceNumber > 0) "Stop ${o.sequenceNumber}: ${o.customerName}" else o.customerName
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(o.latitude, o.longitude))
                    .title(label)
                    .snippet(o.barangay)
            )
        }

        if (routePoints.size >= 2) {
            map.addPolyline(
                PolylineOptions()
                    .addAll(routePoints)
                    .color(AndroidColor.parseColor("#0288D1"))
                    .width(5f)
            )
            val bounds = LatLngBounds.Builder()
            routePoints.forEach { bounds.include(it) }
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
        }
    }

    // ---------- Actions ----------
    fun addCustomer() {
        val barangay = selectedBarangay ?: return
        val n = orderCounter++

        // small offset so customers in the same barangay don't sit on the same spot
        val angle = Math.toRadians(n * 137.5)
        val offset = 0.0005

        orders.add(
            DeliveryOrder(
                id = "ORD-%03d".format(n),
                customerName = customerName.trim(),
                barangay = barangay.name,
                latitude = barangay.latLng.latitude + offset * Math.sin(angle),
                longitude = barangay.latLng.longitude + offset * Math.cos(angle)
            )
        )
        customerName = ""
        selectedBarangay = null
        routePoints = emptyList()
        summary = ""
    }

    fun planRoute() {
        if (orders.isEmpty()) {
            Toast.makeText(context, "Add at least one customer first.", Toast.LENGTH_SHORT).show()
            return
        }

        scope.launch {
            isLoading = true

            // 1. choose the order of stops (nearest first)
            val sequenced = RouteSequencer.sequenceRoute(HUB.latLng, orders.toList())

            // 2. ask OSRM for the real road: hub -> stops -> hub
            val waypoints = listOf(HUB.latLng) +
                    sequenced.map { LatLng(it.latitude, it.longitude) } +
                    listOf(HUB.latLng)
            val route = RouteFetcher.fetchRoute(waypoints)

            if (route == null) {
                Toast.makeText(context, "Could not get the route. Check your internet.", Toast.LENGTH_LONG).show()
            } else {
                sequenced.forEach { s ->
                    val i = orders.indexOfFirst { it.id == s.id }
                    if (i >= 0) orders[i] = s
                }
                routePoints = route.points
                val km = route.distanceMeters / 1000.0
                val minutes = (route.durationSeconds / 60.0).roundToInt()
                summary = "Total: %.1f km, about %d min of driving".format(km, minutes)
            }
            isLoading = false
        }
    }

    // ---------- Screen ----------
    Column(modifier = Modifier.fillMaxSize()) {

        Surface(color = MaterialTheme.colorScheme.primary) {
            Text(
                text = "Delivery Route Management System",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            )
        }

        Box(modifier = Modifier.weight(0.45f)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(0.55f)
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Add a customer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            item {
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Customer name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                BarangayDropdown(
                    options = barangays,
                    selected = selectedBarangay?.name ?: "",
                    onSelected = { selectedBarangay = it }
                )
            }
            item {
                Button(
                    onClick = { addCustomer() },
                    enabled = customerName.isNotBlank() && selectedBarangay != null,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add customer") }
            }

            item {
                Text("Customers (${orders.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            items(orders, key = { it.id }) { o ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val stop = if (o.sequenceNumber > 0) "Stop ${o.sequenceNumber} - " else ""
                            Text(stop + o.customerName, fontWeight = FontWeight.SemiBold)
                            Text("${o.id} | ${o.barangay}", fontSize = 12.sp)
                        }
                        TextButton(onClick = {
                            orders.remove(o)
                            routePoints = emptyList()
                            summary = ""
                        }) { Text("Remove") }
                    }
                }
            }

            item {
                Button(
                    onClick = { planRoute() },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isLoading) "Working..." else "Plan route") }
            }
            if (summary.isNotEmpty()) {
                item {
                    Text(summary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarangayDropdown(
    options: List<BarangayPoint>,
    selected: String,
    onSelected: (BarangayPoint) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Barangay") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
