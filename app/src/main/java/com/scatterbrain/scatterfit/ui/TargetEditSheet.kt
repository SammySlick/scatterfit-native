package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.data.SettingsStorage
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

enum class TargetEditType {
    CALORIES,
    STEPS,
    SLEEP,
    ALCOHOL,
    EXERCISE
}

fun validateMacroSplit(proteinPct: Int, carbsPct: Int, fatPct: Int): Boolean =
    (proteinPct + carbsPct + fatPct) == 100

fun clampProteinPerLb(value: Double): Double =
    value.coerceIn(1.0, 2.0)

fun deriveProteinGramsFromLb(weightKg: Double, gPerLb: Double): Int {
    val weightLb = weightKg * 2.20462
    return round(weightLb * clampProteinPerLb(gPerLb)).toInt()
}

fun deriveMacroGramsFromSplit(kcal: Int, proteinPct: Int, carbsPct: Int, fatPct: Int): Triple<Int, Int, Int> {
    val pG = round(kcal * (proteinPct / 100.0) / 4.0).toInt().coerceAtLeast(0)
    val cG = round(kcal * (carbsPct / 100.0) / 4.0).toInt().coerceAtLeast(0)
    val fG = round(kcal * (fatPct / 100.0) / 9.0).toInt().coerceAtLeast(0)
    return Triple(pG, cG, fG)
}

data class ExerciseZoneBreakdown(val totalMin: Int, val z2: Int, val z3: Int, val z4: Int, val z5: Int)

fun deriveExerciseZoneMinutes(timesPerWeek: Int, sessionDurationMin: Int = 45): ExerciseZoneBreakdown {
    val totalMin = timesPerWeek.coerceAtLeast(0) * sessionDurationMin
    val z2 = round(totalMin * 0.60).toInt()
    val z3 = round(totalMin * 0.25).toInt()
    val z4 = round(totalMin * 0.10).toInt()
    val z5 = round(totalMin * 0.05).toInt()
    return ExerciseZoneBreakdown(totalMin, z2, z3, z4, z5)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetEditSheet(
    editType: TargetEditType,
    settingsStorage: SettingsStorage,
    currentSettings: ScoringSettings,
    effectiveWeightKg: Double,
    onDismissRequest: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkCardSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (editType) {
                TargetEditType.CALORIES -> {
                    CaloriesEditContent(
                        settingsStorage = settingsStorage,
                        currentSettings = currentSettings,
                        effectiveWeightKg = effectiveWeightKg,
                        onCancel = onDismissRequest,
                        onSave = onSaveSuccess
                    )
                }
                TargetEditType.STEPS -> {
                    StepsEditContent(
                        settingsStorage = settingsStorage,
                        currentSettings = currentSettings,
                        onCancel = onDismissRequest,
                        onSave = onSaveSuccess
                    )
                }
                TargetEditType.SLEEP -> {
                    SleepEditContent(
                        settingsStorage = settingsStorage,
                        currentSettings = currentSettings,
                        onCancel = onDismissRequest,
                        onSave = onSaveSuccess
                    )
                }
                TargetEditType.ALCOHOL -> {
                    AlcoholEditContent(
                        settingsStorage = settingsStorage,
                        currentSettings = currentSettings,
                        onCancel = onDismissRequest,
                        onSave = onSaveSuccess
                    )
                }
                TargetEditType.EXERCISE -> {
                    ExerciseEditContent(
                        settingsStorage = settingsStorage,
                        currentSettings = currentSettings,
                        onCancel = onDismissRequest,
                        onSave = onSaveSuccess
                    )
                }
            }
        }
    }
}

