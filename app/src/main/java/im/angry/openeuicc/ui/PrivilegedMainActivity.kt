package im.angry.openeuicc.ui

import android.os.Build
import android.telephony.UiccSlotMapping
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.R
import im.angry.openeuicc.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrivilegedMainActivity : MainActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_main_privileged, menu)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            menu.findItem(R.id.slot_mapping).isVisible = false
        }

        menu.findItem(R.id.dsds).apply {
            isVisible = tm.supportsDSDS
            isChecked = tm.dsdsEnabled
        }

        // Samsung "tsds2" devices (e.g. Galaxy S21 family): the built-in eUICC shares the SIM 2
        // interface with SIM tray 2 and is selected with a slot mapping to physical slot 2.
        // The platform does not report that slot, so offer the switch directly.
        menu.findItem(R.id.tsds2_switch).apply {
            isVisible = isTsds2Device()
            setTitle(
                if (isTsds2EsimActive()) R.string.tsds2_switch_to_tray
                else R.string.tsds2_switch_to_esim
            )
        }

        return true
    }

    internal fun showSlotMappingFragment() =
        SlotMappingFragment().show(supportFragmentManager, SlotMappingFragment.TAG)

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.dsds -> {
            tm.setDsdsEnabled(euiccChannelManager, !item.isChecked)
            Toast.makeText(this, R.string.toast_dsds_switched, Toast.LENGTH_LONG).show()
            finish()
            true
        }

        R.id.slot_mapping -> {
            showSlotMappingFragment()
            true
        }

        R.id.tsds2_switch -> {
            switchTsds2(toEsim = !isTsds2EsimActive())
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private fun systemProperty(name: String): String = try {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java, String::class.java)
            .invoke(null, name, "") as String
    } catch (e: Exception) {
        ""
    }

    private fun isTsds2Device(): Boolean =
        systemProperty("persist.radio.esim.slotswitch") == "tsds2"

    private fun isTsds2EsimActive(): Boolean =
        systemProperty("ril.simslottype1") == "1" || systemProperty("ril.simslottype2") == "1"

    private fun switchTsds2(toEsim: Boolean) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    // Same request One UI sends: logical 1 -> physical 2 (eSIM) or 1 (tray 2)
                    tm.simSlotMapping = listOf(
                        UiccSlotMapping(0, 0, 0),
                        UiccSlotMapping(0, if (toEsim) 2 else 1, 1)
                    )
                    true
                } catch (e: Exception) {
                    Log.e("PrivilegedMainActivity", "tsds2 slot switch failed", e)
                    false
                }
            }
            Toast.makeText(
                this@PrivilegedMainActivity,
                if (ok) R.string.tsds2_switch_done else R.string.tsds2_switch_failed,
                Toast.LENGTH_LONG
            ).show()
            if (ok) {
                euiccChannelManager.invalidate()
                finish()
            }
        }
    }
}
