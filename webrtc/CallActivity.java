package rent.auto.webrtc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.github.nikartm.button.FitButton;
import com.makeramen.roundedimageview.RoundedImageView;

import org.webrtc.MediaStream;

import java.util.Arrays;

import butterknife.BindView;
import butterknife.ButterKnife;
import rent.auto.App;
import rent.auto.R;
import rent.auto.model.Chat;
import rent.auto.model.ChatData;
import rent.auto.socket.SocketListener;
import rent.auto.util.GlideApp;
import rent.auto.util.Helpers;
import rent.auto.util.Preferences;
import rent.auto.util.ServiceUtil;
import rent.auto.util.UiUtils;

public class CallActivity extends AppCompatActivity implements WebRtcClient.RtcListener {

    public static final String EXTRA_CALL_TOKEN = "rent.auto.extra.call_token";


    private static final String EXTRA_CHAT = "rent.auto.exta.chat";
    public static final String ACTION_CALL_DISCONNECT = "rent.auto.action.call_disconnect";
    private final BroadcastReceiver callDisconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String token = intent.getStringExtra(EXTRA_CALL_TOKEN);
            if (!Preferences.getCallToken().equals(token)) {
                return;
            }
//            if (client != null)
//                client.removePeer();
            onStatusChanged(WebRtcClient.Status.DISCONNECTED);
        }
    };
    protected PermissionChecker permissionChecker = new PermissionChecker();
    private WebRtcClient client;
    private static final String EXTRA_FROM_CHAT = "rent.auto.extra.is_from_chat";
    private boolean isFromChat;
    @BindView(R.id.button_in_call)
    ImageButton btnInCall;
    @BindView(R.id.layout_in_call)
    LinearLayout llInCall;
    @BindView(R.id.button_mute)
    FitButton btnMute;
    @BindView(R.id.button_speaker)
    FitButton btnSpeaker;
    @BindView(R.id.button_answer_call)
    ImageButton btnAnswerCall;
    @BindView(R.id.button_end_call)
    ImageButton btnEndCall;
    @BindView(R.id.call_status)
    TextView tvCallStatus;
    @BindView(R.id.user_name)
    TextView tvUsername;
    @BindView(R.id.user_photo)
    RoundedImageView rivUserPhoto;
    @BindView(R.id.rent_title)
    TextView tvRentTitle;
    @BindView(R.id.period)
    TextView tvPeriod;
    @BindView(R.id.layout_answer_call)
    LinearLayout llAnswerCall;
    @BindView(R.id.layout_end_call)
    LinearLayout llEndCall;
    @BindView(R.id.chronometer)
    Chronometer chronometer;
    private CallAction callAction = CallAction.INITIATE_CALL;
    private rent.auto.webrtc.AudioManager audioManager;

    public static Intent getIntent(Context context, Chat chat, CallAction callAction) {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setClass(context, CallActivity.class);
        if (App.get().getLifecycle().isForeground()) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NO_HISTORY);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        }
        intent.putExtra(EXTRA_CHAT, chat);
        intent.setAction(callAction.name());
        intent.putExtra(EXTRA_FROM_CHAT, false);
        return intent;
    }

    public static Intent getIntent(Context context, ChatData chatData, Long bookingId, String pcUrl, CallAction callAction) {
        Chat chat = new Chat();
        chat.setData(chatData);
        chat.setTarget(bookingId);
        chat.setPcUrl(pcUrl);
        Intent intent = new Intent(context, CallActivity.class);
        intent.putExtra(EXTRA_CHAT, chat);
        intent.setAction(callAction.name());
        intent.putExtra(EXTRA_FROM_CHAT, true);
        return intent;
    }

    private final Boolean[] quit = new Boolean[]{false};


    private void onConnected() {
        llEndCall.setVisibility(View.INVISIBLE);
        llAnswerCall.setVisibility(View.INVISIBLE);
        llInCall.setVisibility(View.VISIBLE);
        btnInCall.setOnClickListener(v -> {
            runOnUiThread(() -> {
                for (View view : Arrays.asList(v, btnMute, btnSpeaker)) {
                    view.setEnabled(false);
                }
                for (View view : Arrays.asList(rivUserPhoto, tvPeriod, tvRentTitle, v, btnMute, btnSpeaker)) {
                    view.setAlpha(0.4f);
                }
                chronometer.setVisibility(View.INVISIBLE);
                tvCallStatus.setText(R.string.call_status_end);
                tvCallStatus.setVisibility(View.VISIBLE);

            });
            audioManager.stop(false);
            if (client != null) {
                client.endCall();
            }
        });
        btnMute.setOnClickListener(v -> {
            final boolean mute = v.getAlpha() == 0.8f;
            FitButton fb = (FitButton) v;

            if (client != null && client.handleMuteAudio(mute)) {
                fb.setAlpha(mute ? 1.0f : 0.8f);
                fb.setIconColor(Color.parseColor(mute ? "#00DDFF" : "#FFFFFF"));
            }
        });

        btnSpeaker.setOnClickListener(v -> {
            android.media.AudioManager audioManager = ServiceUtil.getAudioManager(CallActivity.this);
            final boolean speaker = v.getAlpha() == 0.8f;
            audioManager.setSpeakerphoneOn(speaker);
            FitButton fb = (FitButton) v;
            fb.setAlpha(speaker ? 1.0f : 0.8f);
            fb.setIconColor(Color.parseColor(speaker ? "#00DDFF" : "#FFFFFF"));
        });

    }


    @Override
    public void onLocalStream(MediaStream localStream) {
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream) {
    }


    @Override
    public void onStatusChanged(WebRtcClient.Status newStatus) {

        switch (newStatus) {
            case DISCONNECTED:
                App.socket().unsubscribeOnCall();
                if (!isFinishing())
                    runOnUiThread(this::disconnectAndExit);
                break;
            case CONNECTING:
                runOnUiThread(() -> tvCallStatus.setText(R.string.call_status_call));
                break;
            case CONNECTED:
                if (!isFinishing())
                    runOnUiThread(() -> {
                        audioManager.startCommunication(false);
                        tvCallStatus.setVisibility(View.INVISIBLE);
                        chronometer.setVisibility(View.VISIBLE);
                        chronometer.setBase(SystemClock.elapsedRealtime());
                        chronometer.start();
                        onConnected();
                    });
                break;

        }

    }

    @Override
    public void finish() {

        if (isFromChat) {
            setResult(RESULT_OK);
            super.finish();
        } else {
            finishAndRemoveTask();
        }

    }

    public static Intent getDisconnectIntent(String token) {
        Intent intent = new Intent(ACTION_CALL_DISCONNECT);
        intent.putExtra(EXTRA_CALL_TOKEN, token);
        return intent;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SocketListener socketListener = new SocketListener();
        getLifecycle().addObserver(socketListener);
        new Handler().postDelayed(socketListener::enable, 2000);
        Thread.setDefaultUncaughtExceptionHandler(
                new UnhandledExceptionHandler(this));
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_call);
        ButterKnife.bind(this);
        LocalBroadcastManager.getInstance(this).registerReceiver(callDisconnectReceiver, new IntentFilter(ACTION_CALL_DISCONNECT));
        Chat chat = (Chat) getIntent().getSerializableExtra(EXTRA_CHAT);
        callAction = CallAction.valueOf(getIntent().getAction());
        isFromChat = getIntent().getBooleanExtra(EXTRA_FROM_CHAT, false);


        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        audioManager = new rent.auto.webrtc.AudioManager(this);

        if (chat != null) {
            client = new WebRtcClient(this, chat.getTarget(), callAction);
        }
