package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BrandPrimaryLime
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.MutedFill
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted

data class DrinkItem(
    val name: String,
    val abv: Double,
    val volumeMl: Int,
    val category: String,
    val isFavorite: Boolean = false
) {
    val units: Double get() = (abv * volumeMl) / 1000.0
    val estKcal: Int get() = (units * 56).toInt()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrinkPickerModal(
    onDismiss: () -> Unit,
    onDrinkLogged: (DrinkItem, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var daySelection by remember { mutableStateOf("Today") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Favorites") }
    var selectedStomachState by remember { mutableStateOf("Meal") }

    // Custom drink inputs
    var customAbv by remember { mutableStateOf("5.0") }
    var customVolumeMl by remember { mutableStateOf("568") }

    val sampleDrinks = remember {
        listOf(
            DrinkItem("Pint of Stella / Lager", 5.0, 568, "Beer", true),
            DrinkItem("Glass of Pinot Noir", 13.0, 175, "Wine", true),
            DrinkItem("Gin & Tonic (Single)", 40.0, 25, "Spirits", true),
            DrinkItem("Guinness Pint", 4.2, 568, "Beer", false),
            DrinkItem("Large Sauvignon Blanc", 12.5, 250, "Wine", false),
            DrinkItem("Double Vodka Soda", 40.0, 50, "Spirits", false)
        )
    }

    var selectedDrink by remember { mutableStateOf<DrinkItem?>(sampleDrinks.first()) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        containerColor = AppCardSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Log a Drink",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
                TextButton(onClick = onDismiss) {
                    Text("Close", color = TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Quick Day Selector (Today, Yesterday, Pick a day)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val days = listOf("Today", "Yesterday", "Pick a day")
                    days.forEach { day ->
                        val isSelected = day == daySelection
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) BrandPrimaryLime else SecondaryFill)
                                .clickable { daySelection = day }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else TextForeground
                            )
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search UK drinks catalog...", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandPrimaryLime,
                        unfocusedBorderColor = BorderDefault,
                        focusedContainerColor = MutedFill,
                        unfocusedContainerColor = MutedFill
                    )
                )

                // Category Filter Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val categories = listOf("Favorites", "Beer", "Wine", "Spirits", "Custom")
                    categories.forEach { cat ->
                        val isSel = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSel) BrandPrimaryLime.copy(alpha = 0.2f) else MutedFill)
                                .border(1.dp, if (isSel) BrandPrimaryLime else BorderDefault, RoundedCornerShape(16.dp))
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                color = if (isSel) BrandPrimaryLime else TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (selectedCategory == "Custom") {
                    // Custom Drink Inputs & Live Units Calculator
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MutedFill)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Custom Drink & Units Calculator", style = MaterialTheme.typography.labelMedium, color = BrandPrimaryLime, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customAbv,
                                    onValueChange = { customAbv = it },
                                    label = { Text("ABV %", color = TextMuted) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BrandPrimaryLime,
                                        unfocusedBorderColor = BorderDefault,
                                        focusedContainerColor = AppCardSurface,
                                        unfocusedContainerColor = AppCardSurface
                                    )
                                )
                                OutlinedTextField(
                                    value = customVolumeMl,
                                    onValueChange = { customVolumeMl = it },
                                    label = { Text("Volume (ml)", color = TextMuted) },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BrandPrimaryLime,
                                        unfocusedBorderColor = BorderDefault,
                                        focusedContainerColor = AppCardSurface,
                                        unfocusedContainerColor = AppCardSurface
                                    )
                                )
                            }
                            val abvVal = customAbv.toDoubleOrNull() ?: 5.0
                            val volVal = customVolumeMl.toIntOrNull() ?: 568
                            val calcUnits = (abvVal * volVal) / 1000.0
                            val calcKcal = (calcUnits * 56).toInt()
                            Text("Calculated: %.1f Units · ≈ %d kcal".format(calcUnits, calcKcal), color = JudgementGood, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                } else {
                    // Drink Catalog Item Picker List
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val filtered = sampleDrinks.filter {
                            (selectedCategory == "Favorites" && it.isFavorite) ||
                                    (selectedCategory != "Favorites" && it.category == selectedCategory)
                        }
                        filtered.forEach { drink ->
                            val isSel = drink == selectedDrink
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDrink = drink },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = if (isSel) BrandPrimaryLime.copy(alpha = 0.15f) else MutedFill),
                                border = BorderStroke(1.dp, if (isSel) BrandPrimaryLime else BorderDefault)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = drink.name, color = TextForeground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(text = "${drink.abv}% ABV · ${drink.volumeMl}ml", color = TextMuted, fontSize = 11.sp)
                                    }
                                    Text(text = "%.1f Units".format(drink.units), color = BrandPrimaryLime, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Session Tags / Stomach State for BAC Estimation
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Stomach State (BAC Metabolic Estimation)", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Empty stomach", "Snack", "Meal").forEach { state ->
                            val isSel = state == selectedStomachState
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) BrandPrimaryLime else MutedFill)
                                    .clickable { selectedStomachState = state }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = state,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSel) Color.Black else TextMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedDrink?.let { onDrinkLogged(it, daySelection) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimaryLime, contentColor = Color.Black)
            ) {
                Text("Log Drink", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DrinkPickerModalPreview() {
    ScatterFitTheme {
        DrinkPickerModal(
            onDismiss = {},
            onDrinkLogged = { _, _ -> }
        )
    }
}
