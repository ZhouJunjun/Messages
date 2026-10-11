package org.fossify.messages.activities

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatEditText
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyTextView
import org.fossify.messages.App
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityCustomAppearanceBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.THEME_MODE_DARK
import org.fossify.messages.helpers.THEME_MODE_LIGHT
import org.fossify.messages.helpers.THEME_MODE_SYSTEM

/**
 * "Custom appearance" screen. Replaces the Commons customization entry and lets the user tweak
 * the text size + color of the message-related labels. There is intentionally no font picker.
 *
 * As of 1.0.9 it opens with a theme picker (follow system / light / dark, default light) and every
 * colour exists twice, once per theme, while the text sizes stay shared. The colours are edited by
 * typing a hex code rather than by picking from a fixed palette, so any colour is reachable.
 */
class CustomAppearanceActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityCustomAppearanceBinding::inflate)

    private val textSizeOptions = arrayListOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24)

    /** A single int-backed preference: the pair of accessors a row needs to read and write it. */
    private class IntSetting(val get: () -> Int, val set: (Int) -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.customAppearanceScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.customAppearanceScrollview,
            topAppBar = binding.customAppearanceAppbar
        )
        setupTopAppBar(binding.customAppearanceAppbar, NavigationIcon.Arrow)

        val appearance = config

        addThemeRow()

        addRow(
            R.string.appearance_home_title,
            IntSetting({ appearance.homeTitleTextSize }, { appearance.homeTitleTextSize = it }),
            IntSetting({ appearance.homeTitleTextColorLight }, { appearance.homeTitleTextColorLight = it }),
            IntSetting({ appearance.homeTitleTextColorDark }, { appearance.homeTitleTextColorDark = it })
        )
        addRow(
            R.string.appearance_home_snippet,
            IntSetting({ appearance.homeSnippetTextSize }, { appearance.homeSnippetTextSize = it }),
            IntSetting({ appearance.homeSnippetTextColorLight }, { appearance.homeSnippetTextColorLight = it }),
            IntSetting({ appearance.homeSnippetTextColorDark }, { appearance.homeSnippetTextColorDark = it })
        )
        addRow(
            R.string.appearance_home_info,
            IntSetting({ appearance.homeInfoTextSize }, { appearance.homeInfoTextSize = it }),
            IntSetting({ appearance.homeInfoTextColorLight }, { appearance.homeInfoTextColorLight = it }),
            IntSetting({ appearance.homeInfoTextColorDark }, { appearance.homeInfoTextColorDark = it })
        )
        addRow(
            R.string.appearance_copyable_strings,
            IntSetting({ appearance.copyableTextSize }, { appearance.copyableTextSize = it }),
            IntSetting({ appearance.copyableTextColorLight }, { appearance.copyableTextColorLight = it }),
            IntSetting({ appearance.copyableTextColorDark }, { appearance.copyableTextColorDark = it })
        )
        addRow(
            R.string.appearance_thread_snippet,
            IntSetting({ appearance.threadSnippetTextSize }, { appearance.threadSnippetTextSize = it }),
            IntSetting({ appearance.threadSnippetTextColorLight }, { appearance.threadSnippetTextColorLight = it }),
            IntSetting({ appearance.threadSnippetTextColorDark }, { appearance.threadSnippetTextColorDark = it })
        )
        addRow(
            R.string.appearance_thread_info,
            IntSetting({ appearance.threadInfoTextSize }, { appearance.threadInfoTextSize = it }),
            IntSetting({ appearance.threadInfoTextColorLight }, { appearance.threadInfoTextColorLight = it }),
            IntSetting({ appearance.threadInfoTextColorDark }, { appearance.threadInfoTextColorDark = it })
        )
        // the link colour has no dedicated size, only a colour
        addRow(
            R.string.appearance_link_color,
            null,
            IntSetting({ appearance.linkTextColorLight }, { appearance.linkTextColorLight = it }),
            IntSetting({ appearance.linkTextColorDark }, { appearance.linkTextColorDark = it })
        )
        // the highlight colour has no dedicated size either; it drives the selected top tab, the
        // search-result hit highlighting and the "delivered" check mark (1.0.7 spec)
        addRow(
            R.string.appearance_highlight_color,
            null,
            IntSetting({ appearance.highlightColorLight }, { appearance.highlightColorLight = it }),
            IntSetting({ appearance.highlightColorDark }, { appearance.highlightColorDark = it })
        )

        updateTextColors(binding.customAppearanceScrollview)
    }

    /**
     * Theme picker, the first entry of the screen (1.0.9 spec). Changing it re-applies the
     * app-wide night mode, which makes AppCompat recreate this activity, so the whole screen -
     * and the rest of the app behind it - comes back in the new theme immediately.
     */
    private fun addThemeRow() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val label = MyTextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setText(R.string.appearance_theme)
        }

        val value = MyTextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            text = getString(themeModeLabelRes(config.themeMode))
            setTextColor(getProperTextColor())
        }

        val onClick = {
            val items = arrayListOf(
                RadioItem(THEME_MODE_SYSTEM, getString(R.string.appearance_theme_system)),
                RadioItem(THEME_MODE_LIGHT, getString(R.string.appearance_theme_light)),
                RadioItem(THEME_MODE_DARK, getString(R.string.appearance_theme_dark)),
            )
            RadioGroupDialog(this, items, config.themeMode) {
                val mode = it as Int
                config.themeMode = mode
                value.text = getString(themeModeLabelRes(mode))
                // the running activities are recreated by this, this screen included
                (application as App).applyThemeMode()
            }
        }

        label.setOnClickListener { onClick() }
        value.setOnClickListener { onClick() }

        row.addView(label)
        row.addView(value)

        binding.customAppearanceHolder.addView(row)
        binding.customAppearanceHolder.addView(makeDivider())
    }

    private fun themeModeLabelRes(mode: Int) = when (mode) {
        THEME_MODE_LIGHT -> R.string.appearance_theme_light
        THEME_MODE_DARK -> R.string.appearance_theme_dark
        else -> R.string.appearance_theme_system
    }

    /**
     * One settings row: `label … size  亮● 暗●`.
     *
     * [size] is null for the two colour-only entries. Tapping the label or the size opens the size
     * picker; tapping either dot opens the hex colour input for that half of the pair.
     */
    private fun addRow(
        labelRes: Int,
        size: IntSetting?,
        light: IntSetting,
        dark: IntSetting,
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val label = MyTextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setText(labelRes)
        }

        val sizeView = size?.let {
            MyTextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                minWidth = dp(44)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
        }

        val lightDot = makeColorDot()
        val darkDot = makeColorDot()

        fun refresh() {
            sizeView?.text = size?.get()?.toString() ?: ""
            sizeView?.setTextColor(getProperTextColor())
            lightDot.backgroundTintList = ColorStateList.valueOf(light.get())
            darkDot.backgroundTintList = ColorStateList.valueOf(dark.get())
        }

        label.setOnClickListener {
            if (size != null) {
                showSizeDialog(size.get(), size.set, ::refresh)
            } else {
                showHexColorDialog(light.get(), light.set, ::refresh)
            }
        }
        sizeView?.setOnClickListener {
            val current = size
            if (current != null) {
                showSizeDialog(current.get(), current.set, ::refresh)
            }
        }
        lightDot.setOnClickListener { showHexColorDialog(light.get(), light.set, ::refresh) }
        darkDot.setOnClickListener { showHexColorDialog(dark.get(), dark.set, ::refresh) }
        refresh()

        row.addView(label)
        sizeView?.let { row.addView(it) }
        row.addView(makeDotLabel(R.string.appearance_color_light_short))
        row.addView(lightDot)
        row.addView(makeDotLabel(R.string.appearance_color_dark_short))
        row.addView(darkDot)

        binding.customAppearanceHolder.addView(row)
        binding.customAppearanceHolder.addView(makeDivider())
    }

    private fun makeColorDot(): View {
        val dotSize = dp(24)
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { marginStart = dp(4) }
            background = AppCompatResources.getDrawable(
                this@CustomAppearanceActivity,
                org.fossify.commons.R.drawable.circle_background
            )
        }
    }

    /** Tiny "亮" / "暗" tag in front of each dot, so the pair is unambiguous. */
    private fun makeDotLabel(textRes: Int): MyTextView = MyTextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(14) }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setText(textRes)
        setTextColor(getProperTextColor())
    }

    private fun makeDivider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        setBackgroundColor(0x22808080)
    }

    private fun showSizeDialog(current: Int, setter: (Int) -> Unit, onDone: () -> Unit) {
        val items = ArrayList<RadioItem>()
        textSizeOptions.forEach { items.add(RadioItem(it, it.toString())) }
        RadioGroupDialog(this, items, current) {
            setter(it as Int)
            onDone()
        }
    }

    /**
     * Hex colour input (1.0.9 spec), replacing the fixed palette the earlier versions offered.
     * `#RRGGBB` and `#AARRGGBB` are both accepted, with or without the leading `#`; anything else
     * is rejected and the dialog stays open so the user can fix it.
     */
    private fun showHexColorDialog(current: Int, setter: (Int) -> Unit, onDone: () -> Unit) {
        val initial = String.format("#%08X", current)
        val input = AppCompatEditText(this).apply {
            setText(initial)
            setSelection(initial.length)
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setTextColor(getProperTextColor())
            setHint(R.string.appearance_hex_hint)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }

        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(4), dp(24), dp(0))
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.appearance_hex_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            // handled here instead of through the builder's own listener so invalid input can keep
            // the dialog open
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = parseHexColor(input.text?.toString().orEmpty())
                if (parsed == null) {
                    toast(R.string.appearance_hex_invalid)
                } else {
                    setter(parsed)
                    onDone()
                    dialog.dismiss()
                }
            }
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
    }

    /** `#4CAF50` / `#FF4CAF50` / `4caf50` -> ARGB int, or null when it is not a colour code. */
    private fun parseHexColor(value: String): Int? {
        var text = value.trim().removePrefix("#")
        if (text.length == 6) {
            text = "FF$text"
        }
        if (text.length != 8 || !text.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return null
        }
        return text.toLong(16).toInt()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
