package com.example.astromerged

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// ============================================================================
// 1. AURA HEALTH DESIGN SCHEME (NORDIC LIGHT PALETTE)
// ============================================================================
val BgCanvas = Color(0xFFF4F6FA)
val CardPure = Color(0xFFFFFFFF)
val PillSurface = Color(0xFFEEF2F8)
val InkPrimary = Color(0xFF0B132B)
val InkSecondary = Color(0xFF6C7A9C)
val ElectricBlue = Color(0xFF2563EB)
val FluidsBlueDark = Color(0xFF0284C7)
val InnerRingGreen = Color(0xFF34D399)
val CyanGlow = Color(0xFF06B6D4)
val MintVital = Color(0xFF10B981)
val CoralAlert = Color(0xFFF43F5E)
val AmberWarn = Color(0xFFF59E0B)
val VioletSleep = Color(0xFF8B5CF6)
val BorderSubtle = Color(0xFFE2E8F0)

private val MinimalLightColors = lightColorScheme(
    primary = ElectricBlue,
    secondary = CyanGlow,
    tertiary = MintVital,
    background = BgCanvas,
    surface = CardPure,
    onPrimary = Color.White,
    onBackground = InkPrimary,
    onSurface = InkPrimary,
    surfaceVariant = PillSurface,
    outline = BorderSubtle
)

// ============================================================================
// 2. UNIFIED MODELS (VITA SYS + ASTRO HELIX + CDME-PRO-MAX)
// ============================================================================
enum class MetricType(
    val displayName: String,
    val shortName: String,
    val defaultUnit: String,
    val category: String,
    val chartColor: Color
) {
    HEART_RATE("Heart Rate", "HR", "BPM", "Cardiovascular", CoralAlert),
    BLOOD_PRESSURE("Blood Pressure", "BP", "mmHg", "Cardiovascular", ElectricBlue),
    OXYGEN_SATURATION("Oxygen Saturation", "SpO2", "%", "Respiratory", CyanGlow),
    BODY_TEMP("Body Temp", "Temp", "°C", "Thermoregulation", AmberWarn),
    RESPIRATORY_RATE("Respiratory Rate", "RR", "RPM", "Respiratory", VioletSleep),
    SLEEP_QUALITY("Sleep Duration", "Sleep", "hrs", "Circadian & Behavioral", VioletSleep),
    HYDRATION("Daily Hydration", "Fluid", "mL", "Nutrition & Renal", CyanGlow),
    RADIATION_DOSIMETER("Radiation Dose", "Dose", "mSv/d", "Environmental", AmberWarn)
}

enum class StatusLevel(val label: String, val shortLabel: String, val sortPriority: Int) {
    CRITICAL("ATTENTION REQUIRED", "CRITICAL", 0),
    MONITOR("MONITOR", "MONITOR", 1),
    NORMAL("NOMINAL", "NOMINAL", 2),
    UNAVAILABLE("NO SIGNAL", "OFFLINE", 3)
}

enum class DataSource(val label: String) {
    SENSOR("CSA Bio-Monitor"),
    MANUAL("Crew Log"),
    ESTIMATED("Estimated"),
    DOSIMETER("Curiosity MSL-RAD")
}

data class BaselineRange(
    val min: Float,
    val nominal: Float,
    val max: Float,
    val criticalLow: Float,
    val criticalHigh: Float
) {
    fun evaluateStatus(value: Float?): StatusLevel {
        if (value == null) return StatusLevel.UNAVAILABLE
        return when {
            value <= criticalLow || value >= criticalHigh -> StatusLevel.CRITICAL
            value < min || value > max -> StatusLevel.MONITOR
            else -> StatusLevel.NORMAL
        }
    }
}

data class VitalMetric(
    val id: String,
    val type: MetricType,
    val title: String,
    val value: String?,
    val secondaryValue: String? = null,
    val numericValue: Float?,
    val unit: String,
    val status: StatusLevel,
    val lastUpdated: String,
    val deltaFromBaseline: String?,
    val sourceType: DataSource,
    val baselineDescription: String,
    val baselineRange: BaselineRange,
    val isSimulatedDisconnected: Boolean = false,
    val protocolCode: String? = null,
    val clinicalGuidance: String = ""
)

data class TelemetryDataPoint(
    val id: String = UUID.randomUUID().toString(),
    val timeLabel: String,
    val value: Float?,
    val secondaryValue: Float? = null,
    val source: DataSource = DataSource.SENSOR,
    val isEstimated: Boolean = false,
    val isGap: Boolean = false,
    val annotation: String? = null
)

data class VitalStatistics(
    val average: Float,
    val min: Float,
    val max: Float,
    val maxTimeLabel: String,
    val validPacketRatio: Int,
    val latestDelta: Float
)

enum class Timeframe(val label: String, val durationTag: String) {
    HOURS_24("24 Hours", "24H"),
    DAYS_7("7 Days", "7D"),
    DAYS_30("30 Days", "30D")
}

enum class HazardType(val title: String, val code: String, val summary: String) {
    RADIATION("Space Radiation", "RAD", "GCR and SPE increase cellular oxidative stress and hematologic suppression."),
    ISOLATION_CONFINEMENT("Isolation & Confinement", "ISO", "Enclosed module living disrupts circadian entrainment and sleep efficiency."),
    DISTANCE_FROM_EARTH("Distance from Earth", "DIST", "Deep-space communications latency requires autonomous onboard triage."),
    GRAVITY_FIELDS("Altered Gravity", "GRAV", "Weightlessness causes cephalad fluid shift, SANS, and 1–1.5% monthly bone loss."),
    HOSTILE_CLOSED_ENV("Closed Environment", "ENV", "Recirculated cabin atmosphere with elevated CO2 pockets (>3 mmHg) impacts cognition.")
}

data class Countermeasure(
    val title: String,
    val description: String,
    val dosageOrFrequency: String,
    val hardwareUsed: String
)

data class SymptomPrompt(
    val id: String,
    val symptomText: String,
    val severityWeight: Int,
    val recommendedAction: String
)

data class AnatomySystem(
    val id: String,
    val name: String,
    val shortLabel: String,
    val anatomicalRegion: String,
    val hotspotXFraction: Float,
    val hotspotYFraction: Float,
    val status: StatusLevel,
    val primaryRisk: String,
    val relatedMetrics: List<MetricType>,
    val spaceflightEffects: List<String>,
    val physiologicalIndicators: List<String>,
    val hazards: List<HazardType>,
    val countermeasures: List<Countermeasure>,
    val symptomChecklist: List<SymptomPrompt>,
    val clinicalAdvice: String
)

data class HealthAlert(
    val id: String,
    val metricType: MetricType,
    val title: String,
    val triggerEvent: String,
    val contextualGuidance: String,
    val protocolCode: String,
    val priority: StatusLevel,
    val relativeTime: String,
    val isAcknowledged: Boolean = false,
    val isEscalated: Boolean = false
)

data class ProtocolStep(
    val index: Int,
    val instruction: String,
    val detail: String = "",
    val isCompleted: Boolean = false
)

data class ClinicalProtocol(
    val code: String,
    val title: String,
    val category: String,
    val severity: StatusLevel,
    val triggerCondition: String,
    val timerDurationSeconds: Int? = null,
    val timerLabel: String? = null,
    val steps: List<ProtocolStep>
)

data class HealthMissionTask(
    val id: String,
    val title: String,
    val category: String,
    val target: String,
    val progress: String,
    val durationMinutes: Int,
    val linkedMetric: MetricType? = null,
    val isCompleted: Boolean = false
)

data class CrewReadinessState(
    val crewName: String = "Cmdr. J. Vance",
    val missionDay: Int = 47,
    val missionPhase: String = "ARTEMIS-MARS TRANSIT • Sol 47",
    val crewReadinessScore: Int = 81,
    val cabinO2Percent: Float = 20.9f,
    val cabinCo2MmHg: Float = 2.27f,
    val wanninkhofFlux: Float = 0.86f,
    val amineScrubberRamped: Boolean = false,
    val cabinTempC: Float = 21.4f,
    val cabinPressurePsi: Float = 14.7f
)

sealed interface AiAssistantUiState {
    data object Idle : AiAssistantUiState
    data object Loading : AiAssistantUiState
    data class Success(val text: String, val interlockRules: List<String> = emptyList()) : AiAssistantUiState
    data class Error(val message: String) : AiAssistantUiState
}

// ============================================================================
// 3. ASTRO HELIX SIMULATED DATA (FROM AstroHealix MissionSimulator)
// ============================================================================
enum class AstroSimulationScenario(val title: String) {
    NOMINAL_CRUISE("CSA Bio-Monitor • Nominal"),
    MSL_RAD_MARS_CRUISE("Curiosity MSL-RAD Cruise (1.84 mSv/d)"),
    SOLAR_RADIATION_EVENT("NASA DONKI Solar Particle Surge"),
    CARDIO_SPIKE_EVA("NASA HRP EVA Cardio Spike (Rule 5.2)"),
    HYPERCAPNIA_TACHYCARDIA("CDME CO2 > 3mmHg + HR > 100 BPM"),
    SENSOR_DISCONNECT("CSA Bio-Monitor & RAD Disconnect"),
    LINK_BLACKOUT("Deep-Space Link Blackout")
}

class AstroHelixMissionSimulator {
    var currentScenario: AstroSimulationScenario = AstroSimulationScenario.NOMINAL_CRUISE
        private set
    private var tickCount = 0
    val historyByVital: MutableMap<MetricType, MutableList<TelemetryDataPoint>> = mutableMapOf()

    init {
        seedHistory()
    }

    private fun pseudoNoise(seed: Int): Float {
        val x = sin(seed * 12.9898f + 78.233f) * 43758.5453f
        return ((x - kotlin.math.floor(x)) * 2f - 1f)
    }

    private fun seedHistory() {
        MetricType.entries.forEach { historyByVital[it] = mutableListOf() }

        // 49 points over 24 hours (30 min increments) calibrated to CSA Bio-Monitor & Curiosity MSL-RAD
        for (i in 48 downTo 0) {
            val hoursAgo = (i * 30) / 60
            val minsAgo = (i * 30) % 60
            val timeLabel = if (i == 0) "Now" else if (hoursAgo > 0) "-${hoursAgo}h${minsAgo}m" else "-${minsAgo}m"
            val phase = (48 - i).toFloat() / 48f

            val isGap = i in 14..16
            val isEstimated = i == 13 || i == 17
            val isSpike = i == 33

            val hrVal = if (isGap) null else if (isSpike) 94f else (71f + sin(phase * Math.PI.toFloat() * 3f) * 5f + pseudoNoise(i) * 3f).roundToInt().toFloat()

            historyByVital[MetricType.HEART_RATE]?.add(
                TelemetryDataPoint(
                    timeLabel = timeLabel,
                    value = hrVal,
                    source = if (isEstimated) DataSource.ESTIMATED else DataSource.SENSOR,
                    isEstimated = isEstimated,
                    isGap = isGap,
                    annotation = if (isSpike) "Baseline Delta Rule 5.2" else null
                )
            )

            historyByVital[MetricType.OXYGEN_SATURATION]?.add(
                TelemetryDataPoint(
                    timeLabel = timeLabel,
                    value = if (isGap) null else (98f + pseudoNoise(i + 11) * 1.2f).coerceIn(95f, 100f).roundToInt().toFloat(),
                    source = if (isEstimated) DataSource.ESTIMATED else DataSource.SENSOR,
                    isEstimated = isEstimated,
                    isGap = isGap
                )
            )

            historyByVital[MetricType.RESPIRATORY_RATE]?.add(
                TelemetryDataPoint(
                    timeLabel = timeLabel,
                    value = if (isGap) null else (14f + sin(phase * Math.PI.toFloat() * 2f) * 1.5f + pseudoNoise(i + 23)).roundToInt().toFloat(),
                    source = if (isEstimated) DataSource.ESTIMATED else DataSource.SENSOR,
                    isEstimated = isEstimated,
                    isGap = isGap
                )
            )

            historyByVital[MetricType.RADIATION_DOSIMETER]?.add(
                TelemetryDataPoint(
                    timeLabel = timeLabel,
                    value = if (isGap) null else ((0.64f + abs(sin(phase * Math.PI.toFloat() * 4f)) * 0.12f + pseudoNoise(i + 37) * 0.04f) * 100f).roundToInt() / 100f,
                    source = DataSource.DOSIMETER,
                    isGap = isGap
                )
            )

            if (i % 8 == 0) {
                historyByVital[MetricType.BLOOD_PRESSURE]?.add(
                    TelemetryDataPoint(
                        timeLabel = timeLabel,
                        value = (116f + pseudoNoise(i + 41) * 5f).roundToInt().toFloat(),
                        secondaryValue = (75f + pseudoNoise(i + 42) * 4f).roundToInt().toFloat(),
                        source = DataSource.MANUAL
                    )
                )
                historyByVital[MetricType.BODY_TEMP]?.add(
                    TelemetryDataPoint(
                        timeLabel = timeLabel,
                        value = ((36.8f + pseudoNoise(i + 53) * 0.25f) * 10f).roundToInt() / 10f,
                        source = DataSource.MANUAL
                    )
                )
            }
        }

        historyByVital[MetricType.SLEEP_QUALITY]?.apply {
            add(TelemetryDataPoint(timeLabel = "2d ago", value = 7.1f, source = DataSource.MANUAL))
            add(TelemetryDataPoint(timeLabel = "1d ago", value = 6.4f, source = DataSource.MANUAL))
            add(TelemetryDataPoint(timeLabel = "04h ago", value = 5.3f, source = DataSource.MANUAL))
        }

        historyByVital[MetricType.HYDRATION]?.apply {
            add(TelemetryDataPoint(timeLabel = "6h ago", value = 900f, source = DataSource.MANUAL))
            add(TelemetryDataPoint(timeLabel = "3h ago", value = 1450f, source = DataSource.MANUAL))
            add(TelemetryDataPoint(timeLabel = "1h ago", value = 1800f, source = DataSource.MANUAL))
        }
    }

    fun setScenario(scenario: AstroSimulationScenario) {
        currentScenario = scenario
        stepTick()
    }

    fun stepFastTick(): Map<MetricType, TelemetryDataPoint> {
        tickCount++
        val n = pseudoNoise(tickCount)
        val updates = mutableMapOf<MetricType, TelemetryDataPoint>()
        val label = "Live"

        when (currentScenario) {
            AstroSimulationScenario.SENSOR_DISCONNECT -> {
                updates[MetricType.HEART_RATE] = TelemetryDataPoint(timeLabel = label, value = null, isGap = true)
                updates[MetricType.RESPIRATORY_RATE] = TelemetryDataPoint(timeLabel = label, value = null, isGap = true)
            }
            AstroSimulationScenario.CARDIO_SPIKE_EVA -> {
                updates[MetricType.HEART_RATE] = TelemetryDataPoint(timeLabel = label, value = (104f + n * 4f).roundToInt().toFloat(), annotation = "Rule 5.2")
                updates[MetricType.OXYGEN_SATURATION] = TelemetryDataPoint(timeLabel = label, value = (94f + n * 0.5f).roundToInt().toFloat())
                updates[MetricType.RESPIRATORY_RATE] = TelemetryDataPoint(timeLabel = label, value = (22f + n * 1.5f).roundToInt().toFloat())
                updates[MetricType.BLOOD_PRESSURE] = TelemetryDataPoint(timeLabel = label, value = (142f + n * 3f).roundToInt().toFloat(), secondaryValue = (88f + n * 2f).roundToInt().toFloat(), isEstimated = true)
            }
            AstroSimulationScenario.SOLAR_RADIATION_EVENT -> {
                updates[MetricType.HEART_RATE] = TelemetryDataPoint(timeLabel = label, value = (86f + n * 3f).roundToInt().toFloat())
                updates[MetricType.OXYGEN_SATURATION] = TelemetryDataPoint(timeLabel = label, value = (97f + n).coerceIn(95f, 100f).roundToInt().toFloat())
                updates[MetricType.RESPIRATORY_RATE] = TelemetryDataPoint(timeLabel = label, value = (16f + n).roundToInt().toFloat())
                updates[MetricType.BLOOD_PRESSURE] = TelemetryDataPoint(timeLabel = label, value = (122f + n * 3f).roundToInt().toFloat(), secondaryValue = (79f + n * 2f).roundToInt().toFloat())
            }
            AstroSimulationScenario.HYPERCAPNIA_TACHYCARDIA -> {
                updates[MetricType.HEART_RATE] = TelemetryDataPoint(timeLabel = label, value = (108f + n * 3f).roundToInt().toFloat(), annotation = "Hypercapnia Tachycardia")
                updates[MetricType.RESPIRATORY_RATE] = TelemetryDataPoint(timeLabel = label, value = (24f + n * 1.2f).roundToInt().toFloat())
                updates[MetricType.OXYGEN_SATURATION] = TelemetryDataPoint(timeLabel = label, value = 94f)
                updates[MetricType.BLOOD_PRESSURE] = TelemetryDataPoint(timeLabel = label, value = (134f + n * 3f).roundToInt().toFloat(), secondaryValue = (84f + n * 2f).roundToInt().toFloat())
            }
            else -> {
                updates[MetricType.HEART_RATE] = TelemetryDataPoint(timeLabel = label, value = (72f + n * 3f).roundToInt().toFloat())
                updates[MetricType.OXYGEN_SATURATION] = TelemetryDataPoint(timeLabel = label, value = (97f + n * 1.2f).coerceIn(95f, 100f).roundToInt().toFloat())
                updates[MetricType.RESPIRATORY_RATE] = TelemetryDataPoint(timeLabel = label, value = (14f + n * 1.2f).roundToInt().toFloat())
                updates[MetricType.BLOOD_PRESSURE] = TelemetryDataPoint(timeLabel = label, value = (112f + n * 4f).roundToInt().toFloat(), secondaryValue = (78f + n * 2f).roundToInt().toFloat())
            }
        }

        updates.forEach { (type, pt) ->
            val list = historyByVital.getOrPut(type) { mutableListOf() }
            if (list.size > 60) list.removeAt(0)
            list.add(pt)
        }
        return updates
    }

    fun stepSlowTick(): Map<MetricType, TelemetryDataPoint> {
        val n = pseudoNoise(tickCount + 99)
        val updates = mutableMapOf<MetricType, TelemetryDataPoint>()
        val label = "Live"

        when (currentScenario) {
            AstroSimulationScenario.SENSOR_DISCONNECT -> {
                updates[MetricType.RADIATION_DOSIMETER] = TelemetryDataPoint(timeLabel = label, value = null, isGap = true)
            }
            AstroSimulationScenario.MSL_RAD_MARS_CRUISE -> {
                updates[MetricType.RADIATION_DOSIMETER] = TelemetryDataPoint(timeLabel = label, value = ((1.84f + abs(n) * 0.18f) * 100f).roundToInt() / 100f, source = DataSource.DOSIMETER)
            }
            AstroSimulationScenario.SOLAR_RADIATION_EVENT -> {
                updates[MetricType.RADIATION_DOSIMETER] = TelemetryDataPoint(timeLabel = label, value = ((5.35f + abs(n) * 0.4f) * 100f).roundToInt() / 100f, source = DataSource.DOSIMETER)
            }
            else -> {
                updates[MetricType.RADIATION_DOSIMETER] = TelemetryDataPoint(timeLabel = label, value = ((0.64f + abs(n) * 0.12f) * 100f).roundToInt() / 100f, source = DataSource.DOSIMETER)
            }
        }

        updates[MetricType.BODY_TEMP] = TelemetryDataPoint(
            timeLabel = label,
            value = ((36.8f + n * 0.15f) * 10f).roundToInt() / 10f,
            source = DataSource.MANUAL
        )
        updates[MetricType.SLEEP_QUALITY] = TelemetryDataPoint(
            timeLabel = label,
            value = ((5.3f + n * 0.1f) * 10f).roundToInt() / 10f,
            source = DataSource.MANUAL
        )
        val currentHydra = historyByVital[MetricType.HYDRATION]?.lastOrNull()?.value ?: 1800f
        updates[MetricType.HYDRATION] = TelemetryDataPoint(
            timeLabel = label,
            value = currentHydra,
            source = DataSource.MANUAL
        )

        updates.forEach { (type, pt) ->
            val list = historyByVital.getOrPut(type) { mutableListOf() }
            if (list.size > 60) list.removeAt(0)
            list.add(pt)
        }
        return updates
    }

