package com.example.softdial

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat

/** Bottom navigation bar with custom vector icons, shared by the four main screens. */
object BottomNav {
    const val DIALER = 0
    const val RECENTS = 1
    const val CONTACTS = 2
    const val SETTINGS = 3

    private class Tab(val label: Int, val icon: Int, val filled: Int, val target: Class<*>)

    private val tabs = listOf(
        Tab(R.string.nav_dialer, R.drawable.ic_nav_dialer, R.drawable.ic_nav_dialer_filled, MainActivity::class.java),
        Tab(R.string.nav_recents, R.drawable.ic_nav_recents, R.drawable.ic_nav_recents_filled, HistoryActivity::class.java),
        Tab(R.string.nav_contacts, R.drawable.ic_nav_contacts, R.drawable.ic_nav_contacts_filled, ContactsActivity::class.java),
        Tab(R.string.nav_settings, R.drawable.ic_nav_settings, R.drawable.ic_nav_settings_filled, SettingsActivity::class.java)
    )

    /** Returns a view with [content] on top and the bar at the bottom. */
    fun wrap(act: AppCompatActivity, content: View, selected: Int): View {
        AccountBar.attach(act)
        val d = act.resources.displayMetrics.density
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrapC = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(match, 0, 1f))

        val ripple = TypedValue()
        act.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)

        val holder = LinearLayout(act).apply {
            id = R.id.bottomNav
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(act, R.color.bar_bg))
        }
        holder.addView(View(act).apply { setBackgroundColor(0x33888888) }, LinearLayout.LayoutParams(match, 1))

        val bar = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        tabs.forEachIndexed { i, t ->
            val active = i == selected
            val color = ContextCompat.getColor(act, if (active) R.color.nav_active else R.color.nav_inactive)

            val item = LinearLayout(act).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                contentDescription = act.getString(t.label)
                setBackgroundResource(ripple.resourceId)
                setOnClickListener { if (!active) go(act, i) }
            }

            // icon (filled when selected) + optional badge
            val frame = FrameLayout(act)
            val icon = ImageView(act).apply {
                setImageResource(if (active) t.filled else t.icon)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
            }
            frame.addView(icon, FrameLayout.LayoutParams((26 * d).toInt(), (26 * d).toInt(), Gravity.CENTER))
            if (i == RECENTS) {
                val badge = TextView(act).apply {
                    id = R.id.navBadge
                    textSize = 10f
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    minWidth = (16 * d).toInt()
                    setPadding((3 * d).toInt(), 0, (3 * d).toInt(), 0)
                    background = GradientDrawable().apply {
                        setColor(0xFFD32F2F.toInt())
                        cornerRadius = 100f
                    }
                }
                updateBadge(badge)
                frame.addView(
                    badge,
                    FrameLayout.LayoutParams(wrapC, (16 * d).toInt(), Gravity.TOP or Gravity.END)
                )
            }
            item.addView(frame, LinearLayout.LayoutParams((44 * d).toInt(), (26 * d).toInt()))

            val label = TextView(act).apply {
                text = act.getString(t.label)
                textSize = 11f
                setTextColor(color)
                if (active) setTypeface(typeface, Typeface.BOLD)
            }
            item.addView(label, LinearLayout.LayoutParams(wrapC, wrapC).apply { topMargin = (2 * d).toInt() })
            bar.addView(item, LinearLayout.LayoutParams(0, (60 * d).toInt(), 1f))
        }
        holder.addView(bar, LinearLayout.LayoutParams(match, wrapC))
        root.addView(holder, LinearLayout.LayoutParams(match, wrapC))
        return root
    }

    private fun updateBadge(b: TextView) {
        val n = Store.unseenMissed(Prefs.historySeen)
        b.visibility = if (n > 0) View.VISIBLE else View.GONE
        b.text = if (n > 9) "9+" else n.toString()
    }

    /** Re-reads the missed-call count (call when a screen becomes visible or a call ends). */
    fun refreshBadge(act: Activity) {
        act.findViewById<TextView>(R.id.navBadge)?.let { updateBadge(it) }
    }

    /** Shows the dialer (and the call screen if a call is running). */
    fun openDialer(act: AppCompatActivity) = go(act, DIALER)

    @Suppress("DEPRECATION")
    private fun go(act: AppCompatActivity, index: Int) {
        val i = Intent(act, tabs[index].target).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (index == DIALER) i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        act.startActivity(i)
        if (act !is MainActivity) act.finish()
        act.overridePendingTransition(0, 0)
    }
}
