package rent.auto.webrtc.locks;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import rent.auto.util.ServiceUtil;

/**
 * Maintains wake lock state.
 *
 * @author Stuart O. Anderson
 */
public class LockManager {

    private static final String TAG = LockManager.class.getSimpleName();

    private final PowerManager.WakeLock fullLock;
    private final PowerManager.WakeLock partialLock;
    private final WifiManager.WifiLock wifiLock;
    private final ProximityLock proximityLock;

    private final boolean wifiLockEnforced;


    private boolean proximityDisabled = false;

    public LockManager(Context context) {
        PowerManager pm = ServiceUtil.getPowerManager(context);
        fullLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "signal:full");
        partialLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "signal:partial");
        proximityLock = new ProximityLock(pm);

        WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "signal:wifi");

        fullLock.setReferenceCounted(false);
        partialLock.setReferenceCounted(false);
        wifiLock.setReferenceCounted(false);


        wifiLockEnforced = isWifiPowerActiveModeEnabled(context);
    }

    private boolean isWifiPowerActiveModeEnabled(Context context) {
        int wifi_pwr_active_mode = Settings.Secure.getInt(context.getContentResolver(), "wifi_pwr_active_mode", -1);
        Log.d(TAG, "Wifi Activity Policy: " + wifi_pwr_active_mode);

        return wifi_pwr_active_mode != 0;
    }

    private void updateInCallLockState() {
        if (wifiLockEnforced && !proximityDisabled) {
            setLockState(LockState.PROXIMITY);
        } else {
            setLockState(LockState.FULL);
        }
    }

    public void updatePhoneState(PhoneState state) {
        switch (state) {
            case IDLE:
                setLockState(LockState.SLEEP);
                break;
            case PROCESSING:
                setLockState(LockState.PARTIAL);
                break;
            case INTERACTIVE:
                setLockState(LockState.FULL);
                break;
            case IN_VIDEO:
                proximityDisabled = true;
                updateInCallLockState();
                break;
            case IN_CALL:
                proximityDisabled = false;
                updateInCallLockState();
                break;
        }
    }

    private synchronized void setLockState(LockState newState) {
        switch (newState) {
            case FULL:
                fullLock.acquire(10 * 60 * 1000L /*10 minutes*/);
                partialLock.acquire(10 * 60 * 1000L /*10 minutes*/);
                wifiLock.acquire();
                proximityLock.release();
                break;
            case PARTIAL:
                partialLock.acquire(10 * 60 * 1000L /*10 minutes*/);
                wifiLock.acquire();
                fullLock.release();
                proximityLock.release();
                break;
            case SLEEP:
                fullLock.release();
                partialLock.release();
                wifiLock.release();
                proximityLock.release();
                break;
            case PROXIMITY:
                partialLock.acquire(10 * 60 * 1000L /*10 minutes*/);
                proximityLock.acquire();
                wifiLock.acquire();
                fullLock.release();
                break;
            default:
                throw new IllegalArgumentException("Unhandled Mode: " + newState);
        }
        Log.d(TAG, "Entered Lock State: " + newState);
    }

    public enum PhoneState {
        IDLE,
        PROCESSING,  //used when the phone is active but before the user should be alerted.
        INTERACTIVE,
        IN_CALL,
        IN_VIDEO
    }

    private enum LockState {
        FULL,
        PARTIAL,
        SLEEP,
        PROXIMITY
    }
}