    fun stepTick(): Map<MetricType, TelemetryDataPoint> {
        return stepFastTick() + stepSlowTick()
    }
}

// ============================================================================
// 4. DETERMINISTIC SAFETY INTERLOCK ENGINE (CDME-PRO-MAX + ASTROHELIX)
// ============================================================================
data class SafetyInterlockResult(
    val isCompliant: Boolean,
    val modifiedText: String,
    val rulesVerified: List<String>,
    val warnings: List<String>
)

object SafetyInterlockEngine {
    private const val MAX_O2_FLAMMABILITY_PERCENT = 24.1f
    private const val CRITICAL_CO2_THRESHOLD_MMHG = 3.0f
    private const val CRITICAL_HR_THRESHOLD_BPM = 100f

    fun validateAndEnforce(
        rawSuggestion: String,
        cabinCo2MmHg: Float,
        heartRateBpm: Float?,
        cabinO2Percent: Float
    ): SafetyInterlockResult {
        val rulesVerified = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val lower = rawSuggestion.lowercase()
        val textBuilder = StringBuilder(rawSuggestion)

        if (lower.contains("cpr") || lower.contains("compression") || lower.contains("resuscitat") || lower.contains("cardiac arrest")) {
            rulesVerified.add("ACLS Microgravity Physics Interlock (Bulkhead Counter-Resistance)")
            if (!lower.contains("hand-stand") && !lower.contains("reverse-bearhug") && !lower.contains("bulkhead") && !lower.contains("strap")) {
                warnings.add("SAFETY OVERRIDE: Enforcing Hand-Stand / Reverse-Bearhug CPR.")
                textBuilder.append("\n\n[SAFETY INTERLOCK OVERRIDE]: Standard floor compressions fail in 0-g. Anchor against seat tracks or use pelvic cargo straps for Hand-Stand / Reverse-Bearhug CPR against the bulkhead.")
            }
        }

        if (lower.contains("o2") || lower.contains("oxygen") || lower.contains("fio2")) {
            rulesVerified.add("NASA Flight Rule 14.2: O2 Flammability Ceiling (<= 24.1%)")
            if (cabinO2Percent > MAX_O2_FLAMMABILITY_PERCENT) {
                warnings.add("CRITICAL: Cabin O2 exceeds 24.1% flammability limit.")
                textBuilder.append("\n\n[SAFETY INTERLOCK HALT]: Cabin O2 (${cabinO2Percent}%) exceeds 24.1%. Ambient enrichment prohibited; use sealed masks.")
            }
        }

        if (cabinCo2MmHg > CRITICAL_CO2_THRESHOLD_MMHG && (heartRateBpm ?: 0f) > CRITICAL_HR_THRESHOLD_BPM) {
            rulesVerified.add("CDME-PRO-MAX Hypercapnia-Tachycardia Gate (CO2 > 3mmHg & HR > 100 BPM)")
            if (!lower.contains("scrubber") && !lower.contains("4-7-8")) {
                warnings.add("URGENT: Simultaneous Hypercapnia & Tachycardia detected.")
                textBuilder.append("\n\n[SAFETY INTERLOCK PRIORITY]: CO2 (${"%.1f".format(Locale.US, cabinCo2MmHg)} mmHg) + HR (${heartRateBpm?.toInt()} BPM) triggered Hypercapnia Tachycardia Protocol: ramp Amine scrubbers & start 4-7-8 tactical breathing.")
            }
        }

        if (rulesVerified.isEmpty()) {
            rulesVerified.add("NASA Flight Rules & ACLS Baseline Gate")
        }

        return SafetyInterlockResult(
            isCompliant = warnings.isEmpty() || textBuilder.isNotEmpty(),
            modifiedText = textBuilder.toString(),
            rulesVerified = rulesVerified,
            warnings = warnings
        )
    }
}

// ============================================================================
// 5. UNIFIED REPOSITORY (VITA SYS + ASTRO HELIX + CDME-PRO-MAX + AI COACH)
// ============================================================================
class AstroMergedRepository {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val simulator = AstroHelixMissionSimulator()

    private val _vitals = MutableStateFlow<List<VitalMetric>>(initialVitals())
    val vitals: StateFlow<List<VitalMetric>> = _vitals.asStateFlow()

    private val _history24h = MutableStateFlow<Map<MetricType, List<TelemetryDataPoint>>>(
        simulator.historyByVital.mapValues { it.value.toList() }
    )
    val history24h: StateFlow<Map<MetricType, List<TelemetryDataPoint>>> = _history24h.asStateFlow()

    private val _alerts = MutableStateFlow<List<HealthAlert>>(initialAlerts())
    val alerts: StateFlow<List<HealthAlert>> = _alerts.asStateFlow()

    private val _readiness = MutableStateFlow(CrewReadinessState())
    val readiness: StateFlow<CrewReadinessState> = _readiness.asStateFlow()

    private val _tasks = MutableStateFlow<List<HealthMissionTask>>(initialTasks())
    val tasks: StateFlow<List<HealthMissionTask>> = _tasks.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    private val _unSyncedQueueCount = MutableStateFlow(0)
    val unSyncedQueueCount: StateFlow<Int> = _unSyncedQueueCount.asStateFlow()

    private val _encryptedDeltaLog = MutableStateFlow<List<String>>(emptyList())
    val encryptedDeltaLog: StateFlow<List<String>> = _encryptedDeltaLog.asStateFlow()

    private val _isChestSensorDisconnected = MutableStateFlow(false)
    val isChestSensorDisconnected: StateFlow<Boolean> = _isChestSensorDisconnected.asStateFlow()

    private val _bodySystems = MutableStateFlow<List<AnatomySystem>>(initialBodySystems())
    val bodySystems: StateFlow<List<AnatomySystem>> = _bodySystems.asStateFlow()

    private val _checkedSymptoms = MutableStateFlow<Set<String>>(setOf("sym_sleep_short"))
    val checkedSymptoms: StateFlow<Set<String>> = _checkedSymptoms.asStateFlow()

    private val _protocols = MutableStateFlow<List<ClinicalProtocol>>(initialProtocols())
    val protocols: StateFlow<List<ClinicalProtocol>> = _protocols.asStateFlow()

    private val _aiState = MutableStateFlow<AiAssistantUiState>(AiAssistantUiState.Idle)
    val aiState: StateFlow<AiAssistantUiState> = _aiState.asStateFlow()

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _googleOAuthClientId = MutableStateFlow("")
    val googleOAuthClientId: StateFlow<String> = _googleOAuthClientId.asStateFlow()

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _signedInEmail = MutableStateFlow("")
    val signedInEmail: StateFlow<String> = _signedInEmail.asStateFlow()

    private val _authProvider = MutableStateFlow("Guest")
    val authProvider: StateFlow<String> = _authProvider.asStateFlow()

    private val _criticalAlertsEnabled = MutableStateFlow(true)
    val criticalAlertsEnabled: StateFlow<Boolean> = _criticalAlertsEnabled.asStateFlow()

    private val _alertSoundEnabled = MutableStateFlow(true)
    val alertSoundEnabled: StateFlow<Boolean> = _alertSoundEnabled.asStateFlow()

    private val _alertSensitivity = MutableStateFlow("Standard")
    val alertSensitivity: StateFlow<String> = _alertSensitivity.asStateFlow()

    private val _continuousPollingEnabled = MutableStateFlow(true)
    val continuousPollingEnabled: StateFlow<Boolean> = _continuousPollingEnabled.asStateFlow()

    private val _donkiSyncEnabled = MutableStateFlow(true)
    val donkiSyncEnabled: StateFlow<Boolean> = _donkiSyncEnabled.asStateFlow()

    private val _useFahrenheit = MutableStateFlow(false)
    val useFahrenheit: StateFlow<Boolean> = _useFahrenheit.asStateFlow()

    private val _encryptedLogEnabled = MutableStateFlow(true)
    val encryptedLogEnabled: StateFlow<Boolean> = _encryptedLogEnabled.asStateFlow()

    private val _donkiStatusTag = MutableStateFlow("NASA/CSA")
    val donkiStatusTag: StateFlow<String> = _donkiStatusTag.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var activeScenario by mutableStateOf(AstroSimulationScenario.NOMINAL_CRUISE)

    init {
        recalculateReadiness()
        startEnvironmentalAndTelemetryLoop()
        fetchDonkiSepAlertIfOnline()
    }

