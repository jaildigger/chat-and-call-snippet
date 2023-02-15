package rent.auto.webrtc;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Objects;

import rent.auto.R;

import static androidx.core.content.ContextCompat.checkSelfPermission;

public class PermissionChecker {

    private static final String[] requiredPermissions = new String[]{Manifest.permission.MODIFY_AUDIO_SETTINGS, Manifest.permission.RECORD_AUDIO
            , Manifest.permission.DISABLE_KEYGUARD};

    private final int REQUEST_MULTIPLE_PERMISSION = 100;
    private VerifyPermissionsCallback callbackMultiple;

    public static boolean hasPermissions(@NonNull Context context, @NonNull String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void verifyPermissions(Activity activity, VerifyPermissionsCallback callback) {
        String[] denyPermissions = getDenyPermissions(activity);


        if (denyPermissions.length > 0) {
            showMicAlert(activity, (dialog, which) -> ActivityCompat.requestPermissions(activity, denyPermissions, REQUEST_MULTIPLE_PERMISSION));
            this.callbackMultiple = callback;
        } else {
            if (callback != null) {
                callback.onPermissionAllGranted();
            }
        }
    }

    private void showMicAlert(Activity activity,
                              DialogInterface.OnClickListener listener) {
        AlertDialog dialog = new AlertDialog.Builder(Objects.requireNonNull(activity)).setMessage(R.string.audio_permission_rationale).
                setPositiveButton(android.R.string.ok, listener).create();
        dialog.show();
    }

    private String[] getDenyPermissions(@NonNull Context context) {
        ArrayList<String> denyPermissions = new ArrayList<>();
        for (String permission : PermissionChecker.requiredPermissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                denyPermissions.add(permission);
            }
        }
        return denyPermissions.toArray(new String[0]);
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_MULTIPLE_PERMISSION) {
            if (grantResults.length > 0 && callbackMultiple != null) {
                ArrayList<String> denyPermissions = new ArrayList<>();
                int i = 0;
                for (String permission : permissions) {
                    if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                        denyPermissions.add(permission);
                    }
                    i++;
                }
                if (denyPermissions.size() == 0) {
                    callbackMultiple.onPermissionAllGranted();
                } else {
                    callbackMultiple.onPermissionDeny(denyPermissions.toArray(new String[0]));
                }
            }
        }
    }


    public boolean checkLocationPermissions(Activity activity) {
        int permissionState = checkSelfPermission(Objects.requireNonNull(activity),
                Manifest.permission.ACCESS_COARSE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;

    }

    public void requestLocationPermissions(Fragment fragment, int code) {
        boolean shouldProvideRationale =
                fragment.shouldShowRequestPermissionRationale(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION);

        if (shouldProvideRationale) {
            showLocAlert(fragment, (dialog, which) -> fragment.requestPermissions(
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    code));
        } else {
            fragment.requestPermissions(
                    new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    code);
        }
    }

    private void showLocAlert(Fragment fragment, DialogInterface.OnClickListener listener) {
        AlertDialog dialog = new AlertDialog.Builder(fragment.requireActivity()).setMessage(R.string.permission_rationale).
                setPositiveButton(android.R.string.ok, listener).create();
        dialog.show();
    }

    public interface VerifyPermissionsCallback {
        void onPermissionAllGranted();

        void onPermissionDeny(String[] permissions);
    }


}