@Composable
private fun CaloriesEditContent(
    settingsStorage: SettingsStorage,
    currentSettings: ScoringSettings,
    effectiveWeightKg: Double,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var isModeA by remember { mutableStateOf(settingsStorage.isCaloriesModeA()) }
    var proteinPerLb by remember { mutableDoubleStateOf(settingsStorage.getProteinPerLb().coerceIn(1.0, 2.0)) }

    val initialSplit = remember { settingsStorage.getMacroSplitPercentages() }
    var kcalInput by remember { mutableStateOf(currentSettings.kcalTarget.toString()) }
    var pPctInput by remember { mutableStateOf(initialSplit.first.toString()) }
    var cPctInput by remember { mutableStateOf(initialSplit.second.toString()) }
    var fPctInput by remember { mutableStateOf(initialSplit.third.toString()) }

    // Header with Mode Switcher Chips
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Calories & Macros", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextForeground)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeChip(
                label = "Protein rule",
                isSelected = isModeA,
                onClick = { isModeA = true }
            )
            ModeChip(
                label = "Macro split",
                isSelected = !isModeA,
                onClick = { isModeA = false }
            )
        }
    }

    if (isModeA) {
        // MODE A: Protein per pound
        val weightLb = effectiveWeightKg * 2.20462
        val derivedProtein = deriveProteinGramsFromLb(effectiveWeightKg, proteinPerLb)
        val remainingKcal = (currentSettings.kcalTarget - derivedProtein * 4).coerceAtLeast(0)
        val derivedCarbs = round(remainingKcal * 0.40 / 4.0).toInt()
        val derivedFat = round(remainingKcal * 0.60 / 9.0).toInt()

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Current weight: ${effectiveWeightKg.roundToInt()} kg (${weightLb.roundToInt()} lb)",
                fontSize = 12.sp,
                color = TextMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Protein ratio", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextForeground)
                Text(
                    text = "${"%.2f".format(Locale.US, proteinPerLb)} g/lb = $derivedProtein g",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentTeal
                )
            }

            Slider(
                value = proteinPerLb.toFloat(),
                onValueChange = { proteinPerLb = it.toDouble().coerceIn(1.0, 2.0) },
                valueRange = 1.0f..2.0f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = AccentTeal,
                    activeTrackColor = AccentTeal,
                    inactiveTrackColor = SecondaryFill
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Target: ${currentSettings.kcalTarget} kcal · Remaining $remainingKcal kcal: $derivedCarbs g carbs · $derivedFat g fat",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        SheetActions(
            onCancel = onCancel,
            onSave = {
                settingsStorage.saveCaloriesMacroTargets(
                    kcal = currentSettings.kcalTarget,
                    protein = derivedProtein,
                    carbs = derivedCarbs,
                    fat = derivedFat,
                    isModeA = true,
                    proteinPerLb = proteinPerLb
                )
                onSave()
            },
            saveEnabled = true
        )
    } else {
        // MODE B: Macro percentage split
        val pPct = pPctInput.toIntOrNull() ?: 0
        val cPct = cPctInput.toIntOrNull() ?: 0
        val fPct = fPctInput.toIntOrNull() ?: 0
        val sum = pPct + cPct + fPct
        val isSumValid = validateMacroSplit(pPct, cPct, fPct)
        val kcalVal = kcalInput.toIntOrNull() ?: 0
        val canSave = isSumValid && kcalVal > 0

        val (derivedP, derivedC, derivedF) = deriveMacroGramsFromSplit(kcalVal, pPct, cPct, fPct)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = kcalInput,
                onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) kcalInput = it },
                label = { Text("Calories target (kcal)", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = pPctInput,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) pPctInput = it },
                    label = { Text("Protein %", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = cPctInput,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) cPctInput = it },
                    label = { Text("Carbs %", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fPctInput,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) fPctInput = it },
                    label = { Text("Fat %", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total split:", fontSize = 12.sp, color = TextMuted)
                Text(
                    text = if (isSumValid) "100% (valid)" else "$sum% (must equal 100%)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSumValid) StateLocked else AccentTeal
                )
            }

            Text(
                text = "Derived: Protein ${derivedP}g · Carbs ${derivedC}g · Fat ${derivedF}g",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }

        SheetActions(
            onCancel = onCancel,
            onSave = {
                settingsStorage.saveCaloriesMacroTargets(
                    kcal = kcalVal,
                    protein = derivedP,
                    carbs = derivedC,
                    fat = derivedF,
                    isModeA = false,
                    pPct = pPct,
                    cPct = cPct,
                    fPct = fPct
                )
                onSave()
            },
            saveEnabled = canSave
        )
    }
}

@Composable
private fun StepsEditContent(
    settingsStorage: SettingsStorage,
    currentSettings: ScoringSettings,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var stepsInput by remember { mutableStateOf(currentSettings.stepTarget.toString()) }

    Text("Steps Target", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextForeground)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = stepsInput,
            onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) stepsInput = it },
            label = { Text("Steps per day", color = TextMuted) },
            placeholder = { Text("10000", color = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Daily target (default: 10,000 steps)", fontSize = 11.sp, color = TextMuted)
    }

    SheetActions(
        onCancel = onCancel,
        onSave = {
            val s = stepsInput.toIntOrNull() ?: 10000
            settingsStorage.saveStepTarget(s)
            onSave()
        },
        saveEnabled = (stepsInput.toIntOrNull() ?: 0) > 0
    )
}