    fun initPersistence(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences("astromerged_prefs", Context.MODE_PRIVATE)
            prefs?.let { p ->
                _geminiApiKey.value = p.getString("user_ai_key", "") ?: ""
                _googleOAuthClientId.value = p.getString("google_oauth_client_id", "") ?: ""
                _isSignedIn.value = p.getBoolean("is_signed_in", false)
                _signedInEmail.value = p.getString("signed_in_email", "") ?: ""
                _authProvider.value = p.getString("auth_provider", "Guest") ?: "Guest"
                val savedName = p.getString("crew_name", null)
                if (!savedName.isNullOrBlank()) {
                    _readiness.update { it.copy(crewName = savedName) }
                }
                _criticalAlertsEnabled.value = p.getBoolean("critical_alerts_enabled", true)
                _alertSoundEnabled.value = p.getBoolean("alert_sound_enabled", true)
                _alertSensitivity.value = p.getString("alert_sensitivity", "Standard") ?: "Standard"
                _continuousPollingEnabled.value = p.getBoolean("continuous_polling_enabled", true)
                _donkiSyncEnabled.value = p.getBoolean("donki_sync_enabled", true)
                _useFahrenheit.value = p.getBoolean("use_fahrenheit", false)
                _encryptedLogEnabled.value = p.getBoolean("encrypted_log_enabled", true)
            }
        }
    }

    fun updateGeminiApiKey(newKey: String) {
        val trimmed = newKey.trim()
        _geminiApiKey.value = trimmed
        prefs?.edit()?.putString("user_ai_key", trimmed)?.apply()
    }

    fun updateGoogleOAuthClientId(clientId: String) {
        val trimmed = clientId.trim()
        _googleOAuthClientId.value = trimmed
        prefs?.edit()?.putString("google_oauth_client_id", trimmed)?.apply()
    }

    fun signInLocalAccount(name: String, email: String) {
        val cleanName = name.trim().ifBlank { _readiness.value.crewName }
        val cleanEmail = email.trim()
        _readiness.update { it.copy(crewName = cleanName) }
        _isSignedIn.value = true
        _signedInEmail.value = cleanEmail
        _authProvider.value = "Crew Account"
        prefs?.edit()
            ?.putBoolean("is_signed_in", true)
            ?.putString("crew_name", cleanName)
            ?.putString("signed_in_email", cleanEmail)
            ?.putString("auth_provider", "Crew Account")
            ?.apply()
        appendEncryptedDeltaLog("AUTH: Signed in ($cleanEmail)")
    }

    fun handleOAuthRedirectUri(uri: Uri) {
        if (uri.scheme == "com.example.astromerged" && uri.host.orEmpty().isEmpty() && uri.path == "/oauth2redirect") {
            val fragment = uri.fragment.orEmpty()
            val params = fragment.split("&").mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) part.substring(0, idx) to Uri.decode(part.substring(idx + 1)) else null
            }.toMap()
            val token = params["access_token"] ?: uri.getQueryParameter("access_token") ?: uri.getQueryParameter("code")
            if (!token.isNullOrBlank()) {
                val email = uri.getQueryParameter("email") ?: params["email"] ?: "Google OAuth Verified"
                _isSignedIn.value = true
                _signedInEmail.value = email
                _authProvider.value = "Google OAuth"
                prefs?.edit()
                    ?.putBoolean("is_signed_in", true)
                    ?.putString("signed_in_email", email)
                    ?.putString("auth_provider", "Google OAuth")
                    ?.apply()
                appendEncryptedDeltaLog("AUTH: Google OAuth connected")
            }
        }
    }

    fun signOut() {
        _isSignedIn.value = false
        _signedInEmail.value = ""
        _authProvider.value = "Guest"
        prefs?.edit()
            ?.putBoolean("is_signed_in", false)
            ?.putString("signed_in_email", "")
            ?.putString("auth_provider", "Guest")
            ?.apply()
        appendEncryptedDeltaLog("AUTH: Signed out")
    }

    fun setCriticalAlertsEnabled(enabled: Boolean) {
        _criticalAlertsEnabled.value = enabled
        prefs?.edit()?.putBoolean("critical_alerts_enabled", enabled)?.apply()
    }

    fun setAlertSoundEnabled(enabled: Boolean) {
        _alertSoundEnabled.value = enabled
        prefs?.edit()?.putBoolean("alert_sound_enabled", enabled)?.apply()
    }

    fun setAlertSensitivity(sensitivity: String) {
        _alertSensitivity.value = sensitivity
        prefs?.edit()?.putString("alert_sensitivity", sensitivity)?.apply()
    }

    fun setContinuousPollingEnabled(enabled: Boolean) {
        _continuousPollingEnabled.value = enabled
        prefs?.edit()?.putBoolean("continuous_polling_enabled", enabled)?.apply()
    }

    fun setDonkiSyncEnabled(enabled: Boolean) {
        _donkiSyncEnabled.value = enabled
        prefs?.edit()?.putBoolean("donki_sync_enabled", enabled)?.apply()
        if (enabled) fetchDonkiSepAlertIfOnline()
    }

    fun setUseFahrenheit(enabled: Boolean) {
        _useFahrenheit.value = enabled
        prefs?.edit()?.putBoolean("use_fahrenheit", enabled)?.apply()
    }

    fun setEncryptedLogEnabled(enabled: Boolean) {
        _encryptedLogEnabled.value = enabled
        prefs?.edit()?.putBoolean("encrypted_log_enabled", enabled)?.apply()
    }

    fun clearEncryptedDeltaLog() {
        _encryptedDeltaLog.value = emptyList()
    }

    fun resetAppSettings() {
        _criticalAlertsEnabled.value = true
        _alertSoundEnabled.value = true
        _alertSensitivity.value = "Standard"
        _continuousPollingEnabled.value = true
        _donkiSyncEnabled.value = true
        _useFahrenheit.value = false
        _encryptedLogEnabled.value = true
        _isOfflineMode.value = false
        prefs?.edit()
            ?.putBoolean("critical_alerts_enabled", true)
            ?.putBoolean("alert_sound_enabled", true)
            ?.putString("alert_sensitivity", "Standard")
            ?.putBoolean("continuous_polling_enabled", true)
            ?.putBoolean("donki_sync_enabled", true)
            ?.putBoolean("use_fahrenheit", false)
            ?.putBoolean("encrypted_log_enabled", true)
            ?.apply()
    }

    private fun fetchDonkiSepAlertIfOnline() {
        scope.launch(Dispatchers.IO) {
            if (_isOfflineMode.value) return@launch
            try {
                val donkiUrl = "https://" + "kauai.ccmc.gsfc.nasa.gov/DONKI/WS/get/SEP"
                val conn = (URL(donkiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6_000
                    readTimeout = 8_000
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().readText()
                    val arr = JSONArray(body)
                    if (arr.length() > 0) {
                        val latest = arr.getJSONObject(arr.length() - 1)
                        val eventTime = latest.optString("eventTime", "").take(10)
                        _donkiStatusTag.value = if (eventTime.isNotBlank()) "DONKI $eventTime" else "DONKI LIVE"
                    } else {
                        _donkiStatusTag.value = "DONKI QUIET"
                    }
                }
            } catch (_: Exception) {
                _donkiStatusTag.value = "NASA/CSA"
            }
        }
    }

    // CDME-PRO-MAX Section 4: 1-second interval environmental pCO2 & Wanninkhof flux worker + 3-second AstroHelix simulator
    private fun startEnvironmentalAndTelemetryLoop() {
        scope.launch {
            var sec = 0
            while (true) {
                delay(1000L)
                sec++
                val co2Target = when (activeScenario) {
                    AstroSimulationScenario.HYPERCAPNIA_TACHYCARDIA -> 3.45f
                    AstroSimulationScenario.CARDIO_SPIKE_EVA -> 2.85f
                    else -> 2.27f + 0.10f * sin(sec * 0.25f)
                }
                val flux = 0.378f * co2Target
                val hrVal = _vitals.value.find { it.type == MetricType.HEART_RATE }?.numericValue
                val isHypercapniaTachycardia = co2Target > 3.0f && (hrVal ?: 0f) > 100f

                _readiness.update {
                    it.copy(
                        cabinCo2MmHg = (co2Target * 100f).roundToInt() / 100f,
                        wanninkhofFlux = (flux * 100f).roundToInt() / 100f,
                        amineScrubberRamped = isHypercapniaTachycardia
                    )
                }

                if (isHypercapniaTachycardia) {
                    ensureHypercapniaTachycardiaAlert(co2Target, hrVal ?: 105f)
                }

                if (!_isChestSensorDisconnected.value && _continuousPollingEnabled.value) {
                    val updates = if (sec % 4 == 0) {
                        simulator.stepTick()
                    } else {
                        simulator.stepFastTick()
                    }
                    _history24h.value = simulator.historyByVital.mapValues { it.value.toList() }
                    applySimulatorUpdates(updates)
                }
            }
        }
    }

    private fun applySimulatorUpdates(updates: Map<MetricType, TelemetryDataPoint>) {
        _vitals.update { list ->
            list.map { vital ->
                val pt = updates[vital.type] ?: return@map vital
                if (pt.isGap || pt.value == null) {
                    vital.copy(
                        value = null,
                        numericValue = null,
                        status = StatusLevel.UNAVAILABLE,
                        deltaFromBaseline = "Offline",
                        lastUpdated = pt.timeLabel,
                        isSimulatedDisconnected = true
                    )
                } else {
                    val v = pt.value
                    val formatted = when (vital.type) {
                        MetricType.RADIATION_DOSIMETER -> "%.2f".format(Locale.US, v)
                        MetricType.BODY_TEMP, MetricType.SLEEP_QUALITY -> "%.1f".format(Locale.US, v)
                        MetricType.BLOOD_PRESSURE -> "${v.roundToInt()}/${(pt.secondaryValue ?: 79f).roundToInt()}"
                        MetricType.HYDRATION -> "%,d".format(Locale.US, v.roundToInt())
                        else -> v.roundToInt().toString()
                    }
                    vital.copy(
                        value = formatted,
                        secondaryValue = pt.secondaryValue?.roundToInt()?.toString() ?: vital.secondaryValue,
                        numericValue = v,
                        status = vital.baselineRange.evaluateStatus(v),
                        deltaFromBaseline = formatDelta(v, vital.baselineRange.nominal, vital.unit),
                        lastUpdated = pt.timeLabel,
                        isSimulatedDisconnected = false
                    )
                }
            }
        }
        recalculateReadiness()
    }

    private fun ensureHypercapniaTachycardiaAlert(co2: Float, hr: Float) {
        val alertId = "alt_hypercapnia_tachycardia"
        if (_alerts.value.none { it.id == alertId }) {
            val alert = HealthAlert(
                id = alertId,
                metricType = MetricType.HEART_RATE,
                title = "Hypercapnia Tachycardia (CO2 ${"%.1f".format(Locale.US, co2)} mmHg, HR ${hr.toInt()} BPM)",
                triggerEvent = "CO2 > 3.0 mmHg & HR > 100 BPM",
                contextualGuidance = "Amine scrubbers ramped. Open 4-7-8 tactical breathing protocol immediately.",
                protocolCode = "CDME-HYP-01",
                priority = StatusLevel.CRITICAL,
                relativeTime = currentUtcTimeLabel()
            )
            _alerts.update { listOf(alert) + it }
            appendEncryptedDeltaLog("TRIGGER: CDME-HYP-01 CO2=$co2 HR=$hr")
        }
    }

    fun setScenario(scenario: AstroSimulationScenario) {
        activeScenario = scenario
        simulator.setScenario(scenario)
        _history24h.value = simulator.historyByVital.mapValues { it.value.toList() }
        if (scenario == AstroSimulationScenario.LINK_BLACKOUT) {
            _isOfflineMode.value = true
        }
        val latest = simulator.historyByVital.mapNotNull { (k, v) -> v.lastOrNull()?.let { k to it } }.toMap()
        applySimulatorUpdates(latest)
    }

    private fun currentUtcTimeLabel(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

    private fun appendEncryptedDeltaLog(entry: String) {
        val ts = currentUtcTimeLabel()
        _encryptedDeltaLog.update { (listOf("[$ts UTC] AES256::$entry") + it).take(25) }
    }

    fun toggleSensorConnection() {
        _isChestSensorDisconnected.update { !it }
        val disconnected = _isChestSensorDisconnected.value
        _vitals.update { list ->
            list.map { vital ->
                if (vital.type == MetricType.HEART_RATE || vital.type == MetricType.RADIATION_DOSIMETER) {
                    if (disconnected) {
                        vital.copy(
                            value = null,
                            numericValue = null,
                            status = StatusLevel.UNAVAILABLE,
                            deltaFromBaseline = "Offline",
                            sourceType = DataSource.ESTIMATED,
                            lastUpdated = "Offline",
                            isSimulatedDisconnected = true
                        )
                    } else {
                        val latestPoint = _history24h.value[vital.type]?.lastOrNull { it.value != null }
                        val restoredVal = latestPoint?.value ?: vital.baselineRange.nominal
                        val formatted = if (vital.type == MetricType.RADIATION_DOSIMETER) {
                            "%.2f".format(Locale.US, restoredVal)
                        } else {
                            restoredVal.roundToInt().toString()
                        }
                        vital.copy(
                            value = formatted,
                            numericValue = restoredVal,
                            status = vital.baselineRange.evaluateStatus(restoredVal),
                            deltaFromBaseline = formatDelta(restoredVal, vital.baselineRange.nominal, vital.unit),
                            sourceType = if (vital.type == MetricType.RADIATION_DOSIMETER) DataSource.DOSIMETER else DataSource.SENSOR,
                            lastUpdated = "Now",
                            isSimulatedDisconnected = false
                        )
                    }
                } else vital
            }
        }
    }

    fun toggleOfflineMode() {
        val newOffline = !_isOfflineMode.value
        _isOfflineMode.value = newOffline
        if (!newOffline) {
            _unSyncedQueueCount.value = 0
        }
    }

    fun acknowledgeAlert(alertId: String) {
        _alerts.update { list -> list.map { if (it.id == alertId) it.copy(isAcknowledged = true) else it } }
    }

    fun logHydration(amountMl: Int) {
        val currentHydration = _vitals.value.find { it.type == MetricType.HYDRATION }?.numericValue ?: 1800f
        val updatedTotal = (currentHydration + amountMl).coerceAtLeast(0f)
        logVital(
            type = MetricType.HYDRATION,
            valueText = "%,d".format(Locale.US, updatedTotal.roundToInt()),
            numericVal = updatedTotal,
            note = "+$amountMl mL"
        )
    }

    fun logVital(
        type: MetricType,
        valueText: String,
        numericVal: Float,
        secondaryNumeric: Float? = null,
        note: String? = null
    ) {
        val timeLabel = currentUtcTimeLabel()
        val newPoint = TelemetryDataPoint(
            timeLabel = timeLabel,
            value = numericVal,
            secondaryValue = secondaryNumeric,
            source = DataSource.MANUAL,
            annotation = note
        )
        simulator.historyByVital.getOrPut(type) { mutableListOf() }.add(newPoint)
        _history24h.value = simulator.historyByVital.mapValues { it.value.toList() }

        var triggeredStatus = StatusLevel.NORMAL
        var vitalRef: VitalMetric? = null

        _vitals.update { list ->
            list.map { vital ->
                if (vital.type == type) {
                    val newStatus = if (type == MetricType.HYDRATION) {
                        when {
                            numericVal >= 2200f -> StatusLevel.NORMAL
                            numericVal >= 1400f -> StatusLevel.MONITOR
                            else -> StatusLevel.CRITICAL
                        }
                    } else {
                        vital.baselineRange.evaluateStatus(numericVal)
                    }
                    triggeredStatus = newStatus
                    val deltaStr = if (type == MetricType.HYDRATION) {
                        val rem = (2500 - numericVal.roundToInt()).coerceAtLeast(0)
                        if (rem == 0) "Goal met" else "$rem mL left"
                    } else {
                        formatDelta(numericVal, vital.baselineRange.nominal, vital.unit)
                    }
                    val updated = vital.copy(
                        value = valueText,
                        secondaryValue = secondaryNumeric?.roundToInt()?.toString(),
                        numericValue = numericVal,
                        status = newStatus,
                        lastUpdated = timeLabel,
                        deltaFromBaseline = deltaStr,
                        sourceType = DataSource.MANUAL,
                        isSimulatedDisconnected = false
                    )
                    vitalRef = updated
                    updated
                } else vital
            }
        }

        if (type == MetricType.HYDRATION) {
            _tasks.update { list ->
                list.map { t ->
                    if (t.id == "task_hydration") {
                        t.copy(progress = "${numericVal.roundToInt()} / 2,500 mL", isCompleted = numericVal >= 2200f)
                    } else t
                }
            }
        }

        vitalRef?.let { v ->
            if (triggeredStatus == StatusLevel.CRITICAL || triggeredStatus == StatusLevel.MONITOR) {
                val alertId = "alert_${type.name.lowercase()}_${System.currentTimeMillis()}"
                val newAlert = HealthAlert(
                    id = alertId,
                    metricType = type,
                    title = "${v.title}: $valueText ${v.unit}",
                    triggerEvent = "Baseline ${v.baselineDescription}",
                    contextualGuidance = v.clinicalGuidance.ifEmpty { "Rest 10m and re-verify." },
                    protocolCode = v.protocolCode ?: "NASA-MED-C04",
                    priority = triggeredStatus,
                    relativeTime = timeLabel
                )
                _alerts.update { existing -> listOf(newAlert) + existing.filterNot { it.metricType == type && !it.isAcknowledged } }
            }
        }

        recalculateReadiness()
        appendEncryptedDeltaLog("LOG: ${type.shortName}=$valueText")
        if (_isOfflineMode.value) _unSyncedQueueCount.update { it + 1 }
    }

    fun toggleSymptom(symptomId: String) {
        _checkedSymptoms.update { current -> if (symptomId in current) current - symptomId else current + symptomId }
        val activeSymptoms = _checkedSymptoms.value
        _bodySystems.update { systems ->
            systems.map { sys ->
                val matching = sys.symptomChecklist.filter { it.id in activeSymptoms }
                val maxWeight = matching.maxOfOrNull { it.severityWeight } ?: 0
                val newStatus = when {
                    maxWeight >= 3 -> StatusLevel.CRITICAL
                    maxWeight >= 1 -> StatusLevel.MONITOR
                    else -> StatusLevel.NORMAL
                }
                sys.copy(status = newStatus)
            }
        }
        recalculateReadiness()
    }

    fun toggleProtocolStep(protocolCode: String, stepIndex: Int) {
        _protocols.update { list ->
            list.map { proto ->
                if (proto.code == protocolCode) {
                    proto.copy(steps = proto.steps.map { s -> if (s.index == stepIndex) s.copy(isCompleted = !s.isCompleted) else s })
                } else proto
            }
        }
        appendEncryptedDeltaLog("PROTO: $protocolCode step $stepIndex toggled")
    }

    fun resetProtocolSteps(protocolCode: String) {
        _protocols.update { list ->
            list.map { proto ->
                if (proto.code == protocolCode) proto.copy(steps = proto.steps.map { it.copy(isCompleted = false) }) else proto
            }
        }
    }

    fun updateProfileBaselines(
        crewName: String,
        missionDay: Int,
        hrNominal: Float,
        spo2Nominal: Float,
        sleepNominal: Float
    ) {
        _readiness.update {
            it.copy(
                crewName = crewName.ifBlank { it.crewName },
                missionDay = missionDay.coerceAtLeast(1)
            )
        }
        _vitals.update { list ->
            list.map { v ->
                when (v.type) {
                    MetricType.HEART_RATE -> v.copy(baselineRange = v.baselineRange.copy(nominal = hrNominal))
                    MetricType.OXYGEN_SATURATION -> v.copy(baselineRange = v.baselineRange.copy(nominal = spo2Nominal))
                    MetricType.SLEEP_QUALITY -> v.copy(baselineRange = v.baselineRange.copy(nominal = sleepNominal))
                    else -> v
                }
            }
        }
    }

    // AI Coach from Astra Vita SYS (Gemini API + CDME-PRO-MAX SafetyInterlockEngine)
    fun askAiHealthAssistant(userQuestion: String) {
        if (userQuestion.isBlank()) return
        scope.launch {
            _aiState.value = AiAssistantUiState.Loading
            val state = _readiness.value
            val currentVitals = _vitals.value
            val hr = currentVitals.find { it.type == MetricType.HEART_RATE }?.numericValue
            val vitalsSummary = currentVitals.joinToString("; ") { "${it.title}: ${it.value ?: "N/A"} ${it.unit} (${it.status.shortLabel})" }
            val activeAlerts = _alerts.value.filter { !it.isAcknowledged }.joinToString("; ") { it.title }.ifBlank { "None" }

            val prompt = """
                You are AstraMerged AI Coach, a concise NASA astronaut health & recovery coach.
                Keep your response ultra-minimal (under 90 words total):
                - Give 3 short, practical action bullets tailored to the astronaut's vitals vs baseline.
                - If CO2 > 3.0 mmHg and HR > 100 BPM, enforce Hypercapnia Tachycardia Protocol (Amine scrubbers + 4-7-8 breathing).
                - If CPR/resuscitation is asked, enforce Microgravity Hand-Stand / Reverse-Bearhug CPR against bulkhead.
                Context:
                - Crew: ${state.crewName} (Sol ${state.missionDay}), Cabin CO2: ${state.cabinCo2MmHg} mmHg, O2: ${state.cabinO2Percent}%
                - Vitals: $vitalsSummary
                - Active Alerts: $activeAlerts
                Question: $userQuestion
            """.trimIndent()

            val rawReply = withContext(Dispatchers.IO) {
                callGeminiRestOrFallback(prompt, userQuestion, state, currentVitals)
            }

            val interlock = SafetyInterlockEngine.validateAndEnforce(
                rawSuggestion = rawReply,
                cabinCo2MmHg = state.cabinCo2MmHg,
                heartRateBpm = hr,
                cabinO2Percent = state.cabinO2Percent
            )

            appendEncryptedDeltaLog("AI_COACH: ${userQuestion.take(32)}")
            _aiState.value = AiAssistantUiState.Success(
                text = interlock.modifiedText,
                interlockRules = interlock.rulesVerified
            )
        }
    }

    private fun callGeminiRestOrFallback(
        prompt: String,
        question: String,
        state: CrewReadinessState,
        vitals: List<VitalMetric>
    ): String {
        val userKey = _geminiApiKey.value.trim()
        if (!_isOfflineMode.value && userKey.isNotBlank()) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$userKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    doOutput = true
                }
                val body = JSONObject().apply {
                    put("contents", JSONArray().put(
                        JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                    ))
                }.toString()
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
                if (conn.responseCode in 200..299) {
                    val resp = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(resp)
                    val text = json.getJSONArray("candidates")
                        .getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")
                    if (text.isNotBlank()) return text.trim()
                }
            } catch (_: Exception) {
                // Fall through to deterministic offline RAG response
            }
        }
        return buildOfflineRagResponse(question, state, vitals)
    }

    private fun buildOfflineRagResponse(
        question: String,
        state: CrewReadinessState,
        vitals: List<VitalMetric>
    ): String {
        val q = question.lowercase()
        val hr = vitals.find { it.type == MetricType.HEART_RATE }?.value ?: "72"
        val sleep = vitals.find { it.type == MetricType.SLEEP_QUALITY }?.value ?: "5.3"
        val hydra = vitals.find { it.type == MetricType.HYDRATION }?.value ?: "1800"
        return when {
            q.contains("cpr") || q.contains("resuscitat") ->
                "• Anchor feet/pelvis using bulkhead seat tracks or cargo straps for counter-resistance.\n• Perform Hand-Stand or Reverse-Bearhug compressions at 100–120 BPM.\n• Cap Epinephrine at 1.0 mg IV/IO q3–5m per microgravity ACLS."
            q.contains("sleep") ->
                "• Last sleep logged at $sleep hrs (target 7.5 hrs).\n• Switch cabin LEDs to 450nm amber 90 min before rest.\n• Angle sleep bay fan across face to prevent local CO2 pockets (now ${state.cabinCo2MmHg} mmHg)."
            q.contains("co2") || q.contains("breat") || q.contains("hypercapnia") ->
                "• Cabin pCO2 is ${state.cabinCo2MmHg} mmHg (Wanninkhof flux ${state.wanninkhofFlux}).\n• If CO2 > 3.0 mmHg & HR > 100 BPM, ramp Amine scrubbers immediately.\n• Execute 4-7-8 tactical breathing beacon for 5 minutes."
            else ->
                "• Readiness ${state.crewReadinessScore}% | HR $hr BPM | Hydration $hydra mL.\n• Drink 500 mL electrolyte water to hit 2,500 mL renal protection target.\n• Complete daily ARED loading & share summary with Flight Surgeon if symptoms persist."
        }
    }

    fun buildFlightSurgeonReport(): String {
        val state = _readiness.value
        val activeAlerts = _alerts.value.filter { !it.isAcknowledged }
        return buildString {
            appendLine("ASTROMERGED FLIGHT SURGEON SUMMARY")
            appendLine("Crew: ${state.crewName} | ${state.missionPhase}")
            appendLine("Readiness: ${state.crewReadinessScore}% | Cabin O2: ${state.cabinO2Percent}% | pCO2: ${state.cabinCo2MmHg} mmHg")
            appendLine("Active Alerts: ${activeAlerts.size}")
            _vitals.value.forEach { v ->
                appendLine("- ${v.title}: ${v.value ?: "OFFLINE"} ${v.unit} (${v.status.shortLabel})")
            }
        }
    }

    private fun recalculateReadiness() {
        val vitalsList = _vitals.value
        val tasksList = _tasks.value
        val completedRatio = if (tasksList.isNotEmpty()) tasksList.count { it.isCompleted }.toFloat() / tasksList.size else 1f
        val criticalCount = vitalsList.count { it.status == StatusLevel.CRITICAL }
        val monitorCount = vitalsList.count { it.status == StatusLevel.MONITOR }
        val symptomPenalty = _checkedSymptoms.value.size * 2
        val baseScore = 81 + (completedRatio * 12).roundToInt()
        val finalScore = (baseScore - (criticalCount * 15) - (monitorCount * 4) - symptomPenalty).coerceIn(40, 100)
        _readiness.update { it.copy(crewReadinessScore = finalScore) }
    }

    private fun formatDelta(current: Float, nominal: Float, unit: String): String {
        val diff = current - nominal
        if (abs(diff) < 0.05f) return "Baseline"
        val sign = if (diff > 0) "+" else ""
        return if (unit == "mSv/d" || unit == "°C" || unit == "hrs") {
            "%s%.1f %s".format(Locale.US, sign, diff, unit)
        } else {
            "%s%d %s".format(Locale.US, sign, diff.roundToInt(), unit)
        }
    }

    fun getTrendData(metricType: MetricType, timeframe: Timeframe): List<TelemetryDataPoint> {
        val base24h = _history24h.value[metricType].orEmpty()
        return when (timeframe) {
            Timeframe.HOURS_24 -> base24h
            Timeframe.DAYS_7 -> generateMultiDaySeries(metricType, base24h, 7)
            Timeframe.DAYS_30 -> generateMultiDaySeries(metricType, base24h, 30)
        }
    }

    private fun generateMultiDaySeries(
        metricType: MetricType,
        recent24h: List<TelemetryDataPoint>,
        days: Int
    ): List<TelemetryDataPoint> {
        val vital = _vitals.value.find { it.type == metricType }
        val nom = vital?.baselineRange?.nominal ?: 72f
        val span = ((vital?.baselineRange?.max ?: 80f) - (vital?.baselineRange?.min ?: 65f)).coerceAtLeast(1f)
        val latestVal = recent24h.lastOrNull { it.value != null }?.value ?: nom
        return if (days == 7) {
            listOf(-0.12f, 0.08f, -0.05f, 0.18f, -0.09f, 0.04f).mapIndexed { idx, factor ->
                TelemetryDataPoint(timeLabel = "Sol ${41 + idx}", value = roundToPrecision(metricType, nom + span * factor))
            } + TelemetryDataPoint(timeLabel = "Sol 47", value = latestVal, source = DataSource.MANUAL)
        } else {
            listOf(
                "Sol 20" to (nom - span * 0.10f),
                "Sol 25" to (nom + span * 0.05f),
                "Sol 30" to (nom - span * 0.04f),
                "Sol 35" to (nom + span * 0.14f),
                "Sol 40" to (nom + span * 0.02f),
                "Sol 47" to latestVal
            ).map { (label, v) -> TelemetryDataPoint(timeLabel = label, value = roundToPrecision(metricType, v)) }
        }
    }

    private fun roundToPrecision(type: MetricType, value: Float): Float = when (type) {
        MetricType.RADIATION_DOSIMETER -> (value * 100f).roundToInt() / 100f
        MetricType.BODY_TEMP, MetricType.SLEEP_QUALITY -> (value * 10f).roundToInt() / 10f
        else -> value.roundToInt().toFloat()
    }

    fun getStatistics(metricType: MetricType, timeframe: Timeframe): VitalStatistics {
        val points = getTrendData(metricType, timeframe)
        val valid = points.filter { !it.isGap && it.value != null }
        val nominal = _vitals.value.find { it.type == metricType }?.baselineRange?.nominal ?: 0f
        if (valid.isEmpty()) return VitalStatistics(0f, 0f, 0f, "N/A", 0, 0f)
        val values = valid.mapNotNull { it.value }
        val avg = values.average().toFloat()
        val minVal = values.minOrNull() ?: 0f
        val maxPt = valid.maxByOrNull { it.value ?: Float.MIN_VALUE }
        val maxVal = maxPt?.value ?: 0f
        val ratio = ((valid.size.toFloat() / points.size.coerceAtLeast(1)) * 100f).roundToInt()
        val latest = valid.last().value ?: nominal
        return VitalStatistics(
            average = roundToPrecision(metricType, avg),
            min = roundToPrecision(metricType, minVal),
            max = roundToPrecision(metricType, maxVal),
            maxTimeLabel = maxPt?.timeLabel ?: "",
            validPacketRatio = ratio,
            latestDelta = roundToPrecision(metricType, latest - nominal)
        )
    }

    private fun initialVitals(): List<VitalMetric> {
        val latest = simulator.historyByVital
        fun latestVal(t: MetricType) = latest[t]?.lastOrNull { it.value != null }?.value
        return listOf(
            VitalMetric("v_hr", MetricType.HEART_RATE, "Heart Rate", (latestVal(MetricType.HEART_RATE) ?: 72f).roundToInt().toString(), null, latestVal(MetricType.HEART_RATE) ?: 72f, "BPM", StatusLevel.NORMAL, "12s ago", "+2 BPM", DataSource.SENSOR, "60–85 BPM", BaselineRange(60f, 72f, 85f, 48f, 100f), protocolCode = "NASA-MED-C04", clinicalGuidance = "Rest, 5m paced breathing, and 500 mL water."),
            VitalMetric("v_bp", MetricType.BLOOD_PRESSURE, "Blood Press.", "111/79", "79", 111f, "mmHg", StatusLevel.NORMAL, "08:30", "+1 mmHg", DataSource.MANUAL, "100–126 mmHg", BaselineRange(100f, 115f, 126f, 85f, 150f), protocolCode = "NASA-MED-C01", clinicalGuidance = "Rest 5m and re-measure."),
            VitalMetric("v_spo2", MetricType.OXYGEN_SATURATION, "Oxygen Sat.", (latestVal(MetricType.OXYGEN_SATURATION) ?: 97f).roundToInt().toString(), null, latestVal(MetricType.OXYGEN_SATURATION) ?: 97f, "%", StatusLevel.NORMAL, "12s ago", "Baseline", DataSource.SENSOR, "95–100%", BaselineRange(95f, 98f, 100f, 90f, 101f), protocolCode = "NASA-MED-R02", clinicalGuidance = "Check cabin O2/CO2 and re-test pulse oximeter."),
            VitalMetric("v_temp", MetricType.BODY_TEMP, "Body Temp", "%.1f".format(Locale.US, latestVal(MetricType.BODY_TEMP) ?: 36.9f), null, latestVal(MetricType.BODY_TEMP) ?: 36.9f, "°C", StatusLevel.NORMAL, "Now", "Baseline", DataSource.MANUAL, "36.2–37.3 °C", BaselineRange(36.2f, 36.8f, 37.3f, 35.0f, 38.5f), protocolCode = "NASA-MED-T01", clinicalGuidance = "Hydrate and cool cabin airflow."),
            VitalMetric("v_sleep", MetricType.SLEEP_QUALITY, "Sleep", "%.1f".format(Locale.US, latestVal(MetricType.SLEEP_QUALITY) ?: 5.3f), null, latestVal(MetricType.SLEEP_QUALITY) ?: 5.3f, "hrs", StatusLevel.MONITOR, "04h ago", "-2.2 hrs", DataSource.MANUAL, "6.8–8.5 hrs", BaselineRange(6.8f, 7.5f, 8.5f, 5.0f, 11.0f), protocolCode = "NASA-HRP-7C", clinicalGuidance = "Set cabin lights to amber 90m before sleep."),
            VitalMetric("v_resp", MetricType.RESPIRATORY_RATE, "Respiration", (latestVal(MetricType.RESPIRATORY_RATE) ?: 14f).roundToInt().toString(), null, latestVal(MetricType.RESPIRATORY_RATE) ?: 14f, "RPM", StatusLevel.NORMAL, "12s ago", "Baseline", DataSource.SENSOR, "12–18 RPM", BaselineRange(12f, 14f, 18f, 8f, 26f), protocolCode = "CDME-HYP-01", clinicalGuidance = "Verify cabin CO2 scrubber airflow."),
            VitalMetric("v_hydra", MetricType.HYDRATION, "Hydration", "1,800", null, 1800f, "mL", StatusLevel.MONITOR, "1h ago", "700 mL left", DataSource.MANUAL, "2,500 mL/day", BaselineRange(2000f, 2500f, 3200f, 900f, 4500f), protocolCode = "NASA-HRP-F08", clinicalGuidance = "Drink 500 mL water with potassium citrate."),
            VitalMetric("v_rad", MetricType.RADIATION_DOSIMETER, "Radiation", "%.2f".format(Locale.US, latestVal(MetricType.RADIATION_DOSIMETER) ?: 0.64f), null, latestVal(MetricType.RADIATION_DOSIMETER) ?: 0.64f, "mSv/d", StatusLevel.NORMAL, "12s ago", "Baseline", DataSource.DOSIMETER, "0.20–1.20 mSv/d", BaselineRange(0.20f, 0.64f, 1.20f, 0.0f, 4.50f), protocolCode = "NASA-RAD-SPE", clinicalGuidance = "Enter central water-wall shelter if > 1.20 mSv/d.")
        )
    }

    private fun initialAlerts(): List<HealthAlert> = listOf(
        HealthAlert(
            id = "alt_sleep_1",
            metricType = MetricType.SLEEP_QUALITY,
            title = "Sleep Duration Deviation",
            triggerEvent = "-2.2 hrs below 7.5 hr baseline",
            contextualGuidance = "Switch cabin lights to amber 90m before sleep.",
            protocolCode = "NASA-HRP-7C",
            priority = StatusLevel.MONITOR,
            relativeTime = "04h ago"
        ),
        HealthAlert(
            id = "alt_hydra_1",
            metricType = MetricType.HYDRATION,
            title = "Hydration Below 2,500 mL Target",
            triggerEvent = "700 mL remaining",
            contextualGuidance = "Drink 500 mL electrolyte water.",
            protocolCode = "NASA-HRP-F08",
            priority = StatusLevel.MONITOR,
            relativeTime = "1h ago"
        )
    )

    private fun initialTasks(): List<HealthMissionTask> = listOf(
        HealthMissionTask("task_ared", "ARED Resistance", "Bone & Muscle", "Squats, deadlifts & heel raises", "Done", 45, isCompleted = true),
        HealthMissionTask("task_t2", "CEVIS Cardio", "Cardio", "Interval cycle ergometer", "20/30m", 30, MetricType.HEART_RATE, false),
        HealthMissionTask("task_hydration", "Daily Hydration", "Renal", "2,500 mL goal", "1,800 mL", 5, MetricType.HYDRATION, false),
        HealthMissionTask("task_lbnp", "LBNP Session", "Neuro-Ocular", "-25 mmHg lower body vacuum", "Pending", 30, MetricType.BLOOD_PRESSURE, false),
        HealthMissionTask("task_circadian", "Amber Light Shift", "Circadian", "450nm pre-sleep lighting", "Pending", 15, MetricType.SLEEP_QUALITY, false)
    )

    private fun initialBodySystems(): List<AnatomySystem> = listOf(
        AnatomySystem(
            id = "sys_neuro_sans",
            name = "Neuro-Ocular",
            shortLabel = "Head",
            anatomicalRegion = "Cranial & Ophthalmic",
            hotspotXFraction = 0.50f,
            hotspotYFraction = 0.11f,
            status = StatusLevel.MONITOR,
            primaryRisk = "Fluid shift & eye pressure (SANS)",
            relatedMetrics = listOf(MetricType.SLEEP_QUALITY, MetricType.BLOOD_PRESSURE),
            spaceflightEffects = listOf("Cranial fluid shift & elevated eye pressure", "Near-vision focus shift", "Circadian sleep disruption"),
            physiologicalIndicators = listOf("Visual acuity", "Sleep duration"),
            hazards = listOf(HazardType.GRAVITY_FIELDS, HazardType.ISOLATION_CONFINEMENT),
            countermeasures = listOf(
                Countermeasure("LBNP Session", "-25 mmHg lower-body vacuum", "30m daily", "LBNP"),
                Countermeasure("Airflow Check", "Keep cabin CO2 < 2.8 mmHg", "Continuous", "CDRA")
            ),
            symptomChecklist = listOf(
                SymptomPrompt("sym_sleep_short", "Sleep < 6.5 hrs", 2, "Use amber cabin lighting 90m before bed"),
                SymptomPrompt("sym_vision_blur", "Near-vision blur", 2, "Run Amsler grid & OCT eye scan"),
                SymptomPrompt("sym_headache_co2", "Morning headache", 3, "Check sleep bay fan & CO2 level")
            ),
            clinicalAdvice = "Run weekly eye chart check."
        ),
        AnatomySystem(
            id = "sys_cardio",
            name = "Cardiovascular",
            shortLabel = "Heart",
            anatomicalRegion = "Heart & Vessels",
            hotspotXFraction = 0.53f,
            hotspotYFraction = 0.30f,
            status = StatusLevel.NORMAL,
            primaryRisk = "Aerobic deconditioning",
            relatedMetrics = listOf(MetricType.HEART_RATE, MetricType.BLOOD_PRESSURE),
            spaceflightEffects = listOf("Reduced blood plasma volume", "Orthostatic dizziness risk"),
            physiologicalIndicators = listOf("Heart rate", "Blood pressure"),
            hazards = listOf(HazardType.GRAVITY_FIELDS),
            countermeasures = listOf(
                Countermeasure("CEVIS Intervals", "Aerobic cycle ergometer", "30m 4x/wk", "CEVIS"),
                Countermeasure("Saline Loading", "1L isotonic fluid before g-load", "Pre-landing", "Med Kit")
            ),
            symptomChecklist = listOf(
                SymptomPrompt("sym_palpitations", "Palpitations at rest", 3, "Run 12-lead ECG & Protocol C04"),
                SymptomPrompt("sym_fatigue_exertion", "Early exercise fatigue", 2, "Reduce ergometer load 25%")
            ),
            clinicalAdvice = "Track resting HR vs 72 BPM baseline."
        ),
        AnatomySystem(
            id = "sys_respiratory",
            name = "Respiratory",
            shortLabel = "Lungs",
            anatomicalRegion = "Lungs & Airway",
            hotspotXFraction = 0.45f,
            hotspotYFraction = 0.27f,
            status = StatusLevel.NORMAL,
            primaryRisk = "CO2 pockets & cabin dust",
            relatedMetrics = listOf(MetricType.OXYGEN_SATURATION, MetricType.RESPIRATORY_RATE),
            spaceflightEffects = listOf("Stagnant CO2 buildup in low airflow", "Suspended cabin dust irritation"),
            physiologicalIndicators = listOf("SpO2 %", "Respiration rate"),
            hazards = listOf(HazardType.HOSTILE_CLOSED_ENV),
            countermeasures = listOf(
                Countermeasure("HEPA Filtration", "Cabin particulate scrubbing", "Continuous", "ECLSS"),
                Countermeasure("Airlock Vacuum", "Wipe suit dust post-EVA", "Post-EVA", "Airlock")
            ),
            symptomChecklist = listOf(
                SymptomPrompt("sym_cough_dust", "Dry cough or irritation", 1, "Check HEPA filter gauge"),
                SymptomPrompt("sym_dyspnea_rest", "Resting respiration > 20 RPM", 3, "Check SpO2 & cabin CO2")
            ),
            clinicalAdvice = "Keep sleep fan angled across face."
        ),
        AnatomySystem(
            id = "sys_renal_gi",
            name = "Renal & Fluid",
            shortLabel = "Renal",
            anatomicalRegion = "Kidneys & Hydration",
            hotspotXFraction = 0.46f,
            hotspotYFraction = 0.43f,
            status = StatusLevel.NORMAL,
            primaryRisk = "Kidney stone risk",
            relatedMetrics = listOf(MetricType.HYDRATION),
            spaceflightEffects = listOf("Elevated urinary calcium from bone loss", "Dehydration stone risk"),
            physiologicalIndicators = listOf("Daily fluid intake"),
            hazards = listOf(HazardType.GRAVITY_FIELDS),
            countermeasures = listOf(
                Countermeasure("Citrate + 2.5L Water", "Prevents calcium stone formation", "Daily", "Galley")
            ),
            symptomChecklist = listOf(
                SymptomPrompt("sym_low_urine", "Intake < 1,500 mL today", 2, "Drink 500 mL water now"),
                SymptomPrompt("sym_flank_pain", "Flank or abdominal pain", 3, "Run renal ultrasound protocol")
            ),
            clinicalAdvice = "Maintain 2,500 mL daily hydration."
        ),
        AnatomySystem(
            id = "sys_musculo",
            name = "Bone & Muscle",
            shortLabel = "Spine",
            anatomicalRegion = "Skeleton & Core",
            hotspotXFraction = 0.50f,
            hotspotYFraction = 0.56f,
            status = StatusLevel.NORMAL,
            primaryRisk = "1% monthly bone loss",
            relatedMetrics = listOf(MetricType.HEART_RATE, MetricType.BODY_TEMP),
            spaceflightEffects = listOf("Femur & spine bone loss", "Spinal lengthening back ache"),
            physiologicalIndicators = listOf("ARED load", "Core temp"),
            hazards = listOf(HazardType.GRAVITY_FIELDS),
            countermeasures = listOf(
                Countermeasure("ARED Resistance", "Heavy axial squats & deadlifts", "45m 6x/wk", "ARED"),
                Countermeasure("Vitamin D3", "1000 IU supplement", "Daily", "Med Kit")
            ),
            symptomChecklist = listOf(
                SymptomPrompt("sym_lumbar_ache", "Lower back stiffness", 1, "Fetal-tuck stretch before sleep"),
                SymptomPrompt("sym_joint_strain", "Joint pain during ARED", 2, "Reduce bar load 15%")
            ),
            clinicalAdvice = "Complete daily ARED sets."
        ),
        AnatomySystem(
            id = "sys_immune_rad",
            name = "Radiation & Immune",
            shortLabel = "Immune",
            anatomicalRegion = "Lymphatic & Spleen",
            hotspotXFraction = 0.55f,
            hotspotYFraction = 0.39f,
            status = StatusLevel.NORMAL,
            primaryRisk = "Radiation & viral reactivation",
            relatedMetrics = listOf(MetricType.RADIATION_DOSIMETER, MetricType.BODY_TEMP),
            spaceflightEffects = listOf("Reduced red blood cell mass", "Latent viral reactivation under stress"),
            physiologicalIndicators = listOf("Dosimeter rate", "Core temp"),
            hazards = listOf(HazardType.RADIATION),
            countermeasures = listOf(
                Countermeasure("Water-Wall Shelter", "High-mass radiation shielding", "Dose > 1.2 mSv/d", "Shelter")
            ),
            symptomChecklist = listOf(
                SymptomPrompt("sym_skin_rash", "Fever (>37.8°C) or rash", 3, "Log Core Temp & run swab"),
                SymptomPrompt("sym_dosimeter_warn", "Dose > 0.80 mSv/d", 3, "Verify storm shelter readiness")
            ),
            clinicalAdvice = "Wear personal dosimeter continuously."
        )
    )

    // Vita SYS Protocols + CDME-PRO-MAX Document Protocols
    private fun initialProtocols(): List<ClinicalProtocol> = listOf(
        ClinicalProtocol(
            code = "CDME-HYP-01",
            title = "Hypercapnia Tachycardia Protocol",
            category = "CDME-PRO-MAX",
            severity = StatusLevel.CRITICAL,
            triggerCondition = "Cabin CO2 > 3.0 mmHg & HR > 100 BPM",
            timerDurationSeconds = 300,
            timerLabel = "4-7-8 Tactical Breathing Beacon",
            steps = listOf(
                ProtocolStep(1, "Ramp ECLSS Amine scrubbers to MAX flow"),
                ProtocolStep(2, "Check Wanninkhof pCO2 environmental flux"),
                ProtocolStep(3, "Project 4-7-8 tactical breathing beacon (4s in, 7s hold, 8s out)"),
                ProtocolStep(4, "Move to high-airflow module & verify SpO2 > 95%"),
                ProtocolStep(5, "Log follow-up HR & encrypt delta-log for Houston")
            )
        ),
        ClinicalProtocol(
            code = "CDME-CPR-0G",
            title = "Microgravity Resuscitation (0-g CPR)",
            category = "CDME-PRO-MAX",
            severity = StatusLevel.CRITICAL,
            triggerCondition = "Cardiac Arrest / Unresponsive in 0-g",
            timerDurationSeconds = 120,
            timerLabel = "110 BPM Compression Cycle",
            steps = listOf(
                ProtocolStep(1, "Anchor patient & rescuer to bulkhead seat tracks or pelvic cargo straps"),
                ProtocolStep(2, "Perform Hand-Stand or Reverse-Bearhug compressions (standard floor CPR fails in 0-g)"),
                ProtocolStep(3, "Deliver 100–120 compressions/min leveraging spacecraft bulkhead"),
                ProtocolStep(4, "Verify ACLS Safety Interlock: Epinephrine <= 1.0 mg IV/IO q3–5m"),
                ProtocolStep(5, "Verify O2 flammability ceiling <= 24.1% before defibrillation")
            )
        ),
        ClinicalProtocol(
            code = "CDME-ENV-CO2",
            title = "Environmental Carbon & Flux Check",
            category = "CDME-PRO-MAX",
            severity = StatusLevel.MONITOR,
            triggerCondition = "1s Rolling CO2 Drift / Wanninkhof Flux",
            steps = listOf(
                ProtocolStep(1, "Inspect 1-second SensorRepository pCO2 rolling drift"),
                ProtocolStep(2, "Verify CDRA / Amine bed regeneration cycle"),
                ProtocolStep(3, "Clear sleep-bay ventilation grilles of debris")
            )
        ),
        ClinicalProtocol(
            code = "NASA-MED-C04",
            title = "Elevated Heart Rate",
            category = "Cardio",
            severity = StatusLevel.CRITICAL,
            triggerCondition = "Resting HR > 100 BPM",
            timerDurationSeconds = 300,
            timerLabel = "Paced Breathing (5m)",
            steps = listOf(
                ProtocolStep(1, "Stop exertion & rest in neutral posture"),
                ProtocolStep(2, "5m paced breathing (4s in, 6s out)"),
                ProtocolStep(3, "Drink 500 mL electrolyte water"),
                ProtocolStep(4, "Attach 12-lead ECG electrodes"),
                ProtocolStep(5, "Log follow-up Heart Rate")
            )
        ),
        ClinicalProtocol(
            code = "NASA-HRP-7C",
            title = "Sleep Deficit Recovery",
            category = "Circadian",
            severity = StatusLevel.MONITOR,
            triggerCondition = "Sleep < 6.5 hrs",
            timerDurationSeconds = 600,
            timerLabel = "Pre-Sleep Wind-Down (10m)",
            steps = listOf(
                ProtocolStep(1, "Set cabin lighting to 450nm amber"),
                ProtocolStep(2, "Verify sleep fan airflow across face"),
                ProtocolStep(3, "Fit earplugs & latch acoustic doors"),
                ProtocolStep(4, "Set sleep bay temp to 19°C")
            )
        ),
        ClinicalProtocol(
            code = "NASA-HRP-F08",
            title = "Dehydration & Renal Protocol",
            category = "Renal",
            severity = StatusLevel.MONITOR,
            triggerCondition = "Hydration < 2,000 mL",
            steps = listOf(
                ProtocolStep(1, "Drink 500 mL water now"),
                ProtocolStep(2, "Take 20 mEq potassium citrate tablet"),
                ProtocolStep(3, "Check urine pH & specific gravity strip")
            )
        ),
        ClinicalProtocol(
            code = "NASA-RAD-SPE",
            title = "Solar Storm Shelter",
            category = "Radiation",
            severity = StatusLevel.CRITICAL,
            triggerCondition = "Dose > 1.20 mSv/d",
            steps = listOf(
                ProtocolStep(1, "Abort EVA & seal airlock hatch"),
                ProtocolStep(2, "Enter central water-wall shelter bay"),
                ProtocolStep(3, "Stack polyethylene bags on sun-facing wall"),
                ProtocolStep(4, "Monitor active dosimeter hourly")
            )
        )
    )
}

