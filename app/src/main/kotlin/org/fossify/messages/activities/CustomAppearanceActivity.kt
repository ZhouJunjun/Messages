package org.fossify.messages.activities

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyTextView
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityCustomAppearanceBinding
import org.fossify.messages.extensions.config

/**
 * "Custom appearance" screen. Replaces the Commons customization entry and lets the user tweak
 * the text size + color of the message-related labels. There is intentionally no font picker.
 */
class CustomAppearanceActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityCustomAppearanceBinding::inflate)

    private val textSizeOptions = arrayListOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24)

    private val colorOptions by lazy {
        arrayListOf(
            0xff000000.toInt() to R.string.color_black,
            0xff333333.toInt() to R.string.color_dark_gray,
            0xff888888.toInt() to R.string.color_gray,
            0xff999999.toInt() to R.string.color_light_gray,
            0xff4caf50.toInt() to R.string.color_green,
            0xff4d99e0.toInt() to R.string.color_link_blue,
            0xff1e88e5.toInt() to R.string.color_blue,
            0xfff44336.toInt() to R.string.color_red,
            0xffff9800.toInt() to R.string.color_orange,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.customAppearanceScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.customAppearanceScrollview,
            topAppBar = binding.customAppearanceAppbar
        )
        setupTopAppBar(binding.customAppearanceAppbar, NavigationIcon.Arrow)

        addSettingRow(
            R.string.appearance_home_title,
            { config.homeTitleTextSize }, { config.homeTitleTextSize = it },
            { config.homeTitleTextColor }, { config.homeTitleTextColor = it }
        )
        addSettingRow(
            R.string.appearance_home_snippet,
            { config.homeSnippetTextSize }, { config.homeSnippetTextSize = it },
            { config.homeSnippetTextColor }, { config.homeSnippetTextColor = it }
        )
        addSettingRow(
            R.string.appearance_home_info,
            { config.homeInfoTextSize }, { config.homeInfoTextSize = it },
            { config.homeInfoTextColor }, { config.homeInfoTextColor = it }
        )
        addSettingRow(
            R.string.appearance_copyable_strings,
            { config.copyableTextSize }, { config.copyableTextSize = it },
            { config.copyableTextColor }, { config.copyableTextColor = it }
        )
        addSettingRow(
            R.string.appearance_thread_snippet,
            { config.threadSnippetTextSize }, { config.threadSnippetTextSize = it },
            { config.threadSnippetTextColor }, { config.threadSnippetTextColor = it }
        )
        addSettingRow(
            R.string.appearance_thread_info,
            { config.threadInfoTextSize }, { config.threadInfoTextSize = it },
            { config.threadInfoTextColor }, { config.threadInfoTextColor = it }
        )
        // the link color has no dedicated size, only a color
        addSettingRow(
            R.string.appearance_link_color,
            null, null,
            { config.linkTextColor }, { config.linkTextColor = it }
        )

        updateTextColors(binding.customAppearanceScrollview)
    }

    private fun addSettingRow(
        labelRes: Int,
        sizeGetter: (() -> Int)?,
        sizeSetter: ((Int) -> Unit)?,
        colorGetter: () -> Int,
        colorSetter: (Int) -> Unit,
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
            text = getString(labelRes)
        }

        val sizeView = if (sizeGetter != null && sizeSetter != null) {
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
        } else {
            null
        }

        val colorDot = View(this).apply {
            val dotSize = dp(24)
            layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply { marginStart = dp(12) }
            background = AppCompatResources.getDrawable(this@CustomAppearanceActivity, org.fossify.commons.R.drawable.circle_background)
        }

        fun refresh() {
            sizeView?.text = sizeGetter?.invoke()?.toString() ?: ""
            sizeView?.setTextColor(getProperTextColor())
            colorDot.backgroundTintList = ColorStateList.valueOf(colorGetter())
        }

        val onSizeClick = {
            if (sizeGetter != null && sizeSetter != null) {
                showSizeDialog(sizeGetter(), sizeSetter, ::refresh)
            } else {
                showColorDialog(colorGetter(), colorSetter, ::refresh)
            }
        }

        label.setOnClickListener { onSizeClick() }
        sizeView?.setOnClickListener { onSizeClick() }
        colorDot.setOnClickListener { showColorDialog(colorGetter(), colorSetter, ::refresh) }
        refresh()

        // the row used to be added to the holder without its children, which left the whole
        // screen blank - add the children explicitly, in order
        row.addView(label)
        sizeView?.let { row.addView(it) }
        row.addView(colorDot)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(0x22808080)
        }

        binding.customAppearanceHolder.addView(row)
        binding.customAppearanceHolder.addView(divider)
    }

    private fun showSizeDialog(current: Int, setter: (Int) -> Unit, onDone: () -> Unit) {
        val items = ArrayList<RadioItem>()
        textSizeOptions.forEach { items.add(RadioItem(it, it.toString())) }
        RadioGroupDialog(this, items, current) {
            setter(it as Int)
            onDone()
        }
    }

    private fun showColorDialog(current: Int, setter: (Int) -> Unit, onDone: () -> Unit) {
        val items = ArrayList<RadioItem>()
        colorOptions.forEach { (color, nameRes) -> items.add(RadioItem(color, getString(nameRes))) }
        RadioGroupDialog(this, items, current) {
            setter(it as Int)
            onDone()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
