package rent.auto.webrtc;

import android.content.Context;
import android.media.SoundPool;

import androidx.annotation.NonNull;

import rent.auto.R;
import rent.auto.util.ServiceUtil;

public class AudioManager {

    @SuppressWarnings("unused")
    private static final String TAG = AudioManager.class.getSimpleName();

    private final Context context;
    private final IncomingRinger incomingRinger;
    private final OutgoingRinger outgoingRinger;

    private final SoundPool soundPool;
    private final int connectedSoundId;
    private final int disconnectedSoundId;

    AudioManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.incomingRinger = new IncomingRinger(context);
        this.outgoingRinger = new OutgoingRinger(context);
        this.soundPool = new SoundPool(1, android.media.AudioManager.STREAM_VOICE_CALL, 0);

        this.connectedSoundId = this.soundPool.load(context, R.raw.webrtc_completed, 1);
        this.disconnectedSoundId = this.soundPool.load(context, R.raw.webrtc_disconnected, 1);
    }

    void initializeAudioForCall() {
        android.media.AudioManager audioManager = ServiceUtil.getAudioManager(context);
        audioManager.requestAudioFocus(null, android.media.AudioManager.STREAM_VOICE_CALL, android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
    }

    void startIncomingRinger(boolean vibrate) {
        android.media.AudioManager audioManager = ServiceUtil.getAudioManager(context);
        boolean speaker = !audioManager.isWiredHeadsetOn() && !audioManager.isBluetoothScoOn();

        audioManager.setMode(android.media.AudioManager.MODE_RINGTONE);
        audioManager.setMicrophoneMute(false);
        audioManager.setSpeakerphoneOn(speaker);

        incomingRinger.start(android.provider.Settings.System.DEFAULT_RINGTONE_URI, vibrate);
    }

    void startOutgoingRinger() {
        android.media.AudioManager audioManager = ServiceUtil.getAudioManager(context);
        audioManager.setMicrophoneMute(false);

        audioManager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);

        outgoingRinger.start(OutgoingRinger.Type.RINGING);
    }

    public void silenceIncomingRinger() {
        incomingRinger.stop();
    }

    void startCommunication(boolean preserveSpeakerphone) {
        android.media.AudioManager audioManager = ServiceUtil.getAudioManager(context);

        incomingRinger.stop();
        outgoingRinger.stop();

        audioManager.setMode(android.media.AudioManager.MODE_IN_COMMUNICATION);

        if (!preserveSpeakerphone) {
            audioManager.setSpeakerphoneOn(false);
        }

        soundPool.play(connectedSoundId, 1.0f, 1.0f, 0, 0, 1.0f);
    }

    void stop(boolean playDisconnected) {
        android.media.AudioManager audioManager = ServiceUtil.getAudioManager(context);

        incomingRinger.stop();
        outgoingRinger.stop();

        if (playDisconnected) {
            soundPool.play(disconnectedSoundId, 1.0f, 1.0f, 0, 0, 1.0f);
        }

        if (audioManager.isBluetoothScoOn()) {
            audioManager.setBluetoothScoOn(false);
            audioManager.stopBluetoothSco();
        }

        audioManager.setSpeakerphoneOn(false);
        audioManager.setMicrophoneMute(false);
        audioManager.setMode(android.media.AudioManager.MODE_NORMAL);
        audioManager.abandonAudioFocus(null);
    }
}