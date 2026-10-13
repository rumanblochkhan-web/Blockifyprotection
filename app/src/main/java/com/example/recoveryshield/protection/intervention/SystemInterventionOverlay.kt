package com.example.recoveryshield.protection.intervention

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.R
import com.example.recoveryshield.protection.util.BlockifyPerfTracker

/**
 * Pure floating system overlay that renders directly over the user's active screen
 * (e.g. YouTube, Chrome, browser, Launcher) using WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY.
 *
 * Pre-warmed view architecture ensures sub-millisecond attachment without view inflation latency.
 */
class SystemInterventionOverlay(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "SystemInterventionOverlay"
        @Volatile
        var activeDiagnosticContext: com.example.recoveryshield.protection.util.BlockifyLatencyTracker.EventDiagnosticContext? = null
    }

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var countDownTimer: CountDownTimer? = null

    enum class OverlayState { IDLE, REQUESTED, ATTACHING, DISPLAYED, DISMISSING }

    @Volatile
    private var currentState = OverlayState.IDLE

    @Volatile
    private var isDisplayed = false
    @Volatile
    private var isRequestedOrDisplayed = false
    @Volatile
    private var lastRequestedTimestamp = 0L

    // Pre-warmed UI elements
    private var prewarmedRoot: FrameLayout? = null
    private var prewarmedCountdownView: CountdownCircleView? = null
    private var prewarmedTitleView: TextView? = null
    private var prewarmedSubtitleView: TextView? = null
    private var prewarmedCloseBtn: TextView? = null
    private var prewarmedLeaveButton: TextView? = null
    private var prewarmedBottomLabel: TextView? = null

    // Interactive Emergency Open elements
    private var emergencyOpenRoot: FrameLayout? = null
    private val emergencyCodeBoxes = ArrayList<TextView>()
    private var emergencyEditText: android.widget.EditText? = null
    private var emergencyErrorBanner: TextView? = null
    private var emergencySuccessBanner: TextView? = null
    private var emergencyUnlockBtn: TextView? = null
    private var currentEnteredCode: String = ""

    // Pre-allocated LayoutParams
    private val overlayParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }

    init {
        // Pre-warm views on initialization so the first trigger has zero view inflation latency
        prewarm()
    }

    fun prewarm() {
        if (prewarmedRoot != null) return
        if (Looper.myLooper() == Looper.getMainLooper()) {
            buildPrewarmedHierarchy()
        } else {
            mainHandler.post { buildPrewarmedHierarchy() }
        }
    }

    private fun buildPrewarmedHierarchy() {
        if (prewarmedRoot != null) return
        val context = service

        try {
            val root = FrameLayout(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val cardBg = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dpToPx(context, 24f)
                }
                background = cardBg
                elevation = dpToPx(context, 16f)
                isClickable = true

                val horizontalPadding = dpToPx(context, 24f).toInt()
                val verticalPadding = dpToPx(context, 24f).toInt()
                setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

                val widthPx = dpToPx(context, 320f).toInt()
                val lp = FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
                layoutParams = lp
            }

            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val closeBtn = TextView(context).apply {
                text = "✕"
                textSize = 18f
                setTextColor(Color.parseColor("#475569"))
                gravity = Gravity.CENTER
                val size = dpToPx(context, 32f).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                val btnBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#F1F5F9"))
                    cornerRadius = dpToPx(context, 16f)
                }
                background = btnBg
            }
            topRow.addView(closeBtn)
            card.addView(topRow)

            val shieldIcon = ImageView(context).apply {
                setImageResource(R.drawable.ic_safemind_shield)
                val iconSize = dpToPx(context, 68f).toInt()
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    topMargin = dpToPx(context, 4f).toInt()
                }
            }
            card.addView(shieldIcon)

            val titleView = TextView(context).apply {
                text = "Stop"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#0F172A"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(context, 14f).toInt()
                }
            }
            card.addView(titleView)

            val subtitleView = TextView(context).apply {
                text = "This content can harm you.\nYou're stronger than this."
                textSize = 14f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dpToPx(context, 6f).toInt()
                }
            }
            card.addView(subtitleView)

            val countdownView = CountdownCircleView(context).apply {
                val size = dpToPx(context, 130f).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    topMargin = dpToPx(context, 18f).toInt()
                    bottomMargin = dpToPx(context, 16f).toInt()
                }
            }
            card.addView(countdownView)

            val bottomLabel = TextView(context).apply {
                text = "Redirecting automatically..."
                textSize = 12f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
            }
            card.addView(bottomLabel)

            val leaveButton = TextView(context).apply {
                text = "Leave Now"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                val btnHeight = dpToPx(context, 44f).toInt()
                val btnLp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    btnHeight
                ).apply {
                    topMargin = dpToPx(context, 14f).toInt()
                }
                layoutParams = btnLp
                val btnBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#2563EB"))
                    cornerRadius = dpToPx(context, 12f)
                }
                background = btnBg
            }
            card.addView(leaveButton)

            root.addView(card)

            prewarmedRoot = root
            prewarmedCountdownView = countdownView
            prewarmedTitleView = titleView
            prewarmedSubtitleView = subtitleView
            prewarmedCloseBtn = closeBtn
            prewarmedLeaveButton = leaveButton
            prewarmedBottomLabel = bottomLabel
            Log.i("BLOCKIFY_TRIGGER", "SystemInterventionOverlay: prewarm complete, views initialized in memory")
        } catch (e: Exception) {
            Log.e(TAG, "Error building prewarmed hierarchy: ${e.message}", e)
        }
    }

    private fun buildEmergencyOpenHierarchy(onNavigateBack: () -> Unit): FrameLayout {
        val context = service
        emergencyCodeBoxes.clear()
        currentEnteredCode = ""

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#FF0F172A")) // 100% solid dark slate privacy blackout
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
            isFillViewport = true
        }

        val scrollCenter = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val cardBg = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dpToPx(context, 24f)
            }
            background = cardBg
            elevation = dpToPx(context, 16f)
            isClickable = true

            val hPad = dpToPx(context, 20f).toInt()
            val vPad = dpToPx(context, 20f).toInt()
            setPadding(hPad, vPad, hPad, vPad)

            val widthPx = dpToPx(context, 340f).toInt()
            val lp = FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                val m = dpToPx(context, 16f).toInt()
                setMargins(m, m, m, m)
            }
            layoutParams = lp
        }

        // 1. Top row: Red Shield icon badge on left, Close button on right
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val badgeContainer = FrameLayout(context).apply {
            val size = dpToPx(context, 40f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FEE2E2"))
                cornerRadius = dpToPx(context, 20f)
            }
            background = bg
        }
        val shieldIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_safemind_shield)
            val iconSize = dpToPx(context, 24f).toInt()
            val lp = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.CENTER
            }
            layoutParams = lp
            setColorFilter(Color.parseColor("#DC2626"))
        }
        badgeContainer.addView(shieldIcon)
        topRow.addView(badgeContainer)

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        topRow.addView(spacer)

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            val size = dpToPx(context, 34f).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F5F9"))
                cornerRadius = dpToPx(context, 17f)
            }
            background = btnBg
            setOnClickListener {
                dismiss()
                onNavigateBack()
            }
        }
        topRow.addView(closeBtn)
        card.addView(topRow)

        // 2. Title & Subtitle
        val titleView = TextView(context).apply {
            text = "Emergency Open"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#0F172A"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 12f).toInt()
            }
            layoutParams = lp
        }
        card.addView(titleView)

        val subtitleView = TextView(context).apply {
            text = "Blockify App Info is locked to protect your privacy.\nEnter your 8-digit code to open."
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 4f).toInt()
            }
            layoutParams = lp
        }
        card.addView(subtitleView)

        // 3. 8 Code Display Boxes
        val boxesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 14f).toInt()
                bottomMargin = dpToPx(context, 10f).toInt()
            }
            layoutParams = lp
        }

        for (i in 0 until 8) {
            val box = TextView(context).apply {
                text = ""
                textSize = 17f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#0F172A"))
                gravity = Gravity.CENTER
                val bWidth = dpToPx(context, 32f).toInt()
                val bHeight = dpToPx(context, 40f).toInt()
                val bMargin = dpToPx(context, 2f).toInt()
                val lp = LinearLayout.LayoutParams(bWidth, bHeight).apply {
                    setMargins(bMargin, 0, bMargin, 0)
                }
                layoutParams = lp
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#F8FAFC"))
                    setStroke(dpToPx(context, 1.5f).toInt(), Color.parseColor("#CBD5E1"))
                    cornerRadius = dpToPx(context, 8f)
                }
                background = bg
            }
            emergencyCodeBoxes.add(box)
            boxesContainer.addView(box)
        }
        card.addView(boxesContainer)

        fun updateBoxes(code: String) {
            currentEnteredCode = code.uppercase().take(8)
            for (i in 0 until 8) {
                if (i < emergencyCodeBoxes.size) {
                    val box = emergencyCodeBoxes[i]
                    if (i < currentEnteredCode.length) {
                        box.text = currentEnteredCode[i].toString()
                        val bg = GradientDrawable().apply {
                            setColor(Color.parseColor("#FEF2F2"))
                            setStroke(dpToPx(context, 1.5f).toInt(), Color.parseColor("#DC2626"))
                            cornerRadius = dpToPx(context, 8f)
                        }
                        box.background = bg
                    } else {
                        box.text = ""
                        val bg = GradientDrawable().apply {
                            setColor(Color.parseColor("#F8FAFC"))
                            setStroke(dpToPx(context, 1.5f).toInt(), Color.parseColor("#CBD5E1"))
                            cornerRadius = dpToPx(context, 8f)
                        }
                        box.background = bg
                    }
                }
            }
            emergencyErrorBanner?.visibility = View.GONE
        }

        // 4. Input EditText (for hardware or soft keyboard typing and pasting)
        val editTextInput = android.widget.EditText(context).apply {
            hint = "Tap to type or paste 8-character code"
            textSize = 13f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.parseColor("#94A3B8"))
            maxLines = 1
            filters = arrayOf(android.text.InputFilter.LengthFilter(8), android.text.InputFilter.AllCaps())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 38f).toInt()
            ).apply {
                bottomMargin = dpToPx(context, 8f).toInt()
            }
            layoutParams = lp
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F5F9"))
                cornerRadius = dpToPx(context, 8f)
            }
            background = bg
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val newCode = s?.toString()?.uppercase() ?: ""
                    updateBoxes(newCode)
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        emergencyEditText = editTextInput
        card.addView(editTextInput)

        // 5. On-Screen Numeric Keypad + Quick Code Button for effortless one-tap unlock
        val keypadContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("CLR", "0", "⌫")
        )

        for (row in rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(context, 36f).toInt()
                ).apply {
                    topMargin = dpToPx(context, 4f).toInt()
                }
                layoutParams = lp
            }

            for (key in row) {
                val btn = TextView(context).apply {
                    text = key
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#1E293B"))
                    gravity = Gravity.CENTER
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        val m = dpToPx(context, 3f).toInt()
                        setMargins(m, 0, m, 0)
                    }
                    layoutParams = lp
                    val bg = GradientDrawable().apply {
                        setColor(Color.parseColor("#F1F5F9"))
                        cornerRadius = dpToPx(context, 8f)
                    }
                    background = bg
                    isClickable = true
                    setOnClickListener {
                        when (key) {
                            "CLR" -> {
                                updateBoxes("")
                                editTextInput.setText("")
                            }
                            "⌫" -> {
                                if (currentEnteredCode.isNotEmpty()) {
                                    val shortened = currentEnteredCode.dropLast(1)
                                    updateBoxes(shortened)
                                    editTextInput.setText(shortened)
                                }
                            }
                            else -> {
                                if (currentEnteredCode.length < 8) {
                                    val updated = currentEnteredCode + key
                                    updateBoxes(updated)
                                    editTextInput.setText(updated)
                                }
                            }
                        }
                    }
                }
                rowLayout.addView(btn)
            }
            keypadContainer.addView(rowLayout)
        }

        // Quick Code button: [ ⚡ Quick Fill Code: BLOCKIFY ]
        val quickCodeBtn = TextView(context).apply {
            text = "⚡ Quick Fill: BLOCKIFY"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#2563EB"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 32f).toInt()
            ).apply {
                topMargin = dpToPx(context, 8f).toInt()
            }
            layoutParams = lp
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#EFF6FF"))
                setStroke(dpToPx(context, 1f).toInt(), Color.parseColor("#BFDBFE"))
                cornerRadius = dpToPx(context, 8f)
            }
            background = bg
            setOnClickListener {
                updateBoxes("BLOCKIFY")
                editTextInput.setText("BLOCKIFY")
            }
        }
        keypadContainer.addView(quickCodeBtn)
        card.addView(keypadContainer)

        // 6. Error & Success Banners
        val errorBanner = TextView(context).apply {
            text = "Invalid emergency code. Please enter valid 8-digit code or tap Quick Fill."
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#DC2626"))
            gravity = Gravity.CENTER
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 8f).toInt()
            }
            layoutParams = lp
        }
        emergencyErrorBanner = errorBanner
        card.addView(errorBanner)

        val successBanner = TextView(context).apply {
            text = "✓ Access Authorized! Opening Blockify App Info..."
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#16A34A"))
            gravity = Gravity.CENTER
            visibility = View.GONE
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 8f).toInt()
            }
            layoutParams = lp
        }
        emergencySuccessBanner = successBanner
        card.addView(successBanner)

        // 7. Primary Action Button: "Unlock App Info"
        val unlockBtn = TextView(context).apply {
            text = "Unlock App Info"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val btnHeight = dpToPx(context, 44f).toInt()
            val btnLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                btnHeight
            ).apply {
                topMargin = dpToPx(context, 12f).toInt()
            }
            layoutParams = btnLp
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#DC2626"))
                cornerRadius = dpToPx(context, 12f)
            }
            background = btnBg
            setOnClickListener {
                val code = currentEnteredCode.trim().uppercase()
                if (com.blockify.emergencystop.EmergencyCodeVault.isAuthorizedCode(code)) {
                    errorBanner.visibility = View.GONE
                    successBanner.visibility = View.VISIBLE
                    isEnabled = false
                    com.example.recoveryshield.protection.intervention.EmergencyOpenSession.authorizeAppInfoAccess()
                    mainHandler.postDelayed({
                        dismiss()
                    }, 500L)
                } else {
                    errorBanner.visibility = View.VISIBLE
                    errorBanner.text = if (code.length < 8) "Code must be 8 characters. Please enter complete code." else "Invalid emergency code '$code'. Try again or use BLOCKIFY."
                    startVibration()
                }
            }
        }
        emergencyUnlockBtn = unlockBtn
        card.addView(unlockBtn)

        // 8. Cancel / Return to Home
        val cancelBtn = TextView(context).apply {
            text = "Cancel and return to Home"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 10f).toInt()
            }
            layoutParams = lp
            setOnClickListener {
                dismiss()
                onNavigateBack()
            }
        }
        card.addView(cancelBtn)

        scrollCenter.addView(card)
        scrollView.addView(scrollCenter)
        root.addView(scrollView)

        return root
    }

    /**
     * Checks whether the overlay is actively displayed on screen.
     * Self-heals if state says DISPLAYED or REQUESTED but root view is not attached or request timed out.
     */
    fun isShowing(): Boolean {
        val now = System.currentTimeMillis()
        val root = prewarmedRoot
        val eRoot = emergencyOpenRoot

        if (currentState == OverlayState.DISPLAYED) {
            val attached = (root != null && root.isAttachedToWindow) || (eRoot != null && eRoot.isAttachedToWindow)
            if (!attached) {
                Log.w("BLOCKIFY_OVERLAY_RUNTIME", "Overlay state DISPLAYED but view not attached to window. Self-healing to IDLE.")
                cleanupState()
                return false
            }
            return true
        }

        if (currentState == OverlayState.REQUESTED || currentState == OverlayState.ATTACHING) {
            if ((now - lastRequestedTimestamp) > 500L) {
                Log.w("BLOCKIFY_OVERLAY_RUNTIME", "Overlay state $currentState timed out after 500ms without display. Self-healing to IDLE.")
                cleanupState()
                return false
            }
            return true
        }

        return false
    }

    fun show(
        isAntiTamper: Boolean = false,
        customMessage: String? = null,
        customTitle: String? = null,
        durationMillis: Long = 3000L,
        navigateAwayOnFinish: Boolean = true,
        isEmergencyOpen: Boolean = false,
        onNavigateBack: () -> Unit = {},
        onOpenEmergencyPrompt: (() -> Unit)? = null
    ): Boolean {
        BlockifyPerfTracker.onOverlayCreationStarted()
        val currentDiag = activeDiagnosticContext
        currentDiag?.markOverlayCreateStart()

        if (isShowing()) {
            Log.w("BLOCKIFY_TRIGGER", "SystemInterventionOverlay: already requested or displayed, skipping duplicate show")
            return false
        }

        lastRequestedTimestamp = System.currentTimeMillis()
        currentState = OverlayState.REQUESTED
        isRequestedOrDisplayed = true

        fun executeShow(): Boolean {
            val timestamp = System.currentTimeMillis()
            Log.i("BLOCKIFY_OVERLAY_RUNTIME", """
                |--- BLOCKIFY_OVERLAY_RUNTIME executeShow ---
                |timestamp: $timestamp
                |overlayShowEntered: true
                |mainThreadEntered: true
                |windowManagerObtained: ${windowManager != null}
                |currentState: $currentState
                |isAntiTamper: $isAntiTamper
                |isEmergencyOpen: $isEmergencyOpen
            """.trimMargin())

            val attachedRoot = if (isEmergencyOpen) emergencyOpenRoot else prewarmedRoot
            if (currentState == OverlayState.DISPLAYED && isDisplayed && attachedRoot?.isAttachedToWindow == true) {
                Log.w("BLOCKIFY_TRIGGER", "SystemInterventionOverlay: already displayed, skipping duplicate show in main thread")
                return true
            }

            try {
                val targetRoot: View
                val targetParams: WindowManager.LayoutParams

                if (isEmergencyOpen) {
                    val eRoot = buildEmergencyOpenHierarchy(onNavigateBack)
                    emergencyOpenRoot = eRoot
                    targetRoot = eRoot

                    targetParams = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.CENTER
                        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    }
                } else {
                    // Ensure hierarchy is built for normal intervention
                    if (prewarmedRoot == null || prewarmedCountdownView == null) {
                        buildPrewarmedHierarchy()
                    }
                    currentDiag?.markOverlayCreateEnd()

                    val rootView = prewarmedRoot
                    val countdownView = prewarmedCountdownView
                    if (rootView == null || countdownView == null) {
                        Log.e("BLOCKIFY_TRIGGER", "SystemInterventionOverlay: prewarmed root or countdown view unavailable after prewarm attempt")
                        cleanupState()
                        return false
                    }

                    val titleView = prewarmedTitleView
                    val subtitleView = prewarmedSubtitleView
                    val closeBtn = prewarmedCloseBtn
                    val leaveBtn = prewarmedLeaveButton
                    val bottomLabel = prewarmedBottomLabel

                    rootView.setBackgroundColor(Color.TRANSPARENT)
                    countdownView.visibility = View.VISIBLE
                    bottomLabel?.visibility = View.VISIBLE
                    val btnBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#2563EB"))
                        cornerRadius = dpToPx(service, 12f)
                    }
                    leaveBtn?.background = btnBg

                    // Bind text and state
                    titleView?.text = when {
                        !customTitle.isNullOrBlank() -> customTitle
                        isAntiTamper -> "Protection Active"
                        !customMessage.isNullOrBlank() && customMessage.startsWith("Blocked") -> "Access Blocked"
                        else -> "Stop"
                    }

                    subtitleView?.text = when {
                        !customMessage.isNullOrBlank() -> customMessage
                        isAntiTamper -> "Blockify is actively protecting you.\nTampering with protection is restricted."
                        else -> "This content can harm you.\nYou're stronger than this."
                    }

                    bottomLabel?.text = if (navigateAwayOnFinish) "Redirecting automatically..." else "Dismissing automatically..."
                    leaveBtn?.text = if (navigateAwayOnFinish) "Leave Now" else "Dismiss"

                    // Bind click listeners
                    val dismissAndNavigate = {
                        dismiss()
                        if (navigateAwayOnFinish) {
                            onNavigateBack()
                        }
                    }
                    rootView.setOnClickListener { dismissAndNavigate() }
                    closeBtn?.setOnClickListener { dismissAndNavigate() }
                    leaveBtn?.setOnClickListener { dismissAndNavigate() }

                    countdownView.update(((durationMillis + 999L) / 1000L).toInt(), 0f)

                    targetRoot = rootView
                    targetParams = overlayParams
                }

                // Attach to window manager
                BlockifyPerfTracker.onAddViewAttempted()
                currentDiag?.markAddViewStart()

                // Remove root if previously attached to window manager cleanly
                if (targetRoot.isAttachedToWindow) {
                    try { windowManager.removeViewImmediate(targetRoot) } catch (_: Exception) {}
                }

                Log.i("BLOCKIFY_OVERLAY_RUNTIME", "Attempting WindowManager.addView...")
                currentState = OverlayState.ATTACHING
                windowManager.addView(targetRoot, targetParams)

                currentState = OverlayState.DISPLAYED
                isDisplayed = true
                isRequestedOrDisplayed = true

                val isAttached = targetRoot.isAttachedToWindow
                Log.i("BLOCKIFY_OVERLAY_RUNTIME", """
                    |--- BLOCKIFY_OVERLAY_RUNTIME SUCCESS ---
                    |timestamp: ${System.currentTimeMillis()}
                    |addViewAttempted: true
                    |addViewSucceeded: true
                    |overlayState: DISPLAYED
                    |isAttachedToWindow: $isAttached
                    |isEmergencyOpen: $isEmergencyOpen
                """.trimMargin())

                BlockifyPerfTracker.onAddViewSuccessful()
                currentDiag?.markAddViewEnd()
                currentDiag?.markPopupVisible()

                currentDiag?.logDiagnosticSummary(
                    isOverlayShowing = true,
                    isInterventionActive = true,
                    isCountingDown = !isEmergencyOpen
                )

                // Start haptic vibration immediately when popup appears
                startVibration()

                // Start countdown timer only for standard interventions
                if (!isEmergencyOpen) {
                    val cView = prewarmedCountdownView
                    if (cView != null) {
                        startCountdownTimer(
                            durationMillis = durationMillis,
                            countdownView = cView,
                            navigateAwayOnFinish = navigateAwayOnFinish,
                            onNavigateBack = onNavigateBack
                        )
                    }
                }
                BlockifyPerfTracker.onPopupRenderReached()
                return true
            } catch (e: Exception) {
                Log.e("BLOCKIFY_OVERLAY_RUNTIME", """
                    |--- BLOCKIFY_OVERLAY_RUNTIME FAILURE ---
                    |timestamp: ${System.currentTimeMillis()}
                    |addViewAttempted: true
                    |addViewSucceeded: false
                    |overlayState: IDLE
                    |isAttachedToWindow: false
                    |exceptionClass: ${e.javaClass.simpleName}
                    |exceptionMessage: ${e.message}
                """.trimMargin(), e)

                cleanupState()
                if (navigateAwayOnFinish) onNavigateBack()
                return false
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            return executeShow()
        } else {
            val latch = java.util.concurrent.CountDownLatch(1)
            var showResult = false
            mainHandler.post {
                try {
                    showResult = executeShow()
                } finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(1000L, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                Log.e("BLOCKIFY_OVERLAY_RUNTIME", "Latch await interrupted/timed out: ${e.message}")
                cleanupState()
                return false
            }
            return showResult
        }
    }

    private fun startCountdownTimer(
        durationMillis: Long,
        countdownView: CountdownCircleView,
        navigateAwayOnFinish: Boolean,
        onNavigateBack: () -> Unit
    ) {
        countDownTimer?.cancel()
        val totalMillis = durationMillis
        countDownTimer = object : CountDownTimer(totalMillis, 40L) {
            override fun onTick(millisUntilFinished: Long) {
                if (!isDisplayed) {
                    cancel()
                    return
                }
                val progress = 1f - (millisUntilFinished.toFloat() / totalMillis)
                val secondsRemaining = ((millisUntilFinished + 999L) / 1000L).toInt().coerceIn(1, (totalMillis / 1000L).toInt().coerceAtLeast(1))
                countdownView.update(secondsRemaining, progress)
            }

            override fun onFinish() {
                countdownView.update(0, 1f)
                dismiss()
                if (navigateAwayOnFinish) {
                    onNavigateBack()
                }
            }
        }.start()
    }

    private fun cleanupState() {
        countDownTimer?.cancel()
        countDownTimer = null
        currentState = OverlayState.IDLE
        isDisplayed = false
        isRequestedOrDisplayed = false
        stopVibration()
        com.example.recoveryshield.protection.intervention.EmergencyOpenSession.isPromptVisible = false
    }

    fun dismiss() {
        val dismissAction = {
            currentState = OverlayState.DISMISSING
            cleanupState()

            val root = prewarmedRoot
            if (root != null && root.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(root)
                    Log.i("BLOCKIFY_TRIGGER", "SystemInterventionOverlay: removeViewImmediate success, overlay removed")
                } catch (e: Exception) {
                    Log.w("BLOCKIFY_TRIGGER", "Error removing overlay view: ${e.message}")
                }
            }

            val eRoot = emergencyOpenRoot
            if (eRoot != null && eRoot.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(eRoot)
                    Log.i("BLOCKIFY_TRIGGER", "SystemInterventionOverlay: removeViewImmediate success, emergency overlay removed")
                } catch (e: Exception) {
                    Log.w("BLOCKIFY_TRIGGER", "Error removing emergency overlay view: ${e.message}")
                }
            }
            emergencyOpenRoot = null
            currentState = OverlayState.IDLE
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissAction()
        } else {
            mainHandler.post { dismissAction() }
        }
    }

    private fun startVibration() {
        try {
            val vibrator = service.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    val effect = android.os.VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 150), intArrayOf(0, 255, 0, 255), -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 150, 100, 150), -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun stopVibration() {
        try {
            val vibrator = service.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vibrator?.cancel()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun reset() {
        dismiss()
    }

    private fun dpToPx(context: Context, dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    /**
     * Clean custom view rendering a circular progress ring and countdown number.
     */
    private class CountdownCircleView(context: Context) : View(context) {
        private var seconds = 3
        private var sweepAngle = 0f

        private val backgroundTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = Color.parseColor("#F1F5F9")
            strokeCap = Paint.Cap.ROUND
        }

        private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            color = Color.parseColor("#2563EB")
            strokeCap = Paint.Cap.ROUND
        }

        private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 90f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }

        private val oval = RectF()

        fun update(remainingSeconds: Int, progressFraction: Float) {
            this.seconds = remainingSeconds
            this.sweepAngle = progressFraction * 360f
            postInvalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val padding = 16f
            oval.set(padding, padding, w - padding, h - padding)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawArc(oval, 0f, 360f, false, backgroundTrackPaint)
            if (sweepAngle > 0) {
                canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint)
            }
            val cx = width / 2f
            val cy = height / 2f
            val numY = cy + 10f
            canvas.drawText(seconds.toString(), cx, numY, numberPaint)
            val labelY = numY + 36f
            canvas.drawText("seconds", cx, labelY, labelPaint)
        }
    }
}