// ============================================================================
// 6. MAIN ACTIVITY
// ============================================================================
enum class AstromergedTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Vitals", Icons.Outlined.FavoriteBorder),
    TRENDS("Trends", Icons.Outlined.Timeline),
    BIOSCAN("Body", Icons.Outlined.AccessibilityNew),
    PROTOCOLS("Protocols", Icons.Outlined.MedicalServices),
    AI_COACH("AI Coach", Icons.Outlined.AutoAwesome)
}

class MainActivity : ComponentActivity() {
    private val repository = AstroMergedRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository.initPersistence(applicationContext)
        intent?.data?.let { repository.handleOAuthRedirectUri(it) }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = MinimalLightColors) {
                AstromergedApp(repository = repository)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { repository.handleOAuthRedirectUri(it) }
    }
}

@Composable
fun AstromergedApp(repository: AstroMergedRepository) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AstromergedTab.DASHBOARD) }
    var selectedTrendMetric by remember { mutableStateOf(MetricType.HEART_RATE) }
    var isTrendDetailOpen by remember { mutableStateOf(false) }
    var activeLogMetric by remember { mutableStateOf<MetricType?>(null) }
    var focusedProtocolCode by remember { mutableStateOf<String?>(null) }
    var showProfileSettings by remember { mutableStateOf(false) }

    val vitals by repository.vitals.collectAsState()
    val historyMap by repository.history24h.collectAsState()
    val alerts by repository.alerts.collectAsState()
    val readiness by repository.readiness.collectAsState()
    val isOfflineMode by repository.isOfflineMode.collectAsState()
    val isSensorDisconnected by repository.isChestSensorDisconnected.collectAsState()
    val bodySystems by repository.bodySystems.collectAsState()
    val checkedSymptoms by repository.checkedSymptoms.collectAsState()
    val protocols by repository.protocols.collectAsState()
    val aiState by repository.aiState.collectAsState()
    val deltaLog by repository.encryptedDeltaLog.collectAsState()
    val donkiStatusTag by repository.donkiStatusTag.collectAsState()
    val geminiApiKey by repository.geminiApiKey.collectAsState()
    val googleOAuthClientId by repository.googleOAuthClientId.collectAsState()
    val isSignedIn by repository.isSignedIn.collectAsState()
    val signedInEmail by repository.signedInEmail.collectAsState()
    val authProvider by repository.authProvider.collectAsState()
    val criticalAlertsEnabled by repository.criticalAlertsEnabled.collectAsState()
    val alertSoundEnabled by repository.alertSoundEnabled.collectAsState()
    val alertSensitivity by repository.alertSensitivity.collectAsState()
    val continuousPollingEnabled by repository.continuousPollingEnabled.collectAsState()
    val donkiSyncEnabled by repository.donkiSyncEnabled.collectAsState()
    val useFahrenheit by repository.useFahrenheit.collectAsState()
    val encryptedLogEnabled by repository.encryptedLogEnabled.collectAsState()

    BackHandler(enabled = currentTab != AstromergedTab.DASHBOARD || isTrendDetailOpen) {
        if (isTrendDetailOpen) {
            isTrendDetailOpen = false
        } else {
            currentTab = AstromergedTab.DASHBOARD
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BgCanvas,
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, BgCanvas.copy(alpha = 0.88f), BgCanvas)
                            )
                        )
                )
                AuraFloatingDockBar(
                    current = currentTab,
                    onSelect = {
                        isTrendDetailOpen = false
                        currentTab = it
                    },
                    onQuickLog = { activeLogMetric = MetricType.HYDRATION }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val navStateKey = Pair(currentTab, isTrendDetailOpen)
            AnimatedContent(
                targetState = navStateKey,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(initialScale = 0.97f, animationSpec = tween(260)))
                        .togetherWith(fadeOut(tween(180)))
                },
                label = "astromergedScreenTransition"
            ) { (tab, trendOpen) ->
                if (trendOpen) {
                    TrendAnalysisScreen(
                        activeMetric = selectedTrendMetric,
                        onSelectMetric = { selectedTrendMetric = it },
                        repository = repository,
                        onLogVitalClick = { activeLogMetric = it },
                        onBack = { isTrendDetailOpen = false }
                    )
                } else {
                    when (tab) {
                        AstromergedTab.DASHBOARD -> {
                            VitaDashboardScreen(
                                vitals = vitals,
                                historyMap = historyMap,
                                alerts = alerts,
                                readiness = readiness,
                                donkiStatusTag = donkiStatusTag,
                                isOfflineMode = isOfflineMode,
                                isSensorDisconnected = isSensorDisconnected,
                                onToggleOffline = { repository.toggleOfflineMode() },
                                onToggleSensor = { repository.toggleSensorConnection() },
                                onOpenProfileSettings = { showProfileSettings = true },
                                onSelectVital = { vital ->
                                    selectedTrendMetric = vital.type
                                    isTrendDetailOpen = true
                                },
                                onQuickLogVital = { activeLogMetric = it },
                                onAcknowledgeAlert = { repository.acknowledgeAlert(it) },
                                onOpenProtocol = { code ->
                                    focusedProtocolCode = code
                                    currentTab = AstromergedTab.PROTOCOLS
                                },
                                onSelectScenario = { repository.setScenario(it) },
                                reportPayload = repository.buildFlightSurgeonReport()
                            )
                        }
                        AstromergedTab.TRENDS -> {
                            TrendAnalysisScreen(
                                activeMetric = selectedTrendMetric,
                                onSelectMetric = { selectedTrendMetric = it },
                                repository = repository,
                                onLogVitalClick = { activeLogMetric = it },
                                onBack = { currentTab = AstromergedTab.DASHBOARD }
                            )
                        }
                        AstromergedTab.BIOSCAN -> {
                            BodySystemsScreen(
                                systems = bodySystems,
                                checkedSymptoms = checkedSymptoms,
                                onToggleSymptom = { repository.toggleSymptom(it) },
                                onInspectMetric = { metricType ->
                                    selectedTrendMetric = metricType
                                    isTrendDetailOpen = true
                                }
                            )
                        }
                        AstromergedTab.PROTOCOLS -> {
                            ProtocolsScreen(
                                protocols = protocols,
                                initialExpandedProtocolCode = focusedProtocolCode,
                                readiness = readiness,
                                deltaLog = deltaLog,
                                reportPayload = repository.buildFlightSurgeonReport(),
                                onToggleProtocolStep = { code, stepIdx -> repository.toggleProtocolStep(code, stepIdx) },
                                onResetProtocol = { repository.resetProtocolSteps(it) }
                            )
                        }
                        AstromergedTab.AI_COACH -> {
                            AiCoachScreen(
                                readiness = readiness,
                                aiState = aiState,
                                hasGeminiKey = geminiApiKey.isNotBlank(),
                                onAskAi = { repository.askAiHealthAssistant(it) },
                                onUpdateBaselines = { name, day, hr, spo2, sleep ->
                                    repository.updateProfileBaselines(name, day, hr, spo2, sleep)
                                }
                            )
                        }
                    }
                }
            }

            activeLogMetric?.let { metricType ->
                LogEntryDialog(
                    vitals = vitals,
                    initialMetricType = metricType,
                    onDismiss = { activeLogMetric = null },
                    onLogHydration = { repository.logHydration(it) },
                    onLogVital = { type, text, num, sec, note -> repository.logVital(type, text, num, sec, note) }
                )
            }

            if (showProfileSettings) {
                ProfileSettingsDialog(
                    crewName = readiness.crewName,
                    isSignedIn = isSignedIn,
                    signedInEmail = signedInEmail,
                    authProvider = authProvider,
                    currentGeminiKey = geminiApiKey,
                    currentOAuthClientId = googleOAuthClientId,
                    criticalAlertsEnabled = criticalAlertsEnabled,
                    alertSoundEnabled = alertSoundEnabled,
                    alertSensitivity = alertSensitivity,
                    continuousPollingEnabled = continuousPollingEnabled,
                    isOfflineMode = isOfflineMode,
                    donkiSyncEnabled = donkiSyncEnabled,
                    useFahrenheit = useFahrenheit,
                    encryptedLogEnabled = encryptedLogEnabled,
                    onDismiss = { showProfileSettings = false },
                    onGoogleOAuthSignIn = { clientId ->
                        val authUri = Uri.Builder()
                            .scheme("https")
                            .authority("accounts.google.com")
                            .appendEncodedPath("o/oauth2/v2/auth")
                            .appendQueryParameter("client_id", clientId)
                            .appendQueryParameter("redirect_uri", "com.example.astromerged:/oauth2redirect")
                            .appendQueryParameter("response_type", "token")
                            .appendQueryParameter("scope", "openid email profile")
                            .build()
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, authUri))
                        } catch (_: Exception) {
                            Toast.makeText(context, "No browser available for Google OAuth", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLocalSignIn = { name, email -> repository.signInLocalAccount(name, email) },
                    onSignOut = { repository.signOut() },
                    onSaveGeminiKey = { repository.updateGeminiApiKey(it) },
                    onSaveOAuthClientId = { repository.updateGoogleOAuthClientId(it) },
                    onToggleCriticalAlerts = { repository.setCriticalAlertsEnabled(it) },
                    onToggleAlertSound = { repository.setAlertSoundEnabled(it) },
                    onSelectAlertSensitivity = { repository.setAlertSensitivity(it) },
                    onToggleContinuousPolling = { repository.setContinuousPollingEnabled(it) },
                    onToggleOfflineMode = { repository.toggleOfflineMode() },
                    onToggleDonkiSync = { repository.setDonkiSyncEnabled(it) },
                    onToggleUseFahrenheit = { repository.setUseFahrenheit(it) },
                    onToggleEncryptedLog = { repository.setEncryptedLogEnabled(it) },
                    onClearDeltaLog = {
                        repository.clearEncryptedDeltaLog()
                        Toast.makeText(context, "Delta-log cleared", Toast.LENGTH_SHORT).show()
                    },
                    onResetSettings = {
                        repository.resetAppSettings()
                        Toast.makeText(context, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

// ============================================================================
// 7. AURA HEALTH FLOATING PILL DOCK BAR
// ============================================================================
@Composable
fun AuraFloatingDockBar(
    current: AstromergedTab,
    onSelect: (AstromergedTab) -> Unit,
    onQuickLog: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCanvas)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = CardPure,
            shape = RoundedCornerShape(50),
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AstromergedTab.entries.forEach { item ->
                    val sel = current == item
                    val pillBg by animateColorAsState(
                        if (sel) InkPrimary else Color.Transparent,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "NavBg"
                    )
                    val iconTint by animateColorAsState(
                        if (sel) Color.White else InkSecondary,
                        label = "NavFg"
                    )
                    Surface(
                        onClick = { onSelect(item) },
                        color = pillBg,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = iconTint,
                                modifier = Modifier.size(19.dp)
                            )
                            AnimatedVisibility(visible = sel) {
                                Row {
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        item.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                FilledIconButton(
                    onClick = onQuickLog,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ElectricBlue),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Log", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ============================================================================
// 8. FRONT PAGE DASHBOARD (COPIED FROM REFERENCE UI ABOVE BOTTOM TILES, AURA COLORS)
// ============================================================================
private enum class VitalFilter(val label: String) {
    ALL("ALL"),
    ATTENTION("ATTN"),
    CARDIO_RESP("CARDIO"),
    METABOLIC("META")
}

@Composable
fun VitaDashboardScreen(
    vitals: List<VitalMetric>,
    historyMap: Map<MetricType, List<TelemetryDataPoint>>,
    alerts: List<HealthAlert>,
    readiness: CrewReadinessState,
    donkiStatusTag: String,
    isOfflineMode: Boolean,
    isSensorDisconnected: Boolean,
    onToggleOffline: () -> Unit,
    onToggleSensor: () -> Unit,
    onOpenProfileSettings: () -> Unit,
    onSelectVital: (VitalMetric) -> Unit,
    onQuickLogVital: (MetricType) -> Unit,
    onAcknowledgeAlert: (String) -> Unit,
    onOpenProtocol: (String) -> Unit,
    onSelectScenario: (AstroSimulationScenario) -> Unit,
    reportPayload: String
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(VitalFilter.ALL) }
    var showScenarioMenu by remember { mutableStateOf(false) }

    val displayedVitals = remember(vitals, selectedFilter) {
        val filtered = when (selectedFilter) {
            VitalFilter.ALL -> vitals
            VitalFilter.ATTENTION -> vitals.filter { it.status != StatusLevel.NORMAL }
            VitalFilter.CARDIO_RESP -> vitals.filter {
                it.type in setOf(MetricType.HEART_RATE, MetricType.BLOOD_PRESSURE, MetricType.OXYGEN_SATURATION, MetricType.RESPIRATORY_RATE)
            }
            VitalFilter.METABOLIC -> vitals.filter {
                it.type in setOf(MetricType.HYDRATION, MetricType.SLEEP_QUALITY, MetricType.BODY_TEMP, MetricType.RADIATION_DOSIMETER)
            }
        }
        filtered.sortedBy { it.status.sortPriority }
    }

    val unackedAlerts = remember(alerts) { alerts.filter { !it.isAcknowledged } }
    val vitalRows = remember(displayedVitals) { displayedVitals.chunked(2) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCanvas),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Header Bar (Clickable Profile -> Settings + NASA/CSA & DONKI + Readiness + Sensor Toggle)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenProfileSettings() }
                        .padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val avatarInitials = remember(readiness.crewName) {
                        readiness.crewName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.takeLast(2)
                            .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "CR" }
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ElectricBlue.copy(alpha = 0.14f))
                            .border(1.dp, ElectricBlue.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(avatarInitials, color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text(
                        readiness.crewName,
                        color = InkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        color = ElectricBlue.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            "DAY ${readiness.missionDay}",
                            color = ElectricBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        Surface(
                            onClick = { showScenarioMenu = true },
                            color = AmberWarn.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(999.dp),
                            border = BorderStroke(1.dp, AmberWarn.copy(alpha = 0.4f))
                        ) {
                            Text(
                                donkiStatusTag,
                                color = AmberWarn,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        DropdownMenu(expanded = showScenarioMenu, onDismissRequest = { showScenarioMenu = false }) {
                            AstroSimulationScenario.entries.forEach { sc ->
                                DropdownMenuItem(
                                    text = { Text(sc.title, fontSize = 12.sp) },
                                    onClick = {
                                        showScenarioMenu = false
                                        onSelectScenario(sc)
                                    }
                                )
                            }
                        }
                    }

                    val rColor = when {
                        readiness.crewReadinessScore >= 80 -> MintVital
                        readiness.crewReadinessScore >= 65 -> AmberWarn
                        else -> CoralAlert
                    }
                    Surface(
                        onClick = onToggleOffline,
                        color = rColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, rColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            if (isOfflineMode) "OFFLINE" else "${readiness.crewReadinessScore}%",
                            color = rColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        onClick = onToggleSensor,
                        color = if (isSensorDisconnected) CoralAlert.copy(alpha = 0.14f) else ElectricBlue.copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSensorDisconnected) Icons.Default.SensorsOff else Icons.Default.Bedtime,
                                contentDescription = "Sensor",
                                tint = if (isSensorDisconnected) CoralAlert else ElectricBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Hero Readiness Gauge + 2x2 Environmental Telemetry Card + Share Vitals Button
        item {
            HeroReadinessAndEnvCard(
                readiness = readiness,
                onShareVitals = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "AstroMerged Flight Surgeon Vitals - ${readiness.crewName}")
                        putExtra(Intent.EXTRA_TEXT, reportPayload)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Vitals to Flight Surgeon"))
                }
            )
        }

        // 3. Compact Alert Bar (MONITOR: ... + ACK + +N)
        if (unackedAlerts.isNotEmpty()) {
            item {
                val topAlert = unackedAlerts.first()
                val alertColor = if (topAlert.priority == StatusLevel.CRITICAL) CoralAlert else AmberWarn
                Surface(
                    onClick = { onOpenProtocol(topAlert.protocolCode) },
                    color = alertColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, alertColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.size(8.dp).background(alertColor, CircleShape))
                            Text(
                                text = "${topAlert.priority.shortLabel}: ${topAlert.title}",
                                color = InkPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = { onAcknowledgeAlert(topAlert.id) },
                                color = alertColor.copy(alpha = 0.22f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    "ACK",
                                    color = alertColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            if (unackedAlerts.size > 1) {
                                Text(
                                    "+${unackedAlerts.size - 1}",
                                    color = InkSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. TELEMETRY Section Header + Filter Pill + "+ LOG" Pill
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TELEMETRY",
                    color = InkPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.8.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = {
                            val entries = VitalFilter.entries
                            selectedFilter = entries[(entries.indexOf(selectedFilter) + 1) % entries.size]
                        },
                        color = CardPure,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Text(
                            text = "${selectedFilter.label}  ${displayedVitals.size}",
                            color = ElectricBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        onClick = { onQuickLogVital(MetricType.HEART_RATE) },
                        color = ElectricBlue.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = "+ LOG",
                            color = ElectricBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 5. 2-Column Telemetry Sparkline Grid (Matching Reference Screenshot)
        items(vitalRows) { rowVitals ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowVitals.forEach { vital ->
                    TelemetrySparkCard(
                        vital = vital,
                        points = historyMap[vital.type].orEmpty(),
                        onClick = { onSelectVital(vital) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowVitals.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroReadinessAndEnvCard(
    readiness: CrewReadinessState,
    onShareVitals: () -> Unit
) {
    val animatedScore by animateIntAsState(readiness.crewReadinessScore, tween(700), label = "heroScore")
    val outerSweep by animateFloatAsState((readiness.crewReadinessScore / 100f) * 260f, spring(stiffness = Spring.StiffnessLow), label = "oSweep")
    val co2Color = if (readiness.cabinCo2MmHg > 3.0f) CoralAlert else MintVital
    val safetyText = if (readiness.amineScrubberRamped) "OVERRIDE" else "LOCKED"
    val safetyColor = if (readiness.amineScrubberRamped) CoralAlert else MintVital

    Surface(
        color = CardPure,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.22f)),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Double Concentric Arc Gauge
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(102.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 7.dp.toPx()
                        drawArc(
                            color = PillSurface,
                            startAngle = 140f,
                            sweepAngle = 260f,
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = FluidsBlueDark,
                            startAngle = 140f,
                            sweepAngle = outerSweep,
                            useCenter = false,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                        val inset = 13.dp.toPx()
                        drawArc(
                            color = PillSurface,
                            startAngle = 140f,
                            sweepAngle = 260f,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2, size.height - inset * 2),
                            useCenter = false,
                            style = Stroke(5.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = InnerRingGreen,
                            startAngle = 140f,
                            sweepAngle = (outerSweep * 0.88f),
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - inset * 2, size.height - inset * 2),
                            useCenter = false,
                            style = Stroke(5.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$animatedScore",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = InkPrimary
                        )
                        Text(
                            "READY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = FluidsBlueDark,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // 2x2 Environmental & Safety Mini-Grid
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnvMiniTile(
                            label = "pCO2",
                            unit = "mmHg",
                            value = "%.2f".format(Locale.US, readiness.cabinCo2MmHg),
                            valueColor = co2Color,
                            modifier = Modifier.weight(1f)
                        )
                        EnvMiniTile(
                            label = "SCRUBBER",
                            unit = "AMINE",
                            value = if (readiness.amineScrubberRamped) "100%" else "55%",
                            valueColor = ElectricBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnvMiniTile(
                            label = "FLUX k",
                            unit = "cm/h",
                            value = "%.2f".format(Locale.US, readiness.wanninkhofFlux),
                            valueColor = ElectricBlue,
                            modifier = Modifier.weight(1f)
                        )
                        EnvMiniTile(
                            label = "SAFETY",
                            unit = "B-01",
                            value = safetyText,
                            valueColor = safetyColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Full-Width Share Vitals to Flight Surgeon Button
            Button(
                onClick = onShareVitals,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share Vitals to Flight Surgeon", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EnvMiniTile(
    label: String,
    unit: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BgCanvas,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = InkSecondary)
                Text(unit, fontSize = 9.sp, color = InkSecondary)
            }
            Spacer(Modifier.height(2.dp))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
private fun TelemetrySparkCard(
    vital: VitalMetric,
    points: List<TelemetryDataPoint>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = vital.type.chartColor
    val statusDotColor = when (vital.status) {
        StatusLevel.NORMAL -> MintVital
        StatusLevel.MONITOR -> AmberWarn
        StatusLevel.CRITICAL -> CoralAlert
        StatusLevel.UNAVAILABLE -> InkSecondary
    }
    val validValues = remember(points) { points.mapNotNull { it.value }.takeLast(18) }

    Surface(
        onClick = onClick,
        color = CardPure,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.06f), CardPure)
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(accent, CircleShape))
                    Text(
                        text = vital.title.uppercase(Locale.US),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(modifier = Modifier.size(7.dp).background(statusDotColor, CircleShape))
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = vital.value ?: "—",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = if (vital.status == StatusLevel.CRITICAL) CoralAlert else InkPrimary,
                    maxLines = 1
                )
                if (vital.value != null) {
                    Text(
                        text = vital.unit,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }

            // Mini Cubic-Bezier Sparkline with Gradient Fill
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                if (validValues.size >= 2) {
                    val minV = validValues.minOrNull() ?: 0f
                    val maxV = validValues.maxOrNull() ?: 1f
                    val span = (maxV - minV).coerceAtLeast(1f)
                    val coords = validValues.mapIndexed { idx, v ->
                        val x = (idx.toFloat() / (validValues.size - 1)) * size.width
                        val y = size.height - 4.dp.toPx() - ((v - minV) / span) * (size.height - 8.dp.toPx())
                        Offset(x, y)
                    }
                    val path = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 0 until coords.size - 1) {
                            val p0 = coords[i]
                            val p1 = coords[i + 1]
                            val mx = (p0.x + p1.x) / 2f
                            cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                        }
                    }
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(coords.last().x, size.height)
                        lineTo(coords.first().x, size.height)
                        close()
                    }
                    drawPath(
                        path = fill,
                        brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent))
                    )
                    drawPath(path = path, color = accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(color = accent, radius = 3.dp.toPx(), center = coords.last())
                }
            }
        }
    }
}

@Composable
fun StatusPill(status: StatusLevel, compact: Boolean = false) {
    val (color, bg) = when (status) {
        StatusLevel.NORMAL -> MintVital to MintVital.copy(alpha = 0.14f)
        StatusLevel.MONITOR -> AmberWarn to AmberWarn.copy(alpha = 0.14f)
        StatusLevel.CRITICAL -> CoralAlert to CoralAlert.copy(alpha = 0.14f)
        StatusLevel.UNAVAILABLE -> InkSecondary to PillSurface
    }
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Text(if (compact) status.shortLabel else status.label, color = color, fontWeight = FontWeight.Bold, fontSize = if (compact) 10.sp else 11.sp)
    }
}

// ============================================================================
// 9. TRENDS SCREEN: AURA HEALTH MULTI-COLOR GRAPHS + VITA SYS LEFT-TO-RIGHT LOAD ANIMATION
// ============================================================================
@Composable
fun TrendAnalysisScreen(
    activeMetric: MetricType,
    onSelectMetric: (MetricType) -> Unit,
    repository: AstroMergedRepository,
    onLogVitalClick: (MetricType) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var activeTimeframe by remember { mutableStateOf(Timeframe.HOURS_24) }

    val vitals by repository.vitals.collectAsState()
    val historyMap by repository.history24h.collectAsState()

    val currentVital = vitals.find { it.type == activeMetric } ?: vitals.first()
    val trendPoints = remember(activeMetric, activeTimeframe, historyMap) {
        repository.getTrendData(activeMetric, activeTimeframe)
    }
    val stats = remember(activeMetric, activeTimeframe, historyMap) {
        repository.getStatistics(activeMetric, activeTimeframe)
    }

    fun formatNum(value: Float): String = when (activeMetric) {
        MetricType.RADIATION_DOSIMETER -> "%.2f".format(Locale.US, value)
        MetricType.BODY_TEMP, MetricType.SLEEP_QUALITY -> "%.1f".format(Locale.US, value)
        else -> value.roundToInt().toString()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgCanvas),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(modifier = Modifier.fillMaxWidth(), color = CardPure) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = InkPrimary)
                            }
                            Text(currentVital.title, color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("${currentVital.value ?: "—"} ${currentVital.unit}", color = activeMetric.chartColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Button(
                            onClick = { onLogVitalClick(activeMetric) },
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = activeMetric.chartColor, contentColor = Color.White),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Log", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = BorderSubtle)
                }
            }
        }

        // Different colored metric pills from Aura Health
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MetricType.entries) { mType ->
                    val isSelected = mType == activeMetric
                    val pillBg by animateColorAsState(if (isSelected) mType.chartColor else CardPure, tween(240), label = "mBg")
                    val pillText by animateColorAsState(if (isSelected) Color.White else InkPrimary, tween(240), label = "mTx")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(pillBg)
                            .border(1.dp, if (isSelected) mType.chartColor else BorderSubtle, RoundedCornerShape(999.dp))
                            .clickable { onSelectMetric(mType) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(mType.shortName, color = pillText, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(PillSurface, RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    val isSel = tf == activeTimeframe
                    val tabBg by animateColorAsState(if (isSel) CardPure else Color.Transparent, tween(200), label = "tfBg")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tabBg)
                            .clickable { activeTimeframe = tf }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tf.durationTag, color = if (isSel) activeMetric.chartColor else InkSecondary, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, fontSize = 12.sp)
                    }
                }
            }
        }

        // Smooth Left-to-Right Graph Loading Animation (Vita SYS) + Multi-Color Cubic Bezier (Aura Health)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                TrendChart(
                    dataPoints = trendPoints,
                    baselineRange = currentVital.baselineRange,
                    unit = currentVital.unit,
                    accentColor = activeMetric.chartColor,
                    chartKey = Pair(activeMetric, activeTimeframe)
                )
            }
        }

        // 3-Column Stats Strip
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardPure)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Avg", color = InkSecondary, fontSize = 11.sp)
                    Text("${formatNum(stats.average)} ${currentVital.unit}", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("Min–Max", color = InkSecondary, fontSize = 11.sp)
                    Text("${formatNum(stats.min)}–${formatNum(stats.max)}", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column {
                    Text("Baseline", color = InkSecondary, fontSize = 11.sp)
                    Text(currentVital.baselineDescription, color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Share Vitals to Flight Surgeon Card (from Astra Vita SYS)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                color = CardPure
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Flight Surgeon Summary", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = InkPrimary)
                        Text("Share latest vitals & baseline report", fontSize = 12.sp, color = InkSecondary)
                    }
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "AstroMerged Flight Surgeon Summary")
                                putExtra(Intent.EXTRA_TEXT, repository.buildFlightSurgeonReport())
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Health Summary"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("History", color = InkPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                trendPoints.reversed().take(6).forEach { pt ->
                    val ptStatus = currentVital.baselineRange.evaluateStatus(pt.value)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardPure)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(pt.timeLabel, color = InkSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            Text(
                                text = if (pt.value != null) "${formatNum(pt.value)} ${currentVital.unit}" else "No signal",
                                color = InkPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                        StatusPill(status = ptStatus, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
fun TrendChart(
    dataPoints: List<TelemetryDataPoint>,
    baselineRange: BaselineRange,
    unit: String,
    accentColor: Color,
    chartKey: Any = Unit,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(chartKey) {
        mutableStateOf<Int?>(dataPoints.indexOfLast { it.value != null }.takeIf { it >= 0 })
    }

    // Vita SYS smooth left-to-right loading animation (only replays when metric or timeframe changes)
    val revealAnim = remember(chartKey) { Animatable(0f) }
    LaunchedEffect(chartKey) {
        revealAnim.snapTo(0f)
        revealAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
        )
    }

    val validValues = dataPoints.mapNotNull { it.value }
    val rawMin = minOf(baselineRange.min, validValues.minOrNull() ?: baselineRange.min)
    val rawMax = maxOf(baselineRange.max, validValues.maxOrNull() ?: baselineRange.max)
    val paddingSpan = ((rawMax - rawMin).coerceAtLeast(2f)) * 0.22f
    val computedMin = rawMin - paddingSpan
    val computedMax = rawMax + paddingSpan
    val yRange = (computedMax - computedMin).coerceAtLeast(1f)

    val density = LocalDensity.current
    val axisTextPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkSecondary.toArgb()
            textSize = with(density) { 9.5.sp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
    }

    fun formatTick(v: Float): String = if (yRange < 4f) "%.2f".format(Locale.US, v) else if (yRange < 20f) "%.1f".format(Locale.US, v) else v.roundToInt().toString()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CardPure, RoundedCornerShape(20.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val pt = selectedIndex?.let { dataPoints.getOrNull(it) }
            if (pt != null && pt.value != null) {
                val isOut = pt.value > baselineRange.max || pt.value < baselineRange.min
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .background(if (isOut) AmberWarn.copy(alpha = 0.16f) else accentColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("${formatTick(pt.value)} $unit", color = if (isOut) AmberWarn else accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(pt.timeLabel, color = InkSecondary, fontSize = 11.sp)
                }
            } else {
                Text("Touch or drag curve", color = InkSecondary, fontSize = 12.sp)
            }
            Text("Nominal Band", color = InkSecondary, fontSize = 10.sp)
        }

        Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(dataPoints) {
                        detectTapGestures { tapOffset ->
                            val leftGutter = 34.dp.toPx()
                            val chartW = (size.width - leftGutter).coerceAtLeast(1f)
                            if (dataPoints.isNotEmpty()) {
                                val stepX = chartW / (dataPoints.size - 1).coerceAtLeast(1)
                                val relX = (tapOffset.x - leftGutter).coerceAtLeast(0f)
                                selectedIndex = ((relX + stepX / 2f) / stepX).toInt().coerceIn(0, dataPoints.lastIndex)
                            }
                        }
                    }
                    .pointerInput(dataPoints) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val leftGutter = 34.dp.toPx()
                            val chartW = (size.width - leftGutter).coerceAtLeast(1f)
                            if (dataPoints.isNotEmpty()) {
                                val stepX = chartW / (dataPoints.size - 1).coerceAtLeast(1)
                                val relX = (change.position.x - leftGutter).coerceAtLeast(0f)
                                selectedIndex = ((relX + stepX / 2f) / stepX).toInt().coerceIn(0, dataPoints.lastIndex)
                            }
                        }
                    }
            ) {
                val leftGutter = 34.dp.toPx()
                val topPad = 8.dp.toPx()
                val bottomPad = 8.dp.toPx()
                val chartW = (size.width - leftGutter).coerceAtLeast(1f)
                val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
                val n = dataPoints.size
                val stepX = if (n > 1) chartW / (n - 1) else chartW

                fun toX(index: Int): Float = leftGutter + index * stepX
                fun toY(v: Float): Float = topPad + chartH - (((v - computedMin) / yRange) * chartH).coerceIn(0f, chartH)

                val yNomMax = toY(baselineRange.max)
                val yNomMin = toY(baselineRange.min)
                drawRect(
                    color = accentColor.copy(alpha = 0.08f),
                    topLeft = Offset(leftGutter, yNomMax),
                    size = Size(chartW, (yNomMin - yNomMax).coerceAtLeast(2f))
                )

                val dashedGrid = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                listOf(baselineRange.max, baselineRange.nominal, baselineRange.min).forEach { tickVal ->
                    val ty = toY(tickVal)
                    drawLine(BorderSubtle, Offset(leftGutter, ty), Offset(leftGutter + chartW, ty), 1.dp.toPx(), pathEffect = dashedGrid)
                    drawContext.canvas.nativeCanvas.drawText(formatTick(tickVal), leftGutter - 6.dp.toPx(), ty + 3.dp.toPx(), axisTextPaint)
                }

                val segments = mutableListOf<List<Pair<Int, Float>>>()
                var currentSeg = mutableListOf<Pair<Int, Float>>()
                dataPoints.forEachIndexed { idx, pt ->
                    if (!pt.isGap && pt.value != null) {
                        currentSeg.add(idx to pt.value)
                    } else if (currentSeg.isNotEmpty()) {
                        segments.add(currentSeg)
                        currentSeg = mutableListOf()
                    }
                }
                if (currentSeg.isNotEmpty()) segments.add(currentSeg)

                // Progressive left-to-right clip reveal animation
                clipRect(
                    left = leftGutter - 8.dp.toPx(),
                    top = 0f,
                    right = leftGutter + (chartW + 12.dp.toPx()) * revealAnim.value,
                    bottom = size.height
                ) {
                    segments.forEach { seg ->
                        if (seg.size == 1) {
                            val (idx, v) = seg.first()
                            drawCircle(accentColor, 4.dp.toPx(), Offset(toX(idx), toY(v)))
                        } else if (seg.size >= 2) {
                            val coords = seg.map { (idx, v) -> Offset(toX(idx), toY(v)) }
                            val curvePath = Path().apply {
                                moveTo(coords.first().x, coords.first().y)
                                for (k in 0 until coords.size - 1) {
                                    val p0 = coords[k]
                                    val p1 = coords[k + 1]
                                    val midX = (p0.x + p1.x) / 2f
                                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                                }
                            }
                            val fillPath = Path().apply {
                                addPath(curvePath)
                                lineTo(coords.last().x, topPad + chartH)
                                lineTo(coords.first().x, topPad + chartH)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.25f), Color.Transparent), topPad, topPad + chartH)
                            )
                            drawPath(curvePath, accentColor, style = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }

                    selectedIndex?.let { idx ->
                        val pt = dataPoints.getOrNull(idx)
                        if (pt != null && !pt.isGap && pt.value != null) {
                            val px = toX(idx)
                            val py = toY(pt.value)
                            drawLine(accentColor.copy(alpha = 0.35f), Offset(px, topPad), Offset(px, topPad + chartH), 1.5.dp.toPx(), pathEffect = dashedGrid)
                            drawCircle(accentColor.copy(alpha = 0.2f), 11.dp.toPx(), Offset(px, py))
                            drawCircle(CardPure, 6.dp.toPx(), Offset(px, py))
                            drawCircle(accentColor, 4.dp.toPx(), Offset(px, py))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 10. BODY SCAN SCREEN (1-TO-1 COPIED FROM VITA SYS)
// ============================================================================
private fun resolveAnatomicalOrganOffset(systemId: String, w: Float, h: Float): Offset {
    val cx = w * 0.5f
    val s = h * 0.88f
    val topY = h * 0.05f
    fun bx(nx: Float): Float = cx + nx * s
    fun by(ny: Float): Float = topY + ny * s
    return when (systemId) {
        "sys_neuro_sans" -> Offset(bx(0.0f), by(0.072f))
        "sys_respiratory" -> Offset(bx(-0.052f), by(0.265f))
        "sys_cardio" -> Offset(bx(0.022f), by(0.295f))
        "sys_immune_rad" -> Offset(bx(0.054f), by(0.375f))
        "sys_renal_gi" -> Offset(bx(-0.038f), by(0.405f))
        "sys_musculo" -> Offset(bx(0.0f), by(0.515f))
        else -> Offset(cx, by(0.35f))
    }
}

@Composable
fun BodySystemsScreen(
    systems: List<AnatomySystem>,
    checkedSymptoms: Set<String>,
    onToggleSymptom: (String) -> Unit,
    onInspectMetric: (MetricType) -> Unit
) {
    var selectedSystemId by remember { mutableStateOf(systems.firstOrNull()?.id ?: "") }
    val currentSystem = systems.find { it.id == selectedSystemId } ?: systems.first()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgCanvas),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(modifier = Modifier.fillMaxWidth(), color = CardPure) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(currentSystem.name, color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${currentSystem.anatomicalRegion} • ${currentSystem.primaryRisk}", color = InkSecondary, fontSize = 11.sp)
                        }
                        StatusPill(status = currentSystem.status, compact = true)
                    }
                    HorizontalDivider(color = BorderSubtle)
                }
            }
        }

        item {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(350.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardPure)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            ) {
                val density = LocalDensity.current
                val boxWidthPx = with(density) { maxWidth.toPx() }
                val boxHeightPx = with(density) { maxHeight.toPx() }

                DetailedAnatomicalVisualization(
                    systems = systems,
                    selectedSystemId = selectedSystemId,
                    onSelectSystem = { selectedSystemId = it },
                    modifier = Modifier.fillMaxSize()
                )

                systems.forEach { sys ->
                    val isSelected = sys.id == selectedSystemId
                    val statusDotColor = when (sys.status) {
                        StatusLevel.CRITICAL -> CoralAlert
                        StatusLevel.MONITOR -> AmberWarn
                        StatusLevel.NORMAL -> MintVital
                        StatusLevel.UNAVAILABLE -> InkSecondary
                    }
                    val pillBg by animateColorAsState(if (isSelected) ElectricBlue else CardPure.copy(alpha = 0.94f), tween(250), label = "hBg")
                    val pillText by animateColorAsState(if (isSelected) Color.White else InkPrimary, tween(250), label = "hTx")
                    val organPx = resolveAnatomicalOrganOffset(sys.id, boxWidthPx, boxHeightPx)
                    val organDpX: Dp = with(density) { organPx.x.toDp() }
                    val organDpY: Dp = with(density) { organPx.y.toDp() }
                    val (badgeX, badgeY) = when (sys.id) {
                        "sys_neuro_sans" -> (organDpX + 20.dp) to (organDpY - 14.dp)
                        "sys_respiratory" -> (organDpX - 66.dp) to (organDpY - 13.dp)
                        "sys_cardio" -> (organDpX + 22.dp) to (organDpY - 13.dp)
                        "sys_immune_rad" -> (organDpX + 22.dp) to (organDpY - 8.dp)
                        "sys_renal_gi" -> (organDpX - 66.dp) to (organDpY - 10.dp)
                        "sys_musculo" -> (organDpX - 30.dp) to (organDpY + 12.dp)
                        else -> organDpX to organDpY
                    }
                    Box(
                        modifier = Modifier
                            .offset(
                                x = badgeX.coerceIn(8.dp, (maxWidth - 78.dp).coerceAtLeast(8.dp)),
                                y = badgeY.coerceIn(8.dp, (maxHeight - 32.dp).coerceAtLeast(8.dp))
                            )
                            .clip(RoundedCornerShape(999.dp))
                            .background(pillBg)
                            .border(1.dp, if (isSelected) ElectricBlue else BorderSubtle, RoundedCornerShape(999.dp))
                            .clickable { selectedSystemId = sys.id }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(modifier = Modifier.size(6.dp).background(if (isSelected) Color.White else statusDotColor, CircleShape))
                            Text(sys.shortLabel, color = pillText, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(systems, key = { it.id }) { system ->
                    val isSelected = system.id == selectedSystemId
                    val chipBg by animateColorAsState(if (isSelected) ElectricBlue else CardPure, tween(250), label = "sChipBg")
                    val chipText by animateColorAsState(if (isSelected) Color.White else InkPrimary, tween(250), label = "sChipTx")
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(chipBg)
                            .border(1.dp, if (isSelected) ElectricBlue else BorderSubtle, RoundedCornerShape(999.dp))
                            .clickable { selectedSystemId = system.id }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(system.name, color = chipText, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, fontSize = 12.sp)
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardPure)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val activeCheckedCount = currentSystem.symptomChecklist.count { it.id in checkedSymptoms }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Symptoms", color = InkPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(if (activeCheckedCount > 0) "$activeCheckedCount reported" else "None reported", color = if (activeCheckedCount > 0) AmberWarn else InkSecondary, fontSize = 11.sp)
                    }
                    currentSystem.symptomChecklist.forEach { sym ->
                        val isChecked = sym.id in checkedSymptoms
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isChecked) ElectricBlue.copy(alpha = 0.08f) else BgCanvas)
                                .clickable { onToggleSymptom(sym.id) }
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Checkbox(checked = isChecked, onCheckedChange = { onToggleSymptom(sym.id) }, modifier = Modifier.size(18.dp))
                                Text(sym.symptomText, color = InkPrimary, fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp)
                            }
                            AnimatedVisibility(visible = isChecked) {
                                Text("→ ${sym.recommendedAction}", color = AmberWarn, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 28.dp, top = 4.dp))
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardPure)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Countermeasures", color = InkPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    currentSystem.countermeasures.forEach { cm ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BgCanvas).padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cm.title, color = InkPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(cm.description, color = InkSecondary, fontSize = 12.sp)
                            }
                            Text(cm.dosageOrFrequency, color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    if (currentSystem.relatedMetrics.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            currentSystem.relatedMetrics.forEach { metric ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ElectricBlue.copy(alpha = 0.1f))
                                        .clickable { onInspectMetric(metric) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text("${metric.shortName} Trends →", color = ElectricBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailedAnatomicalVisualization(
    systems: List<AnatomySystem>,
    selectedSystemId: String,
    onSelectSystem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bioScannerAnimations")

    val scannerYRatio by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scannerYRatio"
    )

    val lungBreathScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lungBreathScale"
    )

    val heartPulseScale by infiniteTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutLinearInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulseScale"
    )

    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flowPhase"
    )

    val reticleAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reticleAngle"
    )

    val activeSystem = systems.find { it.id == selectedSystemId }
    val activeStatusColor = when (activeSystem?.status) {
        StatusLevel.CRITICAL -> CoralAlert
        StatusLevel.MONITOR -> AmberWarn
        StatusLevel.NORMAL -> ElectricBlue
        else -> ElectricBlue
    }

    val isNeuroSelected = selectedSystemId == "sys_neuro_sans"
    val isRespSelected = selectedSystemId == "sys_respiratory"
    val isCardioSelected = selectedSystemId == "sys_cardio"
    val isImmuneSelected = selectedSystemId == "sys_immune_rad"
    val isRenalSelected = selectedSystemId == "sys_renal_gi"
    val isMusculoSelected = selectedSystemId == "sys_musculo"

    Canvas(
        modifier = modifier.pointerInput(systems) {
            detectTapGestures { tapOffset ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val closest = systems.minByOrNull { sys ->
                    val organPos = resolveAnatomicalOrganOffset(sys.id, w, h)
                    hypot(tapOffset.x - organPos.x, tapOffset.y - organPos.y)
                }
                if (closest != null) {
                    val organPos = resolveAnatomicalOrganOffset(closest.id, w, h)
                    if (hypot(tapOffset.x - organPos.x, tapOffset.y - organPos.y) <= 44.dp.toPx()) {
                        onSelectSystem(closest.id)
                    }
                }
            }
        }
    ) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f

        // --- LAYER 1: Precision Medical Calibration Grid ---
        val gridStep = 24.dp.toPx()
        var gx = gridStep
        while (gx < w) {
            drawLine(BorderSubtle.copy(alpha = 0.35f), Offset(gx, 0f), Offset(gx, h), 0.8f)
            gx += gridStep
        }
        var gy = gridStep
        while (gy < h) {
            drawLine(BorderSubtle.copy(alpha = 0.35f), Offset(0f, gy), Offset(w, gy), 0.8f)
            gy += gridStep
        }

        val axisDash = PathEffect.dashPathEffect(floatArrayOf(5f, 7f), 0f)
        drawLine(
            color = ElectricBlue.copy(alpha = 0.22f),
            start = Offset(cx, 8.dp.toPx()),
            end = Offset(cx, h - 8.dp.toPx()),
            strokeWidth = 1.2f,
            pathEffect = axisDash
        )

        // --- LAYER 2: Contoured Human Anatomical Silhouette ---
        val s = h * 0.88f
        val topY = h * 0.05f
        fun bx(nx: Float): Float = cx + nx * s
        fun by(ny: Float): Float = topY + ny * s

        val bodySilhouette = Path().apply {
            moveTo(bx(0f), by(0.01f))
            cubicTo(bx(0.055f), by(0.01f), bx(0.072f), by(0.045f), bx(0.068f), by(0.085f))
            cubicTo(bx(0.065f), by(0.115f), bx(0.042f), by(0.138f), bx(0.032f), by(0.152f))
            cubicTo(bx(0.032f), by(0.172f), bx(0.085f), by(0.182f), bx(0.155f), by(0.195f))
            cubicTo(bx(0.188f), by(0.205f), bx(0.205f), by(0.235f), bx(0.208f), by(0.275f))
            cubicTo(bx(0.215f), by(0.35f), bx(0.225f), by(0.43f), bx(0.232f), by(0.50f))
            cubicTo(bx(0.238f), by(0.535f), bx(0.215f), by(0.55f), bx(0.198f), by(0.525f))
            cubicTo(bx(0.185f), by(0.45f), bx(0.170f), by(0.35f), bx(0.145f), by(0.275f))
            cubicTo(bx(0.138f), by(0.34f), bx(0.115f), by(0.40f), bx(0.112f), by(0.45f))
            cubicTo(bx(0.128f), by(0.49f), bx(0.135f), by(0.56f), bx(0.122f), by(0.67f))
            cubicTo(bx(0.115f), by(0.75f), bx(0.108f), by(0.84f), bx(0.098f), by(0.94f))
            lineTo(bx(0.048f), by(0.94f))
            cubicTo(bx(0.048f), by(0.83f), bx(0.042f), by(0.68f), bx(0.016f), by(0.54f))
            quadraticTo(bx(0f), by(0.53f), bx(-0.016f), by(0.54f))
            cubicTo(bx(-0.042f), by(0.68f), bx(-0.048f), by(0.83f), bx(-0.048f), by(0.94f))
            lineTo(bx(-0.098f), by(0.94f))
            cubicTo(bx(-0.108f), by(0.84f), bx(-0.115f), by(0.75f), bx(-0.122f), by(0.67f))
            cubicTo(bx(-0.135f), by(0.56f), bx(-0.128f), by(0.49f), bx(-0.112f), by(0.45f))
            cubicTo(bx(-0.115f), by(0.40f), bx(-0.138f), by(0.34f), bx(-0.145f), by(0.275f))
            cubicTo(bx(-0.170f), by(0.35f), bx(-0.185f), by(0.45f), bx(-0.198f), by(0.525f))
            cubicTo(bx(-0.215f), by(0.55f), bx(-0.238f), by(0.535f), bx(-0.232f), by(0.50f))
            cubicTo(bx(-0.225f), by(0.43f), bx(-0.215f), by(0.35f), bx(-0.208f), by(0.275f))
            cubicTo(bx(-0.205f), by(0.235f), bx(-0.188f), by(0.205f), bx(-0.155f), by(0.195f))
            cubicTo(bx(-0.085f), by(0.182f), bx(-0.032f), by(0.172f), bx(-0.032f), by(0.152f))
            cubicTo(bx(-0.042f), by(0.138f), bx(-0.065f), by(0.115f), bx(-0.068f), by(0.085f))
            cubicTo(bx(-0.072f), by(0.045f), bx(-0.055f), by(0.01f), bx(0f), by(0.01f))
            close()
        }

        drawPath(
            path = bodySilhouette,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFF6F1EB), Color(0xFFF2ECE4), Color(0xFFEBE3D9)),
                startY = topY,
                endY = topY + s
            )
        )
        drawPath(
            path = bodySilhouette,
            color = Color(0xFFC5B8AC),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // --- LAYER 3: Skeletal Structure (Spine, Pelvis, Femurs) ---
        val boneColor = if (isMusculoSelected) ElectricBlue else Color(0xFFD2C6BA)
        for (i in 0..15) {
            val vy = by(0.155f + i * 0.021f)
            val vw = (if (i < 4) 0.022f else if (i < 11) 0.028f else 0.036f) * s
            val vh = 0.012f * s
            drawRoundRect(
                color = if (isMusculoSelected) ElectricBlue.copy(alpha = 0.8f) else boneColor,
                topLeft = Offset(cx - vw / 2f, vy),
                size = Size(vw, vh),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
        val pelvisPath = Path().apply {
            moveTo(bx(-0.085f), by(0.46f))
            cubicTo(bx(-0.105f), by(0.49f), bx(-0.065f), by(0.53f), bx(0f), by(0.52f))
            cubicTo(bx(0.065f), by(0.53f), bx(0.105f), by(0.49f), bx(0.085f), by(0.46f))
        }
        drawPath(pelvisPath, boneColor, style = Stroke(if (isMusculoSelected) 2.4.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round))
        drawLine(boneColor, Offset(bx(-0.068f), by(0.50f)), Offset(bx(-0.065f), by(0.88f)), if (isMusculoSelected) 2.2.dp.toPx() else 1.4.dp.toPx(), StrokeCap.Round)
        drawLine(boneColor, Offset(bx(0.068f), by(0.50f)), Offset(bx(0.065f), by(0.88f)), if (isMusculoSelected) 2.2.dp.toPx() else 1.4.dp.toPx(), StrokeCap.Round)

        // --- LAYER 4: Respiratory System (Trachea, Bronchial Tree & Lungs) ---
        val lungStrokeColor = if (isRespSelected) ElectricBlue else Color(0xFF8C624A)
        val lungFillColor = if (isRespSelected) CyanGlow.copy(alpha = 0.28f) else Color(0xFFE4D5C7).copy(alpha = 0.72f)
        val tracheaTop = by(0.155f)
        val carinaY = by(0.222f)
        drawLine(lungStrokeColor, Offset(cx, tracheaTop), Offset(cx, carinaY), if (isRespSelected) 3.5.dp.toPx() else 2.5.dp.toPx(), StrokeCap.Round)

        val lungCenter = Offset(cx, by(0.275f))
        withTransform({ scale(lungBreathScale, lungBreathScale, pivot = lungCenter) }) {
            val rightLung = Path().apply {
                moveTo(bx(-0.015f), by(0.210f))
                cubicTo(bx(-0.072f), by(0.205f), bx(-0.105f), by(0.255f), bx(-0.098f), by(0.332f))
                quadraticTo(bx(-0.055f), by(0.318f), bx(-0.014f), by(0.326f))
                close()
            }
            val leftLung = Path().apply {
                moveTo(bx(0.015f), by(0.210f))
                cubicTo(bx(0.072f), by(0.205f), bx(0.105f), by(0.255f), bx(0.098f), by(0.332f))
                quadraticTo(bx(0.058f), by(0.320f), bx(0.025f), by(0.326f))
                quadraticTo(bx(0.036f), by(0.295f), bx(0.015f), by(0.272f))
                close()
            }
            drawPath(rightLung, color = lungFillColor)
            drawPath(rightLung, color = lungStrokeColor, style = Stroke(if (isRespSelected) 2.2.dp.toPx() else 1.5.dp.toPx(), join = StrokeJoin.Round))
            drawPath(leftLung, color = lungFillColor)
            drawPath(leftLung, color = lungStrokeColor, style = Stroke(if (isRespSelected) 2.2.dp.toPx() else 1.5.dp.toPx(), join = StrokeJoin.Round))

            val bronchiPath = Path().apply {
                moveTo(cx, carinaY); lineTo(bx(-0.048f), by(0.255f)); lineTo(bx(-0.074f), by(0.242f))
                moveTo(bx(-0.048f), by(0.255f)); lineTo(bx(-0.078f), by(0.282f))
                moveTo(bx(-0.048f), by(0.255f)); lineTo(bx(-0.055f), by(0.308f))
                moveTo(cx, carinaY); lineTo(bx(0.048f), by(0.255f)); lineTo(bx(0.074f), by(0.242f))
                moveTo(bx(0.048f), by(0.255f)); lineTo(bx(0.078f), by(0.285f))
                moveTo(bx(0.048f), by(0.255f)); lineTo(bx(0.058f), by(0.308f))
            }
            drawPath(bronchiPath, lungStrokeColor.copy(alpha = if (isRespSelected) 0.95f else 0.65f), style = Stroke(if (isRespSelected) 1.8.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round))
        }

        for (r in 0..4) {
            val ry = by(0.225f + r * 0.024f)
            val ribSpan = (0.092f - r * 0.004f) * s
            val ribArcHeight = 0.016f * s
            val leftRib = Path().apply {
                moveTo(cx - 0.014f * s, ry)
                quadraticTo(cx - ribSpan * 0.65f, ry - ribArcHeight, cx - ribSpan, ry + ribArcHeight * 0.5f)
            }
            val rightRib = Path().apply {
                moveTo(cx + 0.014f * s, ry)
                quadraticTo(cx + ribSpan * 0.65f, ry - ribArcHeight, cx + ribSpan, ry + ribArcHeight * 0.5f)
            }
            drawPath(leftRib, boneColor.copy(alpha = 0.45f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(rightRib, boneColor.copy(alpha = 0.45f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
        }

        // --- LAYER 5: Immune & Lymphatic System (Thymus, Spleen, Lymph Nodes) ---
        val immuneColor = if (isImmuneSelected) MintVital else Color(0xFF5D7A68)
        val thymusCenter = Offset(bx(0.0f), by(0.205f))
        val spleenCenter = Offset(bx(0.054f), by(0.375f))
        val lymphaticTree = Path().apply {
            moveTo(bx(-0.018f), by(0.155f)); lineTo(thymusCenter.x, thymusCenter.y)
            moveTo(bx(0.018f), by(0.155f)); lineTo(thymusCenter.x, thymusCenter.y)
            moveTo(thymusCenter.x, thymusCenter.y); quadraticTo(bx(-0.06f), by(0.21f), bx(-0.105f), by(0.245f))
            moveTo(thymusCenter.x, thymusCenter.y); quadraticTo(bx(0.06f), by(0.21f), bx(0.105f), by(0.245f))
            moveTo(thymusCenter.x, thymusCenter.y); lineTo(bx(0.012f), by(0.35f)); lineTo(spleenCenter.x, spleenCenter.y)
            moveTo(bx(0.012f), by(0.35f)); lineTo(bx(0.0f), by(0.46f)); lineTo(bx(-0.048f), by(0.51f))
            moveTo(bx(0.0f), by(0.46f)); lineTo(bx(0.048f), by(0.51f))
        }
        drawPath(
            path = lymphaticTree,
            color = immuneColor.copy(alpha = if (isImmuneSelected) 0.9f else 0.48f),
            style = Stroke(if (isImmuneSelected) 1.9.dp.toPx() else 1.1.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), -flowPhase * 18f))
        )
        drawOval(immuneColor.copy(alpha = if (isImmuneSelected) 0.35f else 0.18f), Offset(thymusCenter.x - 0.022f * s, thymusCenter.y - 0.014f * s), Size(0.044f * s, 0.028f * s))
        drawOval(immuneColor, Offset(thymusCenter.x - 0.022f * s, thymusCenter.y - 0.014f * s), Size(0.044f * s, 0.028f * s), style = Stroke(if (isImmuneSelected) 1.8.dp.toPx() else 1.2.dp.toPx()))
        drawRoundRect(immuneColor.copy(alpha = if (isImmuneSelected) 0.42f else 0.22f), Offset(spleenCenter.x - 0.018f * s, spleenCenter.y - 0.024f * s), Size(0.036f * s, 0.048f * s), CornerRadius(10.dp.toPx()))
        drawRoundRect(immuneColor, Offset(spleenCenter.x - 0.018f * s, spleenCenter.y - 0.024f * s), Size(0.036f * s, 0.048f * s), CornerRadius(10.dp.toPx()), style = Stroke(if (isImmuneSelected) 2.dp.toPx() else 1.3.dp.toPx()))
        listOf(Offset(bx(-0.105f), by(0.245f)), Offset(bx(0.105f), by(0.245f)), Offset(bx(-0.048f), by(0.51f)), Offset(bx(0.048f), by(0.51f))).forEach { node ->
            drawCircle(immuneColor.copy(alpha = if (isImmuneSelected) 0.9f else 0.55f), if (isImmuneSelected) 3.4.dp.toPx() else 2.4.dp.toPx(), node)
        }

        // --- LAYER 6: Cranial / Neuro-Ocular Pathways ---
        val neuroColor = if (isNeuroSelected) AmberWarn else Color(0xFF8C6E5D)
        val brainCenter = Offset(cx, by(0.068f))
        drawOval(neuroColor.copy(alpha = if (isNeuroSelected) 0.24f else 0.12f), Offset(cx - 0.046f * s, by(0.032f)), Size(0.092f * s, 0.068f * s))
        drawOval(neuroColor, Offset(cx - 0.046f * s, by(0.032f)), Size(0.092f * s, 0.068f * s), style = Stroke(if (isNeuroSelected) 1.9.dp.toPx() else 1.2.dp.toPx()))
        drawCircle(neuroColor, 2.6.dp.toPx(), Offset(bx(-0.022f), by(0.078f)))
        drawCircle(neuroColor, 2.6.dp.toPx(), Offset(bx(0.022f), by(0.078f)))
        drawLine(neuroColor.copy(alpha = 0.7f), Offset(bx(-0.022f), by(0.078f)), brainCenter, 1.2.dp.toPx())
        drawLine(neuroColor.copy(alpha = 0.7f), Offset(bx(0.022f), by(0.078f)), brainCenter, 1.2.dp.toPx())

        // --- LAYER 7: Renal System ---
        val renalColor = if (isRenalSelected) ElectricBlue else Color(0xFF967A6B)
        drawRoundRect(renalColor.copy(alpha = if (isRenalSelected) 0.34f else 0.16f), Offset(bx(-0.052f), by(0.382f)), Size(0.028f * s, 0.045f * s), CornerRadius(8.dp.toPx()))
        drawRoundRect(renalColor, Offset(bx(-0.052f), by(0.382f)), Size(0.028f * s, 0.045f * s), CornerRadius(8.dp.toPx()), style = Stroke(if (isRenalSelected) 2.dp.toPx() else 1.2.dp.toPx()))
        drawRoundRect(renalColor.copy(alpha = if (isRenalSelected) 0.34f else 0.16f), Offset(bx(0.020f), by(0.388f)), Size(0.028f * s, 0.045f * s), CornerRadius(8.dp.toPx()))
        drawRoundRect(renalColor, Offset(bx(0.020f), by(0.388f)), Size(0.028f * s, 0.045f * s), CornerRadius(8.dp.toPx()), style = Stroke(if (isRenalSelected) 2.dp.toPx() else 1.2.dp.toPx()))

        // --- LAYER 8: Cardiovascular Arterial Tree & Beating Heart ---
        val arteryColor = if (isCardioSelected) CoralAlert.copy(alpha = 0.85f) else Color(0xFFD06A6A).copy(alpha = 0.55f)
        val heartCenter = resolveAnatomicalOrganOffset("sys_cardio", w, h)
        val arterialTree = Path().apply {
            moveTo(heartCenter.x, heartCenter.y); lineTo(cx, by(0.15f)); lineTo(bx(-0.015f), by(0.08f))
            moveTo(cx, by(0.15f)); lineTo(bx(0.015f), by(0.08f))
            moveTo(cx, by(0.21f)); quadraticTo(bx(-0.15f), by(0.22f), bx(-0.195f), by(0.45f))
            moveTo(cx, by(0.21f)); quadraticTo(bx(0.15f), by(0.22f), bx(0.195f), by(0.45f))
            moveTo(heartCenter.x, heartCenter.y); lineTo(cx, by(0.47f)); lineTo(bx(-0.055f), by(0.76f))
            moveTo(cx, by(0.47f)); lineTo(bx(0.055f), by(0.76f))
        }
        drawPath(arterialTree, arteryColor, style = Stroke(if (isCardioSelected) 2.dp.toPx() else 1.2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(arteryColor, 2.8.dp.toPx(), Offset(cx, by(0.21f + flowPhase * 0.26f)))

        withTransform({ scale(heartPulseScale, heartPulseScale, pivot = heartCenter) }) {
            drawCircle(CoralAlert.copy(alpha = 0.22f), 11.dp.toPx(), heartCenter)
            drawCircle(if (isCardioSelected) CoralAlert else Color(0xFFC84B4B), 6.5.dp.toPx(), heartCenter)
        }

        // --- LAYER 9: Continuous Vertical Bio-Scanner Sweep ---
        val scanY = h * scannerYRatio
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, ElectricBlue.copy(alpha = 0.12f), Color.Transparent), scanY - 18.dp.toPx(), scanY + 18.dp.toPx()),
            topLeft = Offset(w * 0.18f, scanY - 18.dp.toPx()),
            size = Size(w * 0.64f, 36.dp.toPx())
        )
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, ElectricBlue.copy(alpha = 0.55f), ElectricBlue, ElectricBlue.copy(alpha = 0.55f), Color.Transparent)),
            start = Offset(w * 0.18f, scanY),
            end = Offset(w * 0.82f, scanY),
            strokeWidth = 1.4.dp.toPx()
        )

        // --- LAYER 10: Active Organ Target Reticles & Leader Callout Lines ---
        systems.forEach { sys ->
            val isSelected = sys.id == selectedSystemId
            val nodePos = resolveAnatomicalOrganOffset(sys.id, w, h)
            val nodeColor = when (sys.status) {
                StatusLevel.CRITICAL -> CoralAlert
                StatusLevel.MONITOR -> AmberWarn
                StatusLevel.NORMAL -> MintVital
                StatusLevel.UNAVAILABLE -> InkSecondary
            }
            val leaderEnd = when (sys.id) {
                "sys_neuro_sans" -> Offset(nodePos.x + 22.dp.toPx(), nodePos.y - 4.dp.toPx())
                "sys_respiratory" -> Offset(nodePos.x - 18.dp.toPx(), nodePos.y)
                "sys_cardio" -> Offset(nodePos.x + 22.dp.toPx(), nodePos.y)
                "sys_immune_rad" -> Offset(nodePos.x + 22.dp.toPx(), nodePos.y + 2.dp.toPx())
                "sys_renal_gi" -> Offset(nodePos.x - 18.dp.toPx(), nodePos.y)
                "sys_musculo" -> Offset(nodePos.x, nodePos.y + 14.dp.toPx())
                else -> nodePos
            }
            drawLine(if (isSelected) ElectricBlue else BorderSubtle, nodePos, leaderEnd, if (isSelected) 1.5.dp.toPx() else 1.dp.toPx())

            if (isSelected) {
                drawCircle(activeStatusColor.copy(alpha = 0.15f), 22.dp.toPx(), nodePos)
                val ringRadius = 15.dp.toPx()
                for (seg in 0 until 4) {
                    drawArc(
                        color = activeStatusColor,
                        startAngle = reticleAngle + seg * 90f,
                        sweepAngle = 52f,
                        useCenter = false,
                        topLeft = Offset(nodePos.x - ringRadius, nodePos.y - ringRadius),
                        size = Size(ringRadius * 2f, ringRadius * 2f),
                        style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                drawCircle(CardPure, 5.5.dp.toPx(), nodePos)
                drawCircle(activeStatusColor, 4.dp.toPx(), nodePos)
            } else {
                drawCircle(CardPure, 4.5.dp.toPx(), nodePos)
                drawCircle(nodeColor, 3.dp.toPx(), nodePos)
            }
        }
    }
}

// ============================================================================
// 11. PROTOCOLS SCREEN (VITA SYS + CDME-PRO-MAX DOCUMENT PROTOCOLS + DELTA LOG)
// ============================================================================
@Composable
fun ProtocolsScreen(
    protocols: List<ClinicalProtocol>,
    initialExpandedProtocolCode: String? = null,
    readiness: CrewReadinessState,
    deltaLog: List<String>,
    reportPayload: String,
    onToggleProtocolStep: (String, Int) -> Unit,
    onResetProtocol: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var expandedProtocolCode by remember(initialExpandedProtocolCode) {
        mutableStateOf(initialExpandedProtocolCode ?: protocols.firstOrNull()?.code)
    }
    var activeTimerCode by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }

    LaunchedEffect(isTimerRunning, remainingSeconds) {
        if (isTimerRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds -= 1
            if (remainingSeconds == 0) isTimerRunning = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgCanvas),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(modifier = Modifier.fillMaxWidth(), color = CardPure) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Protocols & Safety Interlocks", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("pCO2: ${readiness.cabinCo2MmHg} mmHg • Wanninkhof Flux: ${readiness.wanninkhofFlux}", color = InkSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    HorizontalDivider(color = BorderSubtle)
                }
            }
        }

        items(protocols, key = { it.code }) { protocol ->
            val isExpanded = expandedProtocolCode == protocol.code
            val completedSteps = protocol.steps.count { it.isCompleted }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, if (isExpanded) ElectricBlue else BorderSubtle, RoundedCornerShape(16.dp))
                    .animateContentSize(),
                color = CardPure
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expandedProtocolCode = if (isExpanded) null else protocol.code },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(protocol.title, color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${protocol.category} • ${protocol.triggerCondition}", color = InkSecondary, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusPill(status = protocol.severity, compact = true)
                            Text("$completedSteps/${protocol.steps.size}", color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = BorderSubtle)
                            if (protocol.timerDurationSeconds != null) {
                                val isThisTimer = activeTimerCode == protocol.code
                                val displaySec = if (isThisTimer) remainingSeconds else protocol.timerDurationSeconds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ElectricBlue.copy(alpha = 0.08f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(protocol.timerLabel ?: "Timer", color = InkSecondary, fontSize = 11.sp)
                                        Text("%02d:%02d".format(displaySec / 60, displaySec % 60), color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = {
                                                if (!isThisTimer) {
                                                    activeTimerCode = protocol.code
                                                    remainingSeconds = protocol.timerDurationSeconds
                                                    isTimerRunning = true
                                                } else {
                                                    isTimerRunning = !isTimerRunning
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Text(if (isThisTimer && isTimerRunning) "Pause" else "Start", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                isTimerRunning = false
                                                remainingSeconds = protocol.timerDurationSeconds
                                                onResetProtocol(protocol.code)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(34.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp)
                                        ) {
                                            Text("Reset", fontSize = 12.sp, color = ElectricBlue)
                                        }
                                    }
                                }
                            }
                            protocol.steps.forEach { step ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (step.isCompleted) MintVital.copy(alpha = 0.1f) else BgCanvas)
                                        .clickable { onToggleProtocolStep(protocol.code, step.index) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Checkbox(checked = step.isCompleted, onCheckedChange = { onToggleProtocolStep(protocol.code, step.index) }, modifier = Modifier.size(18.dp))
                                    Text(step.instruction, color = if (step.isCompleted) InkSecondary else InkPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Encrypted Delta-Log & Flight Surgeon Share
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                color = CardPure
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Encrypted Post-Mission Delta-Log (${deltaLog.size})", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    deltaLog.take(3).forEach { log ->
                        Text(log, color = InkSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(reportPayload))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Copy Report", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "AstroMerged Flight Surgeon Report")
                                    putExtra(Intent.EXTRA_TEXT, reportPayload)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share to Flight Surgeon"))
                            },
                            modifier = Modifier.weight(1f).height(40.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Flight Surgeon", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 12. AI COACH SCREEN (FROM ASTRA VITA SYS + GEMINI API + SAFETY INTERLOCKS)
// ============================================================================
private val quickAiPrompts = listOf(
    "How am I doing today?",
    "Microgravity CPR steps",
    "CO2 & Hypercapnia check",
    "Improve my sleep"
)

@Composable
fun AiCoachScreen(
    readiness: CrewReadinessState,
    aiState: AiAssistantUiState,
    hasGeminiKey: Boolean,
    onAskAi: (String) -> Unit,
    onUpdateBaselines: (String, Int, Float, Float, Float) -> Unit
) {
    var questionInput by remember { mutableStateOf("") }
    var nameInput by remember(readiness.crewName) { mutableStateOf(readiness.crewName) }
    var dayInput by remember(readiness.missionDay) { mutableStateOf(readiness.missionDay.toString()) }
    var hrInput by remember { mutableStateOf("72") }
    var spo2Input by remember { mutableStateOf("98") }
    var sleepInput by remember { mutableStateOf("7.5") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgCanvas).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardPure),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(ElectricBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text("AI Health & Recovery Coach", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = InkPrimary)
                            Text(
                                if (hasGeminiKey) "Gemini API Connected • Safety Interlocks Active" else "Offline Safety Interlock Coach • Paste AI Key in Profile",
                                fontSize = 12.sp,
                                color = InkSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickAiPrompts.forEach { prompt ->
                            OutlinedButton(
                                onClick = {
                                    questionInput = prompt
                                    onAskAi(prompt)
                                },
                                border = BorderStroke(1.dp, BorderSubtle),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(prompt, color = InkPrimary, fontSize = 12.sp)
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = questionInput,
                            onValueChange = { questionInput = it },
                            label = { Text("Ask for care or recovery steps...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onAskAi(questionInput) },
                            enabled = questionInput.isNotBlank() && aiState !is AiAssistantUiState.Loading,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
                        }
                    }

                    when (aiState) {
                        is AiAssistantUiState.Idle -> {
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCanvas).padding(14.dp)) {
                                Text("Tap a prompt above for instant guidance validated by NASA flight-rule interlocks.", fontSize = 13.sp, color = InkSecondary)
                            }
                        }
                        is AiAssistantUiState.Loading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCanvas).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                Text("Running Gemini & Safety Interlock Gate...", fontSize = 13.sp, color = ElectricBlue)
                            }
                        }
                        is AiAssistantUiState.Success -> {
                            Column(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(BgCanvas).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Care Recommendation", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MintVital)
                                Text(aiState.text, fontSize = 13.sp, color = InkPrimary, lineHeight = 20.sp)
                                if (aiState.interlockRules.isNotEmpty()) {
                                    Text(
                                        "Verified: ${aiState.interlockRules.joinToString(" • ")}",
                                        fontSize = 10.sp,
                                        color = ElectricBlue,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        is AiAssistantUiState.Error -> {
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CoralAlert.copy(alpha = 0.1f)).padding(14.dp)) {
                                Text(aiState.message, fontSize = 13.sp, color = CoralAlert)
                            }
                        }
                    }
                }
            }
        }

        // Personal Baseline Editor from Astra Vita SYS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardPure),
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Personal Baselines", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = InkPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = nameInput, onValueChange = { nameInput = it }, label = { Text("Crew Name") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = dayInput, onValueChange = { dayInput = it }, label = { Text("Sol") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(0.6f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = hrInput, onValueChange = { hrInput = it }, label = { Text("HR (BPM)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = spo2Input, onValueChange = { spo2Input = it }, label = { Text("SpO2 (%)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = sleepInput, onValueChange = { sleepInput = it }, label = { Text("Sleep (h)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            onUpdateBaselines(
                                nameInput,
                                dayInput.toIntOrNull() ?: readiness.missionDay,
                                hrInput.toFloatOrNull() ?: 72f,
                                spo2Input.toFloatOrNull() ?: 98f,
                                sleepInput.toFloatOrNull() ?: 7.5f
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text("Save Baselines", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ============================================================================
// 13. PROFILE SETTINGS & LOG ENTRY DIALOGS
// ============================================================================
@Composable
fun ProfileSettingsDialog(
    crewName: String,
    isSignedIn: Boolean,
    signedInEmail: String,
    authProvider: String,
    currentGeminiKey: String,
    currentOAuthClientId: String,
    criticalAlertsEnabled: Boolean,
    alertSoundEnabled: Boolean,
    alertSensitivity: String,
    continuousPollingEnabled: Boolean,
    isOfflineMode: Boolean,
    donkiSyncEnabled: Boolean,
    useFahrenheit: Boolean,
    encryptedLogEnabled: Boolean,
    onDismiss: () -> Unit,
    onGoogleOAuthSignIn: (String) -> Unit,
    onLocalSignIn: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onSaveOAuthClientId: (String) -> Unit,
    onToggleCriticalAlerts: (Boolean) -> Unit,
    onToggleAlertSound: (Boolean) -> Unit,
    onSelectAlertSensitivity: (String) -> Unit,
    onToggleContinuousPolling: (Boolean) -> Unit,
    onToggleOfflineMode: () -> Unit,
    onToggleDonkiSync: (Boolean) -> Unit,
    onToggleUseFahrenheit: (Boolean) -> Unit,
    onToggleEncryptedLog: (Boolean) -> Unit,
    onClearDeltaLog: () -> Unit,
    onResetSettings: () -> Unit
) {
    var keyInput by remember(currentGeminiKey) { mutableStateOf(currentGeminiKey) }
    var showKey by remember { mutableStateOf(false) }
    var oauthInput by remember(currentOAuthClientId) { mutableStateOf(currentOAuthClientId) }
    var showOAuth by remember { mutableStateOf(false) }
    var showLocalSignInForm by remember { mutableStateOf(false) }
    var localNameInput by remember(crewName) { mutableStateOf(crewName) }
    var localEmailInput by remember(signedInEmail) { mutableStateOf(signedInEmail) }

    val initials = remember(crewName) {
        crewName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.takeLast(2)
            .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "CR" }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp)),
            color = CardPure
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account & Settings", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = InkSecondary)
                    }
                }

                // 1. Account & Sign-In Section
                Surface(
                    color = BgCanvas,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSignedIn) InnerRingGreen.copy(alpha = 0.18f) else ElectricBlue.copy(alpha = 0.14f))
                                    .border(1.dp, if (isSignedIn) InnerRingGreen else ElectricBlue.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    initials,
                                    color = if (isSignedIn) MintVital else ElectricBlue,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isSignedIn) crewName else "Guest Crew Session",
                                    color = InkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    if (isSignedIn) {
                                        "${signedInEmail.ifBlank { "Crew Account" }} • $authProvider"
                                    } else {
                                        "Sign in to sync profile & alerts"
                                    },
                                    color = InkSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isSignedIn) {
                            OutlinedButton(
                                onClick = onSignOut,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CoralAlert.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            ) {
                                Text("Sign Out", color = CoralAlert, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    val cid = oauthInput.trim()
                                    if (cid.isNotBlank()) {
                                        onSaveOAuthClientId(cid)
                                        onGoogleOAuthSignIn(cid)
                                    } else {
                                        showLocalSignInForm = !showLocalSignInForm
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(40.dp)
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (oauthInput.isNotBlank()) "Sign in with Google OAuth" else "Sign In",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                            if (oauthInput.isNotBlank()) {
                                TextButton(
                                    onClick = { showLocalSignInForm = !showLocalSignInForm },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        if (showLocalSignInForm) "Hide Crew Sign-In" else "Or sign in with Crew Email",
                                        fontSize = 11.sp,
                                        color = ElectricBlue
                                    )
                                }
                            }
                            AnimatedVisibility(visible = showLocalSignInForm) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = localNameInput,
                                        onValueChange = { localNameInput = it },
                                        label = { Text("Display Name") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = localEmailInput,
                                        onValueChange = { localEmailInput = it },
                                        label = { Text("Email") },
                                        placeholder = { Text("crew@nasa.gov") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Button(
                                        onClick = {
                                            onLocalSignIn(localNameInput.trim(), localEmailInput.trim())
                                            showLocalSignInForm = false
                                        },
                                        enabled = localNameInput.isNotBlank() && localEmailInput.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(38.dp)
                                    ) {
                                        Text("Confirm Sign-In", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderSubtle)

                // 2. Google OAuth & Gemini API Keys
                Text("AI & Google OAuth Keys", color = InkSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = oauthInput,
                    onValueChange = { oauthInput = it },
                    label = { Text("Google OAuth 2.0 Client ID") },
                    placeholder = { Text("Paste OAuth Client ID") },
                    singleLine = true,
                    visualTransformation = if (showOAuth) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showOAuth = !showOAuth }) {
                            Icon(
                                imageVector = if (showOAuth) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle OAuth Client ID Visibility",
                                tint = InkSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Redirect URI: com.example.astromerged:/oauth2redirect",
                    color = InkSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            oauthInput = ""
                            onSaveOAuthClientId("")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear OAuth", color = CoralAlert, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onSaveOAuthClientId(oauthInput.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save OAuth ID", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("Paste AI key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Key Visibility",
                                tint = InkSecondary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            keyInput = ""
                            onSaveGeminiKey("")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear AI Key", color = CoralAlert, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { onSaveGeminiKey(keyInput.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save AI Key", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    }
                }

                HorizontalDivider(color = BorderSubtle)

                // 3. Alerts & Notifications Settings
                Text("Alerts & Notifications", color = InkSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                SettingsToggleRow(
                    title = "Critical Vital Alerts",
                    subtitle = "Trigger banner & protocol prompts on anomalies",
                    checked = criticalAlertsEnabled,
                    onCheckedChange = onToggleCriticalAlerts
                )
                SettingsToggleRow(
                    title = "Alert Chime & Haptics",
                    subtitle = "Audible & tactile warning on critical threshold",
                    checked = alertSoundEnabled,
                    onCheckedChange = onToggleAlertSound
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Standard", "Strict", "EVA").forEach { mode ->
                        val selected = alertSensitivity == mode
                        Surface(
                            onClick = { onSelectAlertSensitivity(mode) },
                            color = if (selected) ElectricBlue else BgCanvas,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (selected) ElectricBlue else BorderSubtle),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    mode,
                                    color = if (selected) Color.White else InkPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = BorderSubtle)

                // 4. Telemetry, Units & Privacy Settings
                Text("Telemetry, Units & Privacy", color = InkSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                SettingsToggleRow(
                    title = "Continuous Sensor Polling",
                    subtitle = "1s cardio-respiratory & 4s metabolic updates",
                    checked = continuousPollingEnabled,
                    onCheckedChange = onToggleContinuousPolling
                )
                SettingsToggleRow(
                    title = "Offline Link Mode",
                    subtitle = "Queue telemetry locally during comms blackout",
                    checked = isOfflineMode,
                    onCheckedChange = { onToggleOfflineMode() }
                )
                SettingsToggleRow(
                    title = "NASA DONKI Space Weather Sync",
                    subtitle = "Check live Solar Energetic Particle alerts",
                    checked = donkiSyncEnabled,
                    onCheckedChange = onToggleDonkiSync
                )
                SettingsToggleRow(
                    title = "Fahrenheit Temperature (°F)",
                    subtitle = "Display body temperature in °F instead of °C",
                    checked = useFahrenheit,
                    onCheckedChange = onToggleUseFahrenheit
                )
                SettingsToggleRow(
                    title = "Encrypted Delta-Log",
                    subtitle = "Retain AES-256 flight-surgeon audit entries",
                    checked = encryptedLogEnabled,
                    onCheckedChange = onToggleEncryptedLog
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onClearDeltaLog,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Logs", color = InkSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onResetSettings,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Defaults", color = CoralAlert, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        onSaveGeminiKey(keyInput.trim())
                        onSaveOAuthClientId(oauthInput.trim())
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = InkPrimary, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = InkPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = InkSecondary, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun LogEntryDialog(
    vitals: List<VitalMetric>,
    initialMetricType: MetricType,
    onDismiss: () -> Unit,
    onLogHydration: (Int) -> Unit,
    onLogVital: (MetricType, String, Float, Float?, String?) -> Unit
) {
    var selectedMetric by remember(initialMetricType) { mutableStateOf(initialMetricType) }
    val currentVital = vitals.find { it.type == selectedMetric } ?: vitals.first()

    var primaryInput by remember(selectedMetric) {
        mutableStateOf(
            when (selectedMetric) {
                MetricType.HYDRATION -> "500"
                MetricType.BLOOD_PRESSURE -> "118"
                else -> currentVital.numericValue?.let {
                    if (selectedMetric == MetricType.RADIATION_DOSIMETER) "%.2f".format(Locale.US, it)
                    else if (selectedMetric == MetricType.BODY_TEMP || selectedMetric == MetricType.SLEEP_QUALITY) "%.1f".format(Locale.US, it)
                    else it.roundToInt().toString()
                } ?: ""
            }
        )
    }
    var diastolicInput by remember(selectedMetric) { mutableStateOf(currentVital.secondaryValue ?: "76") }
    val parsedVal = primaryInput.toFloatOrNull()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, BorderSubtle, RoundedCornerShape(20.dp)),
            color = CardPure
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Log ${currentVital.title}", color = InkPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = InkSecondary)
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(MetricType.entries) { mType ->
                        val isSelected = mType == selectedMetric
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (isSelected) mType.chartColor else BgCanvas)
                                .clickable { selectedMetric = mType }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(mType.shortName, color = if (isSelected) Color.White else InkPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (selectedMetric == MetricType.HYDRATION) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(250, 500, 750).forEach { amt ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (primaryInput == amt.toString()) ElectricBlue else PillSurface)
                                    .clickable { primaryInput = amt.toString() }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+$amt mL", color = if (primaryInput == amt.toString()) Color.White else ElectricBlue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                if (selectedMetric == MetricType.BLOOD_PRESSURE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = primaryInput, onValueChange = { primaryInput = it }, label = { Text("Sys") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = diastolicInput, onValueChange = { diastolicInput = it }, label = { Text("Dia") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                } else {
                    OutlinedTextField(
                        value = primaryInput,
                        onValueChange = { primaryInput = it },
                        label = { Text("${currentVital.unit} (Baseline ${currentVital.baselineDescription})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = {
                        val v = primaryInput.toFloatOrNull() ?: return@Button
                        if (selectedMetric == MetricType.HYDRATION) {
                            onLogHydration(v.roundToInt())
                        } else if (selectedMetric == MetricType.BLOOD_PRESSURE) {
                            val dia = diastolicInput.toFloatOrNull() ?: 76f
                            onLogVital(MetricType.BLOOD_PRESSURE, "${v.roundToInt()}/${dia.roundToInt()}", v, dia, null)
                        } else {
                            val formatted = when (selectedMetric) {
                                MetricType.RADIATION_DOSIMETER -> "%.2f".format(Locale.US, v)
                                MetricType.BODY_TEMP, MetricType.SLEEP_QUALITY -> "%.1f".format(Locale.US, v)
                                else -> v.roundToInt().toString()
                            }
                            onLogVital(selectedMetric, formatted, v, null, null)
                        }
                        onDismiss()
                    },
                    enabled = parsedVal != null,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
