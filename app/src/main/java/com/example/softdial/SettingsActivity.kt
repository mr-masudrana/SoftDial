package com.example.softdial

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat

/** Settings home: a grid of tiles. Each tile opens its own section (SettingsDetailActivity). */
class SettingsActivity : AppCompatActivity() {

    private class Tile(val key: String, val title: Int, val icon: Int)

    private val tiles = listOf(
        Tile("account", R.string.set_account_title, R.drawable.ic_set_account),
        Tile("ringtone", R.string.set_ring_title, R.drawable.ic_set_ringtone),
        Tile("theme", R.string.set_theme_title, R.drawable.ic_set_theme),
        Tile("language", R.string.set_lang_title, R.drawable.ic_set_language),
        Tile("recording", R.string.set_rec_title, R.drawable.ic_set_record),
        Tile("forwarding", R.string.set_fwd_title, R.drawable.ic_set_forward),
        Tile("block", R.string.set_block_title, R.drawable.ic_set_block),
        Tile("permissions", R.string.set_perm_title, R.drawable.ic_set_permission),
        Tile("backup", R.string.set_backup_title, R.drawable.ic_set_backup),
        Tile("logs", R.string.set_logs_title, R.drawable.ic_set_logs),
        Tile("help", R.string.set_help_title, R.drawable.ic_set_help),
        Tile("about", R.string.set_about_title, R.drawable.ic_set_about)
    )

    private val subtitles = mutableMapOf<String, TextView>()
    private val regCb: (String) -> Unit = { _ -> runOnUiThread { refreshSubtitles() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.nav_settings)
        Prefs.init(this)

        val d = resources.displayMetrics.density
        val m = (6 * d).toInt()
        val grid = GridLayout(this).apply { columnCount = 2 }
        tiles.forEachIndexed { i, t ->
            val lastOdd = i == tiles.size - 1 && tiles.size % 2 == 1   // last tile spans the full width
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, if (lastOdd) 2 else 1, 1f)
            ).apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(m, m, m, m)
                setGravity(Gravity.FILL)
            }
            grid.addView(makeTile(t), lp)
        }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            setPadding(m, m, m, m)
            addView(grid)
        }
        setContentView(BottomNav.wrap(this, scroll, BottomNav.SETTINGS))
    }

    override fun onStart() {
        super.onStart()
        BottomNav.refreshBadge(this)
        SipManager.addRegListener(regCb)
    }

    override fun onStop() {
        SipManager.removeRegListener(regCb)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshSubtitles()
    }

    private fun attrColors(attr: Int): ColorStateList? {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return ContextCompat.getColorStateList(this, tv.resourceId)
    }

    private fun makeTile(t: Tile): View {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_ripple)
            setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt())
            minimumHeight = (128 * d).toInt()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(
                    Intent(this@SettingsActivity, SettingsDetailActivity::class.java)
                        .putExtra(SettingsDetailActivity.EXTRA_SECTION, t.key)
                )
            }
        }
        val iconBox = FrameLayout(this).apply { setBackgroundResource(R.drawable.bg_avatar) }
        val icon = ImageView(this).apply {
            setImageResource(t.icon)
            ImageViewCompat.setImageTintList(
                this, ColorStateList.valueOf(ContextCompat.getColor(this@SettingsActivity, R.color.nav_active))
            )
        }
        iconBox.addView(icon, FrameLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt(), Gravity.CENTER))
        box.addView(iconBox, LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()))

        val title = TextView(this).apply {
            text = getString(t.title)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            attrColors(android.R.attr.textColorPrimary)?.let { setTextColor(it) }
        }
        box.addView(
            title,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (12 * d).toInt() }
        )
        val sub = TextView(this).apply {
            textSize = 12f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            attrColors(android.R.attr.textColorSecondary)?.let { setTextColor(it) }
        }
        box.addView(
            sub,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (2 * d).toInt() }
        )
        subtitles[t.key] = sub
        return box
    }

    // ---- live status line on each tile ----

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun refreshSubtitles() {
        // account: default account + online/offline
        val acc = Store.selectedAccount()
        subtitles["account"]?.text = if (acc == null) getString(R.string.set_account_none)
        else AccountBar.displayName(acc) + " · " +
            getString(if (SipManager.isOnline(acc)) R.string.status_online else R.string.status_offline)

        subtitles["ringtone"]?.text = Ringer.currentTitle(this)
        subtitles["logs"]?.text = getString(R.string.tile_logs, AppLog.count())
        subtitles["help"]?.text = getString(R.string.tile_help_sub)
        subtitles["theme"]?.text = getString(
            when (Prefs.theme) { "light" -> R.string.set_theme_light; "dark" -> R.string.set_theme_dark; else -> R.string.set_theme_system }
        )
        subtitles["language"]?.text = getString(
            when (Prefs.language) { "en" -> R.string.set_lang_en; "bn" -> R.string.set_lang_bn; else -> R.string.set_lang_system }
        )
        subtitles["recording"]?.text = getString(if (Prefs.autoRecord) R.string.tile_auto_on else R.string.tile_auto_off)

        subtitles["forwarding"]?.text = when (Prefs.fwdMode) {
            "always" -> getString(R.string.set_fwd_always) + " → " + Prefs.fwdNumber
            "busy" -> getString(R.string.fwd_short_busy) + " → " + Prefs.fwdNumber
            "noanswer" -> getString(R.string.fwd_short_noanswer) + " → " + Prefs.fwdNumber
            else -> getString(R.string.set_fwd_off)
        }
        subtitles["block"]?.text = getString(R.string.tile_blocked, Store.blocked().size) +
            if (Prefs.dnd) " · DND" else ""

        val checks = listOf(
            granted(Manifest.permission.RECORD_AUDIO),
            Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
            granted(Manifest.permission.READ_CONTACTS),
            Build.VERSION.SDK_INT < 31 || granted(Manifest.permission.BLUETOOTH_CONNECT),
            getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        )
        subtitles["permissions"]?.text = getString(R.string.tile_perm_count, checks.count { it }, checks.size)

        subtitles["backup"]?.text = getString(R.string.set_export) + " · " + getString(R.string.set_import)

        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        subtitles["about"]?.text = getString(R.string.set_about_version, ver)
    }
}