//        AudioManager audioManager =
//                ((AudioManager) getSystemService(AUDIO_SERVICE));

        setVolumeControlStream(android.media.AudioManager.STREAM_VOICE_CALL);

//        @SuppressWarnings("deprecation")
//        boolean isWiredHeadsetOn = audioManager.isWiredHeadsetOn();
//        audioManager.setMode(isWiredHeadsetOn ?
//                AudioManager.MODE_IN_CALL : AudioManager.MODE_IN_COMMUNICATION);
//        audioManager.setSpeakerphoneOn(true);
        checkPermissions();
//        chronometer.setBase(0L);

        if (chat != null) {
            tvRentTitle.setText(getString(R.string.rent_title, chat.getData().getCarTitle()));
            GlideApp.with(this).load(chat.getPcUrl()).into(rivUserPhoto);
            tvPeriod.setText(Helpers.formatPeriod(this, chat.getData().getBookingStartDate(), chat.getData().getBookingEndDate()));
            tvUsername.setText(UiUtils.appendOnlineDot(chat.getData().getOnline(), chat.getData().getInterlocutorName()));
        }

    }

    private void init() {

        switch (callAction) {
            case INITIATE_CALL:
                if (client != null)
                    client.sendOffer();
                audioManager.initializeAudioForCall();
                audioManager.startOutgoingRinger();
                tvCallStatus.setText(R.string.call_status_call);
                tvCallStatus.setVisibility(View.VISIBLE);
                onConnected();
                break;
            case RECEIVE_CALL:
                tvCallStatus.setVisibility(View.INVISIBLE);
                llAnswerCall.setVisibility(View.VISIBLE);
                llEndCall.setVisibility(View.VISIBLE);
                llInCall.setVisibility(View.INVISIBLE);
                audioManager.startIncomingRinger(false);
                btnAnswerCall.setOnClickListener(v -> {
                    runOnUiThread(() -> {
                        v.setEnabled(false);
                        btnEndCall.setEnabled(false);
                    });
                    audioManager.initializeAudioForCall();
                    if (client != null) {
                        client.createOffer();
                    }
                });
                btnEndCall.setOnClickListener(v -> {
                    runOnUiThread(() -> {
                        v.setEnabled(false);
                        btnAnswerCall.setEnabled(false);
                    });
                    audioManager.stop(true);
                    if (client != null) {
                        client.endCall();
                    }
                });
                break;
            case ANSWER_FROM_PUSH:
                tvCallStatus.setVisibility(View.INVISIBLE);
                llAnswerCall.setVisibility(View.VISIBLE);
                llEndCall.setVisibility(View.VISIBLE);
                llInCall.setVisibility(View.INVISIBLE);
                btnAnswerCall.setEnabled(false);
                btnEndCall.setEnabled(false);
                audioManager.initializeAudioForCall();
                if (client != null) {
                    client.createOffer();
                }
                break;

        }
    }

    private void checkPermissions() {
        permissionChecker.verifyPermissions(this, new PermissionChecker.VerifyPermissionsCallback() {

            @Override
            public void onPermissionAllGranted() {
                init();

            }

            @Override
            public void onPermissionDeny(String[] permissions) {
                Toast.makeText(CallActivity.this, "Please grant required permissions.", Toast.LENGTH_LONG).show();
                runOnUiThread(() -> {
                    for (View view : Arrays.asList(btnAnswerCall, btnEndCall, btnInCall, btnMute, btnSpeaker)) {
                        view.setEnabled(false);
                    }
                    finish();
                });
            }
        });
    }

    private void disconnectAndExit() {
        synchronized (quit[0]) {
            if (quit[0]) {
                return;
            }
            quit[0] = true;
            if (audioManager != null) {
                audioManager.stop(false);
                audioManager = null;
            }
            if (client != null) {
                client.onDestroy();
                client = null;
            }

        }
    }

    @Override
    public void onDestroy() {
        disconnectAndExit();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(callDisconnectReceiver);
        super.onDestroy();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionChecker.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onResume() {
        if (client != null)
            client.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (client != null)
            client.onPause();
        super.onPause();
    }
}