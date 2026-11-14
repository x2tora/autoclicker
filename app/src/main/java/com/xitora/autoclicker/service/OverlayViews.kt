package com.xitora.autoclicker.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.*
import com.xitora.autoclicker.domain.ClickEngineCalculator
import com.xitora.autoclicker.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun Context.dpToPx(dp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)

private fun Context.dpToPxInt(dp: Float): Int = dpToPx(dp).roundToInt()

/**
 * Custom View matching the target crosshair reticle in the user's reference screenshot:
 * - Dark charcoal circular ring with 4 inward/outward crosshair ticks at 12, 3, 6, 9 o'clock
 * - Semi-transparent light fill so underlying UI elements stay clearly readable
 * - Coral-red centered target index number ('1', '2', ...)
 * - Optional dashed anti-bot random radius boundary around the center
 */
@SuppressLint("ViewConstructor")
class TargetReticleView(
    context: Context,
    var target: TargetPoint,
    var reticleSizeDp: Int,
    var opacityPercent: Int,
    var showAntiBotZoneGuide: Boolean,
    private val onMoved: (TargetPoint, Float, Float) -> Unit,
    private val onTap: (TargetPoint) -> Unit
) : View(context) {

    var isLockedForClicking: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#2B2D30")
    }

    private val innerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(66, 255, 255, 255)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D94B64")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val antiBotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(28, 58, 155, 220)
    }

    private val antiBotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpToPx(1.8f)
        color = Color.argb(185, 58, 155, 220)
        pathEffect = DashPathEffect(floatArrayOf(context.dpToPx(5f), context.dpToPx(4f)), 0f)
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var isDragging = false

    fun requiredWindowSizePx(): Int {
        val reticlePx = context.dpToPx(reticleSizeDp.toFloat())
        val tickOverflow = context.dpToPx(12f)
        val baseSize = reticlePx + tickOverflow
        val antiBotSize = if (target.antiBotRandomEnabled && showAntiBotZoneGuide) {
            context.dpToPx(target.randomRadiusDp.toFloat()) * 2f + context.dpToPx(10f)
        } else {
            0f
        }
        return max(baseSize, antiBotSize).roundToInt().coerceAtLeast(context.dpToPxInt(56f))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val sz = requiredWindowSizePx()
        setMeasuredDimension(sz, sz)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val alphaFactor = (opacityPercent.coerceIn(25, 100) / 100f)

        if (target.antiBotRandomEnabled && showAntiBotZoneGuide) {
            val zoneRadius = context.dpToPx(target.randomRadiusDp.toFloat()).coerceAtLeast(context.dpToPx(8f))
            antiBotFillPaint.alpha = (30 * alphaFactor).roundToInt()
            antiBotStrokePaint.alpha = (190 * alphaFactor).roundToInt()
            canvas.drawCircle(cx, cy, zoneRadius, antiBotFillPaint)
            canvas.drawCircle(cx, cy, zoneRadius, antiBotStrokePaint)
        }

        val ringRadius = context.dpToPx(reticleSizeDp.toFloat()) * 0.40f
        val strokeW = context.dpToPx(3.0f)
        ringPaint.strokeWidth = strokeW
        ringPaint.alpha = (242 * alphaFactor).roundToInt()
        innerFillPaint.alpha = (78 * alphaFactor).roundToInt()
        textPaint.alpha = (255 * alphaFactor).roundToInt()

        // Inner soft translucent circle
        canvas.drawCircle(cx, cy, ringRadius - strokeW * 0.3f, innerFillPaint)

        // Outer charcoal ring
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)

        // 4 crosshair ticks at 12, 3, 6, 9 o'clock extending slightly outside and inside
        val outerTick = context.dpToPx(4.8f)
        val innerTick = context.dpToPx(4.2f)
        // Top
        canvas.drawLine(cx, cy - ringRadius - outerTick, cx, cy - ringRadius + innerTick, ringPaint)
        // Bottom
        canvas.drawLine(cx, cy + ringRadius - innerTick, cx, cy + ringRadius + outerTick, ringPaint)
        // Left
        canvas.drawLine(cx - ringRadius - outerTick, cy, cx - ringRadius + innerTick, cy, ringPaint)
        // Right
        canvas.drawLine(cx + ringRadius - innerTick, cy, cx + ringRadius + outerTick, cy, ringPaint)

        // Centered target index label
        textPaint.textSize = context.dpToPx(reticleSizeDp * 0.38f)
        val fm = textPaint.fontMetrics
        val textY = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(target.order.toString(), cx, textY, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLockedForClicking) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                }
                if (isDragging) {
                    onMoved(target, dx, dy)
                    downRawX = event.rawX
                    downRawY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    onTap(target)
                }
                isDragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