@Composable
private fun SleepEditContent(
    settingsStorage: SettingsStorage,
    currentSettings: ScoringSettings,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var sleepInput by remember {
        mutableStateOf(
            if (currentSettings.sleepTargetHours % 1.0 == 0.0) currentSettings.sleepTargetHours.toInt().toString()
            else currentSettings.sleepTargetHours.toString()
        )
    }

    Text("Sleep Target", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextForeground)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = sleepInput,
            onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d*$"""))) sleepInput = it },
            label = { Text("Hours per night", color = TextMuted) },
            placeholder = { Text("8.0", color = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Nightly target (default: 8.0 hours)", fontSize = 11.sp, color = TextMuted)
    }

    SheetActions(
        onCancel = onCancel,
        onSave = {
            val h = sleepInput.toDoubleOrNull() ?: 8.0
            settingsStorage.saveSleepTarget(h)
            onSave()
        },
        saveEnabled = (sleepInput.toDoubleOrNull() ?: 0.0) > 0.0
    )
}

@Composable
private fun AlcoholEditContent(
    settingsStorage: SettingsStorage,
    currentSettings: ScoringSettings,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var unitsInput by remember { mutableStateOf(currentSettings.unitsPerWeek.toString()) }

    Text("Alcohol Target", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextForeground)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = unitsInput,
            onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) unitsInput = it },
            label = { Text("Units per week", color = TextMuted) },
            placeholder = { Text("14", color = TextMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Text("Weekly moderation cap (default: 14 units)", fontSize = 11.sp, color = TextMuted)
    }

    SheetActions(
        onCancel = onCancel,
        onSave = {
            val u = unitsInput.toIntOrNull() ?: 14
            settingsStorage.saveAlcoholTarget(u)
            onSave()
        },
        saveEnabled = unitsInput.toIntOrNull() != null
    )
}

@Composable
private fun ExerciseEditContent(
    settingsStorage: SettingsStorage,
    currentSettings: ScoringSettings,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    var isSimple by remember { mutableStateOf(settingsStorage.isExerciseModeSimple()) }
    var timesInput by remember { mutableStateOf(settingsStorage.getExerciseTimesPerWeek().toString()) }

    var z2Input by remember { mutableStateOf(currentSettings.z2Target.toString()) }
    var z3Input by remember { mutableStateOf(currentSettings.z3Target.toString()) }
    var z4Input by remember { mutableStateOf(currentSettings.z4Target.toString()) }
    var z5Input by remember { mutableStateOf(currentSettings.z5Target.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Exercise Target", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextForeground)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeChip(
                label = "Simple",
                isSelected = isSimple,
                onClick = { isSimple = true }
            )
            ModeChip(
                label = "Advanced",
                isSelected = !isSimple,
                onClick = { isSimple = false }
            )
        }
    }

    if (isSimple) {
        val times = timesInput.toIntOrNull() ?: 4
        val breakdown = deriveExerciseZoneMinutes(times)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = timesInput,
                onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) timesInput = it },
                label = { Text("Exercise times a week", color = TextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "${breakdown.totalMin} min/week (${times} × 45 min)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AccentTeal
            )

            Text(
                text = "Zone 2: ${breakdown.z2}m (60%) · Zone 3: ${breakdown.z3}m (25%) · Zone 4: ${breakdown.z4}m (10%) · Zone 5: ${breakdown.z5}m (5%)",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        SheetActions(
            onCancel = onCancel,
            onSave = {
                settingsStorage.saveExerciseTargets(
                    timesPerWeek = times,
                    isSimple = true,
                    z2 = breakdown.z2,
                    z3 = breakdown.z3,
                    z4 = breakdown.z4,
                    z5 = breakdown.z5
                )
                onSave()
            },
            saveEnabled = (timesInput.toIntOrNull() ?: 0) > 0
        )
    } else {
        val z2 = z2Input.toIntOrNull() ?: 0
        val z3 = z3Input.toIntOrNull() ?: 0
        val z4 = z4Input.toIntOrNull() ?: 0
        val z5 = z5Input.toIntOrNull() ?: 0
        val totalMin = z2 + z3 + z4 + z5
        val approxTimes = (totalMin / 45.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.US, it) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = z2Input,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) z2Input = it },
                    label = { Text("Zone 2 (min)", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = z3Input,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) z3Input = it },
                    label = { Text("Zone 3 (min)", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = z4Input,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) z4Input = it },
                    label = { Text("Zone 4 (min)", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = z5Input,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) z5Input = it },
                    label = { Text("Zone 5 (min)", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = fieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "$totalMin min/week (about $approxTimes sessions of 45m)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = AccentTeal
            )
        }

        SheetActions(
            onCancel = onCancel,
            onSave = {
                val derivedTimes = round(totalMin / 45.0).toInt().coerceAtLeast(1)
                settingsStorage.saveExerciseTargets(
                    timesPerWeek = derivedTimes,
                    isSimple = false,
                    z2 = z2,
                    z3 = z3,
                    z4 = z4,
                    z5 = z5
                )
                onSave()
            },
            saveEnabled = totalMin > 0
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentTeal else SecondaryFill)
            .border(BorderStroke(1.dp, if (isSelected) AccentTeal else BorderDefault), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.Black else TextForeground
        )
    }
}

@Composable
private fun SheetActions(
    onCancel: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel) {
            Text("Cancel", color = TextMuted, fontSize = 13.sp)
        }
        Button(
            onClick = onSave,
            enabled = saveEnabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentTeal,
                contentColor = Color.Black
            )
        ) {
            Text("Save", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextForeground,
    unfocusedTextColor = TextForeground,
    focusedBorderColor = AccentTeal,
    unfocusedBorderColor = BorderInput,
    focusedContainerColor = SecondaryFill,
    unfocusedContainerColor = SecondaryFill
)
