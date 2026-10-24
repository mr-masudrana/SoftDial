package com.example.softdial

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

/** "Connected" / "Failed" / ... text for the registration state of an account. */
fun regText(ctx: Context, a: Store.SipAccount): String = ctx.getString(
    when (SipManager.regStateOf(a)) {
        "Ok" -> R.string.reg_connected
        "Failed" -> R.string.reg_failed
        "Progress" -> R.string.reg_connecting
        else -> R.string.reg_none
    }
)

/** Shows the default SIP account (name + Online/Offline, two lines) at the right of the top bar. */
object AccountBar {

    fun displayName(a: Store.SipAccount): String =
        if (a.label.isBlank() || a.label == "${a.user}@${a.server}") a.user else a.label

    fun attach(act: AppCompatActivity) {
        act.lifecycle.addObserver(Bar(act))
    }

    private class Bar(private val act: AppCompatActivity) : LifecycleEventObserver {
        private val col = LinearLayout(act)
        private val name = TextView(act)
        private val status = TextView(act)
        private val cb: (String) -> Unit = { _ -> act.runOnUiThread { update() } }

        init {
            val d = act.resources.displayMetrics.density
            val tv = TypedValue()
            act.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
            val primary = ContextCompat.getColorStateList(act, tv.resourceId)

            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            col.setPadding(0, 0, (8 * d).toInt(), 0)

            name.textSize = 13f
            name.setTypeface(name.typeface, Typeface.BOLD)
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            name.maxWidth = (150 * d).toInt()
            name.gravity = Gravity.END
            primary?.let { name.setTextColor(it) }

            status.textSize = 11f
            status.gravity = Gravity.END

            col.addView(name)
            col.addView(status)
        }

        fun update() {
            val a = Store.selectedAccount()
            if (a == null) {
                col.visibility = android.view.View.GONE
                return
            }
            col.visibility = android.view.View.VISIBLE
            name.text = displayName(a)
            val online = SipManager.isOnline(a)
            status.text = "● " + act.getString(if (online) R.string.status_online else R.string.status_offline)
            status.setTextColor(ContextCompat.getColor(act, if (online) R.color.nav_active else R.color.perm_bad))
        }

        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_CREATE -> act.supportActionBar?.let { bar ->
                    bar.setDisplayShowCustomEnabled(true)
                    bar.setCustomView(
                        col,
                        ActionBar.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.END or Gravity.CENTER_VERTICAL
                        )
                    )
                }
                Lifecycle.Event.ON_START -> {
                    SipManager.addRegListener(cb)
                    update()
                }
                Lifecycle.Event.ON_STOP -> SipManager.removeRegListener(cb)
                else -> {}
            }
        }
    }
}