/**
 * Floating vertical capsule control panel matching the uploaded reference screenshot
 * (without the yellow swipe button):
 * - Close (X)
 * - Settings (Gear)
 * - [Multi mode only] Add (+) & Remove (-)
 * - Play / Pause
 * - Status block: Elapsed time, Repeat status, Total click count
 */
@SuppressLint("ViewConstructor")
class FloatingControlPanelView(
    context: Context,
    private val onCloseClick: () -> Unit,
    private val onSettingsClick: () -> Unit,
    private val onAddTargetClick: () -> Unit,
    private val onRemoveTargetClick: () -> Unit,
    private val onToggleRunClick: () -> Unit,
    private val onPanelDrag: (Float, Float) -> Unit
) : LinearLayout(context) {

    private val closeBtn: IconCanvasButton
    private val settingsBtn: IconCanvasButton
    private val addBtn: IconCanvasButton
    private val removeBtn: IconCanvasButton
    private val playPauseBtn: PlayPauseCanvasButton
    private val elapsedLabel: TextView
    private val repeatLabel: TextView
    private val countLabel: TextView

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val padH = context.dpToPxInt(7f)
        val padV = context.dpToPxInt(10f)
        setPadding(padH, padV, padH, padV)

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpToPx(26f)
            setColor(Color.argb(244, 250, 251, 253))
            setStroke(context.dpToPxInt(1f), Color.argb(34, 36, 82, 122))
        }
        elevation = context.dpToPx(8f)

        val btnSize = context.dpToPxInt(38f)

        closeBtn = IconCanvasButton(context, IconKind.CLOSE).apply {
            layoutParams = LayoutParams(btnSize, btnSize)
            setOnClickListener { onCloseClick() }
        }
        settingsBtn = IconCanvasButton(context, IconKind.SETTINGS).apply {
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                topMargin = context.dpToPxInt(6f)
            }
            setOnClickListener { onSettingsClick() }
        }
        addBtn = IconCanvasButton(context, IconKind.ADD).apply {
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                topMargin = context.dpToPxInt(6f)
            }
            setOnClickListener { onAddTargetClick() }
        }
        removeBtn = IconCanvasButton(context, IconKind.REMOVE).apply {
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                topMargin = context.dpToPxInt(4f)
            }
            setOnClickListener { onRemoveTargetClick() }
        }
        playPauseBtn = PlayPauseCanvasButton(context).apply {
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                topMargin = context.dpToPxInt(8f)
            }
            setOnClickListener { onToggleRunClick() }
        }

        val textColor = Color.parseColor("#24527A")
        elapsedLabel = TextView(context).apply {
            text = "00:00"
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dpToPxInt(4f)
            }
        }
        repeatLabel = TextView(context).apply {
            text = "Unlimited"
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dpToPxInt(1f)
            }
        }
        countLabel = TextView(context).apply {
            text = "0"
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = context.dpToPxInt(1f)
            }
        }

        addView(closeBtn)
        addView(settingsBtn)
        addView(addBtn)
        addView(removeBtn)
        addView(playPauseBtn)
        addView(elapsedLabel)
        addView(repeatLabel)
        addView(countLabel)
    }

    fun updateState(
        mode: ClickMode,
        isRunning: Boolean,
        elapsedMs: Long,
        clickCount: Long,
        completedCycles: Long,
        activeConfig: ClickTimingConfig,
        isDarkSurface: Boolean
    ) {
        val showMultiControls = mode == ClickMode.MULTI
        addBtn.visibility = if (showMultiControls) VISIBLE else GONE
        removeBtn.visibility = if (showMultiControls) VISIBLE else GONE
        addBtn.isEnabled = !isRunning
        removeBtn.isEnabled = !isRunning
        addBtn.alpha = if (isRunning) 0.42f else 1f
        removeBtn.alpha = if (isRunning) 0.42f else 1f
        settingsBtn.isEnabled = !isRunning
        settingsBtn.alpha = if (isRunning) 0.48f else 1f

        playPauseBtn.isRunning = isRunning
        elapsedLabel.text = ClickEngineCalculator.formatElapsedMmSs(elapsedMs)
        repeatLabel.text = ClickEngineCalculator.formatRepeatStatusBadge(
            config = activeConfig,
            elapsedMs = elapsedMs,
            completedCycles = completedCycles
        )
        countLabel.text = clickCount.toString()

        val bg = background as? GradientDrawable
        if (isDarkSurface) {
            bg?.setColor(Color.argb(242, 32, 36, 44))
            bg?.setStroke(context.dpToPxInt(1f), Color.argb(65, 255, 255, 255))
            val labelCol = Color.parseColor("#DDE7F2")
            elapsedLabel.setTextColor(labelCol)
            repeatLabel.setTextColor(labelCol)
            countLabel.setTextColor(labelCol)
            closeBtn.customTint = Color.parseColor("#9CC2E6")
            settingsBtn.customTint = Color.parseColor("#9CC2E6")
        } else {
            bg?.setColor(Color.argb(246, 250, 251, 253))
            bg?.setStroke(context.dpToPxInt(1f), Color.argb(32, 36, 82, 122))
            val labelCol = Color.parseColor("#24527A")
            elapsedLabel.setTextColor(labelCol)
            repeatLabel.setTextColor(labelCol)
            countLabel.setTextColor(labelCol)
            closeBtn.customTint = null
            settingsBtn.customTint = null
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX
                downRawY = ev.rawY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    dragging = true
                    return true
                }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    dragging = true
                }
                if (dragging) {
                    onPanelDrag(dx, dy)
                    downRawX = event.rawX
                    downRawY = event.rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private enum class IconKind { CLOSE, SETTINGS, ADD, REMOVE }

    @SuppressLint("ViewConstructor")
    private class IconCanvasButton(
        context: Context,
        private val kind: IconKind
    ) : View(context) {
        var customTint: Int? = null
            set(value) {
                field = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = min(width, height) * 0.28f

            when (kind) {
                IconKind.CLOSE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = context.dpToPx(3.0f)
                    paint.color = customTint ?: Color.parseColor("#24527A")
                    canvas.drawLine(cx - r, cy - r, cx + r, cy + r, paint)
                    canvas.drawLine(cx + r, cy - r, cx - r, cy + r, paint)
                }
                IconKind.SETTINGS -> {
                    paint.color = customTint ?: Color.parseColor("#24527A")
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = context.dpToPx(2.3f)
                    val gearR = r * 0.78f
                    canvas.drawCircle(cx, cy, gearR * 0.48f, paint)
                    paint.style = Paint.Style.STROKE
                    canvas.drawCircle(cx, cy, gearR, paint)
                    val toothInner = gearR * 0.85f
                    val toothOuter = gearR * 1.28f
                    for (i in 0 until 8) {
                        val angle = Math.toRadians(i * 45.0)
                        val sx = cx + toothInner * kotlin.math.cos(angle).toFloat()
                        val sy = cy + toothInner * kotlin.math.sin(angle).toFloat()
                        val ex = cx + toothOuter * kotlin.math.cos(angle).toFloat()
                        val ey = cy + toothOuter * kotlin.math.sin(angle).toFloat()
                        canvas.drawLine(sx, sy, ex, ey, paint)
                    }
                }
                IconKind.ADD -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = context.dpToPx(3.3f)
                    paint.color = Color.parseColor("#3D9BE9")
                    canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                    canvas.drawLine(cx, cy - r, cx, cy + r, paint)
                }
                IconKind.REMOVE -> {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = context.dpToPx(3.3f)
                    paint.color = Color.parseColor("#D94B64")
                    canvas.drawLine(cx - r, cy, cx + r, cy, paint)
                }
            }
        }
    }

    private class PlayPauseCanvasButton(context: Context) : View(context) {
        var isRunning: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = min(width, height) * 0.34f

            if (isRunning) {
                paint.color = Color.parseColor("#E67E22")
                val barW = r * 0.44f
                val barH = r * 1.65f
                val gap = r * 0.32f
                val corner = context.dpToPx(2.5f)
                canvas.drawRoundRect(
                    cx - gap / 2f - barW,
                    cy - barH / 2f,
                    cx - gap / 2f,
                    cy + barH / 2f,
                    corner,
                    corner,
                    paint
                )
                canvas.drawRoundRect(
                    cx + gap / 2f,
                    cy - barH / 2f,
                    cx + gap / 2f + barW,
                    cy + barH / 2f,
                    corner,
                    corner,
                    paint
                )
            } else {
                paint.color = Color.parseColor("#3DA34D")
                path.reset()
                path.moveTo(cx - r * 0.65f, cy - r * 0.92f)
                path.lineTo(cx + r * 0.98f, cy)
                path.lineTo(cx - r * 0.65f, cy + r * 0.92f)
                path.close()
                paint.pathEffect = CornerPathEffect(context.dpToPx(3f))
                canvas.drawPath(path, paint)
            }
        }
    }
}

/**
 * Clean native modal card shown inside [WindowManager] when user taps the Gear icon
 * or taps an individual target reticle while paused.
 */
object OverlayModalFactory {

    fun buildTimingModal(
        context: Context,
        title: String,
        subtitle: String,
        initialConfig: ClickTimingConfig,
        showRepeatOptions: Boolean,
        isDark: Boolean,
        showDeleteButton: Boolean = false,
        onDelete: (() -> Unit)? = null,
        onCancel: () -> Unit,
        onSave: (ClickTimingConfig) -> Unit
    ): View {
        val bgColor = if (isDark) Color.parseColor("#1E2128") else Color.parseColor("#FFFFFF")
        val cardSurface = if (isDark) Color.parseColor("#272B35") else Color.parseColor("#F4F6FA")
        val textPrimary = if (isDark) Color.parseColor("#F1F4FA") else Color.parseColor("#181C24")
        val textSecondary = if (isDark) Color.parseColor("#9AA3B5") else Color.parseColor("#636D7E")
        val primaryBlue = Color.parseColor("#3482FF")
        val dangerRed = Color.parseColor("#E05263")

        val backdrop = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(145, 0, 0, 0))
            setOnClickListener { onCancel() }
        }

        val scroll = ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                context.dpToPxInt(332f),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            setOnClickListener { /* consume */ }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = context.dpToPxInt(18f)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dpToPx(24f)
                setColor(bgColor)
                setStroke(context.dpToPxInt(1f), Color.argb(35, 130, 145, 170))
            }
            setOnClickListener { /* consume */ }
        }

        card.addView(TextView(context).apply {
            text = title
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        card.addView(TextView(context).apply {
            text = subtitle
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, context.dpToPxInt(2f), 0, context.dpToPxInt(12f))
        })

        var selectedIntervalUnit = TimeUnitOption.MS
        val intervalMsInit = ClickEngineCalculator.toMillis(initialConfig.intervalValue, initialConfig.intervalUnit)
        val intervalInput = createNumericEditText(context, intervalMsInit.toString(), cardSurface, textPrimary)
        card.addView(makeSectionLabel(context, "클릭 간격 (ms)", textPrimary))
        card.addView(buildValueWithUnitSelector(context, intervalInput, selectedIntervalUnit, cardSurface, textPrimary, primaryBlue) {
            selectedIntervalUnit = it
        })

        var selectedDurationUnit = TimeUnitOption.MS
        val durationMsInit = ClickEngineCalculator.toMillis(initialConfig.durationValue, initialConfig.durationUnit)
        val durationInput = createNumericEditText(context, durationMsInit.toString(), cardSurface, textPrimary)
        card.addView(makeSectionLabel(context, "클릭 지속시간 (ms)", textPrimary))
        card.addView(buildValueWithUnitSelector(context, durationInput, selectedDurationUnit, cardSurface, textPrimary, primaryBlue) {
            selectedDurationUnit = it
        })

        var selectedRepeatMode = initialConfig.repeatMode
        var selectedRepeatDurationUnit = TimeUnitOption.MS
        val repeatDurationMsInit = ClickEngineCalculator.toMillis(initialConfig.repeatDurationValue, initialConfig.repeatDurationUnit)
        val repeatCountInput = createNumericEditText(context, initialConfig.repeatCount.toString(), cardSurface, textPrimary)
        val repeatDurationInput = createNumericEditText(context, repeatDurationMsInit.toString(), cardSurface, textPrimary)

        if (showRepeatOptions) {
            card.addView(makeSectionLabel(context, "반복 조건", textPrimary))
            val repeatModeContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = context.dpToPxInt(8f) }
            }
            val repeatValueHolder = FrameLayout(context)

            fun refreshRepeatDetail() {
                repeatValueHolder.removeAllViews()
                when (selectedRepeatMode) {
                    RepeatMode.INFINITE -> {
                        repeatValueHolder.addView(TextView(context).apply {
                            text = "정지 버튼을 누를 때까지 계속 클릭합니다."
                            setTextColor(textSecondary)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            setPadding(context.dpToPxInt(4f), 0, 0, context.dpToPxInt(8f))
                        })
                    }
                    RepeatMode.COUNT -> {
                        val row = LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            addView(repeatCountInput.apply {
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            })
                            addView(TextView(context).apply {
                                text = "회 반복 후 자동 정지"
                                setTextColor(textSecondary)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                                setPadding(context.dpToPxInt(8f), 0, 0, 0)
                            })
                        }
                        repeatValueHolder.addView(row)
                    }
                    RepeatMode.DURATION -> {
                        repeatValueHolder.addView(
                            buildValueWithUnitSelector(
                                context,
                                repeatDurationInput,
                                selectedRepeatDurationUnit,
                                cardSurface,
                                textPrimary,
                                primaryBlue
                            ) { selectedRepeatDurationUnit = it }
                        )
                    }
                }
            }

            val repeatChoices = listOf(
                RepeatMode.INFINITE to "무한",
                RepeatMode.DURATION to "시간",
                RepeatMode.COUNT to "횟수"
            )
            val chipViews = mutableListOf<Pair<RepeatMode, TextView>>()
            repeatChoices.forEachIndexed { idx, (mode, label) ->
                val tv = createPillChip(context, label, mode == selectedRepeatMode, cardSurface, textPrimary, primaryBlue).apply {
                    layoutParams = LinearLayout.LayoutParams(0, context.dpToPxInt(34f), 1f).apply {
                        if (idx > 0) marginStart = context.dpToPxInt(6f)
                    }
                    setOnClickListener {
                        selectedRepeatMode = mode
                        chipViews.forEach { (m, view) ->
                            stylePillChip(context, view, m == selectedRepeatMode, cardSurface, textPrimary, primaryBlue)
                        }
                        refreshRepeatDetail()
                    }
                }
                chipViews.add(mode to tv)
                repeatModeContainer.addView(tv)
            }
            card.addView(repeatModeContainer)
            card.addView(repeatValueHolder)
            refreshRepeatDetail()
        }

        // Anti-bot random area toggle + radius slider
        var antiBotEnabled = initialConfig.antiBotRandomEnabled
        var randomRadius = initialConfig.randomRadiusDp.coerceIn(8, 80)

        card.addView(makeSectionLabel(context, "봇 방지 랜덤 터치", textPrimary))
        val antiBotCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dpToPxInt(12f)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dpToPx(14f)
                setColor(cardSurface)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = context.dpToPxInt(14f) }
        }

        val switchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val switchLabel = TextView(context).apply {
            text = "선택 영역 내 랜덤 좌표 클릭"
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val radiusLabel = TextView(context).apply {
            text = "랜덤 영역 반경: ${randomRadius}dp"
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, context.dpToPxInt(8f), 0, context.dpToPxInt(4f))
            visibility = if (antiBotEnabled) View.VISIBLE else View.GONE
        }
        val radiusSeek = SeekBar(context).apply {
            max = 72 // 8..80
            progress = (randomRadius - 8).coerceIn(0, 72)
            visibility = if (antiBotEnabled) View.VISIBLE else View.GONE
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, p: Int, fromUser: Boolean) {
                    randomRadius = p + 8
                    radiusLabel.text = "랜덤 영역 반경: ${randomRadius}dp"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        val toggleBtn = createPillChip(
            context,
            if (antiBotEnabled) "켜짐" else "꺼짐",
            antiBotEnabled,
            bgColor,
            textPrimary,
            primaryBlue
        ).apply {
            layoutParams = LinearLayout.LayoutParams(context.dpToPxInt(64f), context.dpToPxInt(32f))
            setOnClickListener {
                antiBotEnabled = !antiBotEnabled
                text = if (antiBotEnabled) "켜짐" else "꺼짐"
                stylePillChip(context, this, antiBotEnabled, bgColor, textPrimary, primaryBlue)
                radiusLabel.visibility = if (antiBotEnabled) View.VISIBLE else View.GONE
                radiusSeek.visibility = if (antiBotEnabled) View.VISIBLE else View.GONE
            }
        }
        switchRow.addView(switchLabel)
        switchRow.addView(toggleBtn)
        antiBotCard.addView(switchRow)
        antiBotCard.addView(radiusLabel)
        antiBotCard.addView(radiusSeek)
        card.addView(antiBotCard)

        // Action buttons
        if (showDeleteButton && onDelete != null) {
            card.addView(createActionButton(context, "이 타겟 지점 삭제", dangerRed, Color.WHITE).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.dpToPxInt(40f)
                ).apply { bottomMargin = context.dpToPxInt(8f) }
                setOnClickListener { onDelete() }
            })
        }

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        btnRow.addView(createActionButton(context, "취소", cardSurface, textPrimary).apply {
            layoutParams = LinearLayout.LayoutParams(0, context.dpToPxInt(42f), 1f)
            setOnClickListener { onCancel() }
        })
        btnRow.addView(createActionButton(context, "저장", primaryBlue, Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(0, context.dpToPxInt(42f), 1f).apply {
                marginStart = context.dpToPxInt(10f)
            }
            setOnClickListener {
                val intervalVal = intervalInput.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: initialConfig.intervalValue
                val durationVal = durationInput.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: initialConfig.durationValue
                val countVal = repeatCountInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: initialConfig.repeatCount
                val repDurVal = repeatDurationInput.text.toString().toLongOrNull()?.coerceAtLeast(1L) ?: initialConfig.repeatDurationValue
                onSave(
                    initialConfig.copy(
                        intervalValue = intervalVal,
                        intervalUnit = selectedIntervalUnit,
                        durationValue = durationVal,
                        durationUnit = selectedDurationUnit,
                        repeatMode = selectedRepeatMode,
                        repeatCount = countVal,
                        repeatDurationValue = repDurVal,
                        repeatDurationUnit = selectedRepeatDurationUnit,
                        antiBotRandomEnabled = antiBotEnabled,
                        randomRadiusDp = randomRadius
                    )
                )
            }
        })
        card.addView(btnRow)

        scroll.addView(card)
        backdrop.addView(scroll)
        return backdrop
    }

    private fun makeSectionLabel(context: Context, label: String, textColor: Int): TextView {
        return TextView(context).apply {
            text = label
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, context.dpToPxInt(6f), 0, context.dpToPxInt(5f))
        }
    }

    private fun createNumericEditText(context: Context, initialText: String, bgCol: Int, textColor: Int): EditText {
        return EditText(context).apply {
            setText(initialText)
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            val padH = context.dpToPxInt(12f)
            val padV = context.dpToPxInt(8f)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dpToPx(12f)
                setColor(bgCol)
            }
        }
    }

    private fun buildValueWithUnitSelector(
        context: Context,
        editText: EditText,
        initialUnit: TimeUnitOption,
        chipBg: Int,
        textColor: Int,
        activeColor: Int,
        onUnitSelected: (TimeUnitOption) -> Unit
    ): LinearLayout {
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = context.dpToPxInt(8f) }
        }
        wrapper.addView(editText.apply {
            layoutParams = LinearLayout.LayoutParams(0, context.dpToPxInt(40f), 1f)
        })

        onUnitSelected(TimeUnitOption.MS)
        val msBadge = createPillChip(context, "ms", false, chipBg, textColor, activeColor).apply {
            layoutParams = LinearLayout.LayoutParams(context.dpToPxInt(52f), context.dpToPxInt(38f)).apply {
                marginStart = context.dpToPxInt(8f)
            }
        }
        wrapper.addView(msBadge)
        return wrapper
    }

    private fun createPillChip(
        context: Context,
        label: String,
        selected: Boolean,
        inactiveBg: Int,
        inactiveText: Int,
        activeBg: Int
    ): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            stylePillChip(context, this, selected, inactiveBg, inactiveText, activeBg)
        }
    }

    private fun stylePillChip(
        context: Context,
        view: TextView,
        selected: Boolean,
        inactiveBg: Int,
        inactiveText: Int,
        activeBg: Int
    ) {
        view.setTextColor(if (selected) Color.WHITE else inactiveText)
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpToPx(10f)
            setColor(if (selected) activeBg else inactiveBg)
        }
    }

    private fun createActionButton(context: Context, label: String, bgColor: Int, textColor: Int): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dpToPx(14f)
                setColor(bgColor)
            }
        }
    }
}
