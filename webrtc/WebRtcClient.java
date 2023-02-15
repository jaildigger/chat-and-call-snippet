package rent.auto.webrtc;

import android.app.Activity;
import android.util.Log;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.LinkedList;

import rent.auto.App;
import rent.auto.socket.Api;
import rent.auto.socket.AppIceCandidate;
import rent.auto.socket.CallStartResponse;
import rent.auto.socket.ResponseAdapter;
import rent.auto.socket.request.CallBody;
import rent.auto.util.Preferences;
import rent.auto.webrtc.locks.LockManager;

class WebRtcClient {
    private final static String TAG = WebRtcClient.class.getCanonicalName();
    public static final String TURN_USERNAME = "23145db3a32ac575892ac30866bbdd_android";
    public static final String TURN_PASSWORD = "f955aa85a40e712fa0bb351e0062be6885";
    private final Long bookingId;
    private PeerConnectionFactory factory;
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream localMS;
    private RtcListener mListener;
    private Activity activity;
    private final boolean isInitiator;
    private Peer peer;
    private final AudioTrack localAudioTrack;

    private LockManager lockManager;
    private LockManager.PhoneState phoneState;

    WebRtcClient(Activity activity, Long bookingId, CallAction callAction) {
        Log.d(UnhandledExceptionHandler.TAG, "client init begun");
        this.isInitiator = callAction == CallAction.INITIATE_CALL;
//        org.webrtc.Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);
        mListener = (RtcListener) activity;
        this.activity = activity;
        this.bookingId = bookingId;
        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions
                .builder(activity).
                        createInitializationOptions();
        PeerConnectionFactory.initialize(options);
        PeerConnectionFactory.Options options1 = new PeerConnectionFactory.Options();
        options1.networkIgnoreMask = 0;
        factory = PeerConnectionFactory.builder().
                setOptions(options1).createPeerConnectionFactory();
        Log.d(UnhandledExceptionHandler.TAG, "factory created");


        lockManager = new LockManager(activity);


        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("RtpDataChannels", "true"));

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource);
        localAudioTrack.setEnabled(true);


        localMS = factory.createLocalMediaStream("ARDAMS");
        localMS.addTrack(localAudioTrack);


//        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
//        iceServers.add(PeerConnection.IceServer.builder("turn:new.auto.rent:5349").setUsername("test").setPassword("test").createIceServer());
        iceServers.add(PeerConnection.IceServer.builder("turn:turn.auto.rent:5349")
                .setUsername(TURN_USERNAME)
                .setPassword(TURN_PASSWORD).createIceServer());

        iceServers.add(PeerConnection.IceServer.builder("turn:turn2.auto.rent:5349")
                .setUsername(TURN_USERNAME)
                .setPassword(TURN_PASSWORD).createIceServer());

        iceServers.add(PeerConnection.IceServer.builder("turn:new.auto.rent:5349")
                .setUsername(TURN_USERNAME)
                .setPassword(TURN_PASSWORD).createIceServer());
        mListener.onLocalStream(localMS);
        Log.d(UnhandledExceptionHandler.TAG, "client init finished");


    }

//    private PeerConnectionParameters params = new PeerConnectionParameters(
//            false, 1, AUDIO_CODEC_OPUS, true);

    void sendOffer() {
        Log.d(UnhandledExceptionHandler.TAG, "send offer");
        App.socket().callStart(bookingId, new ResponseAdapter(activity) {
            @Override
            public void onSuccess(Api apiName, String json) {
                Log.d(UnhandledExceptionHandler.TAG, "send offer response");
                CallStartResponse response = CallStartResponse.get(json);
                Preferences.setCallToken(response.getToken());
                phoneState = LockManager.PhoneState.IN_CALL;
                lockManager.updatePhoneState(phoneState);
                subscribeOnCall();

            }
        });
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    void onDestroy() {

        Log.d(UnhandledExceptionHandler.TAG, "client destroy begun");
        phoneState = LockManager.PhoneState.IDLE;
        lockManager.updatePhoneState(phoneState);
        mListener = null;
        if (peer != null && peer.pc != null) {
            Log.d(UnhandledExceptionHandler.TAG, "ondestroy, peer.pc not null");
            peer.pc.dispose();
            peer.pc = null;
            peer = null;
        }
        if (factory != null) {
            Log.d(UnhandledExceptionHandler.TAG, "ondestroy, factory not null");
            factory.dispose();
            factory = null;
        }
        Preferences.clearCallToken();
        activity.finish();
        activity = null;
        Log.d(UnhandledExceptionHandler.TAG, "client destroy finished");

    }

    private void addPeer() {
        peer = new Peer();
        Log.d(UnhandledExceptionHandler.TAG, "new peer created");
    }


    /**
     * Call this method in Activity.onPause()
     */
    void onPause() {
//        lockManager.updatePhoneState(LockManager.PhoneState.IDLE);
    }

    /**
     * Call this method in Activity.onResume()
     */
    void onResume() {
//        lockManager.updatePhoneState(phoneState);
    }

    void endCall() {
        Log.d(UnhandledExceptionHandler.TAG, "end call begun");
        App.socket().callDisconnect(bookingId, new ResponseAdapter(null) {
            @Override
            public void onSuccess(Api apiName, String json) {
                Log.d(UnhandledExceptionHandler.TAG, "end call response");
//                removePeer();

            }
        });
        mListener.onStatusChanged(Status.DISCONNECTED);
    }

    enum Status {

        CONNECTING,
        CONNECTED,
        DISCONNECTED

    }

    void createOffer() {
        Log.d(UnhandledExceptionHandler.TAG, "create offer begun");
        boolean first = true;
        if (peer == null) {
            addPeer();
        } else {
            first = false;
        }
        peer.pc.createOffer(peer, new MediaConstraints());
        if (first)
            subscribeOnCall();
        Log.d(UnhandledExceptionHandler.TAG, "create offer finished");
    }

    /**
     * Start the client.
     * <p>
     * Set up the local stream and notify the signaling server.
     * Call this method after onCallReady.
     */
    private void subscribeOnCall() {
        Log.d(UnhandledExceptionHandler.TAG, "subscribe on call begun");
        App.socket().subscribeOnCallConnect(bookingId, new ResponseAdapter(null) {
            @Override
            public void onSuccess(Api apiName, String json) {
                if (json == null)
                    return;
                Log.d(UnhandledExceptionHandler.TAG, "subscribe on call response");

                CallBody body = CallBody.get(json);

                if (!body.getToken().equals(Preferences.getCallToken())) {
                    return;
                }


                if (peer == null) {
                    Log.d(UnhandledExceptionHandler.TAG, "subscribe on call, peer is null");
                    addPeer();
                }

                if (body.getCandidate() != null && getRemoteSdp() != null &&
                        body.getCandidate().getCandidate() != null && body.getCandidate().getSdpMid() != null && body.getCandidate().getSdpMLineIndex() != null

                ) {
                    Log.d(UnhandledExceptionHandler.TAG, "subscribe on call, add candidate");
                    addCandidate(body.getCandidate().toCandidate());

                }

                if (body.getSdp() != null &&
                        (isInitiator && body.getSdp().type == SessionDescription.Type.OFFER ||
                                !isInitiator && body.getSdp().type == SessionDescription.Type.ANSWER)
                ) {
                    Log.d(UnhandledExceptionHandler.TAG, "subscribe on call, add remote sdp");
                    addRemoteSdp(body.getSdp());
                }
            }
        });


    }


    /**
     * Implement this interface to be notified of events.
     */
    public interface RtcListener {
        void onStatusChanged(Status newStatus);

        void onLocalStream(MediaStream localStream);

        void onAddRemoteStream(MediaStream remoteStream);

    }

    private void addRemoteSdp(SessionDescription sessionDescription) {
        Log.d(UnhandledExceptionHandler.TAG, "add remote sdp begun");
        runIfAlive(() -> {
            peer.pc.setRemoteDescription(peer, sessionDescription);
            Log.d(UnhandledExceptionHandler.TAG, "add remote sdp finished");
            if (isInitiator) {
                peer.pc.createAnswer(peer, pcConstraints);
                Log.d(UnhandledExceptionHandler.TAG, "add remote sdp, create answer invoked");
            }
        });

    }


    private SessionDescription getRemoteSdp() {
        if (peer == null || peer.pc == null) {
            Log.d(UnhandledExceptionHandler.TAG,
                    "get remote sdp, peer of peer.pc is null ");
            return null;
        }

        Log.d(UnhandledExceptionHandler.TAG,
                "get remote sdp, sdp is " + (peer.pc.getRemoteDescription() == null ? "null" : "not null"));
//        while (!remoteAvailable) {
//            try {
//                Thread.sleep(500);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
        return peer.pc.getRemoteDescription();
    }

    private void addCandidate(IceCandidate iceCandidate) {
//        if (successIsSet)
//            return;
//        if (queuedRemoteCandidates != null) {
//            queuedRemoteCandidates.add(iceCandidate);
//        } else {
        Log.d(UnhandledExceptionHandler.TAG, "add candidate begun");
        peer.pc.addIceCandidate(iceCandidate);
        Log.d(UnhandledExceptionHandler.TAG, "add candidate finished");
//        }
    }

    private void runIfAlive(Runnable runnable) {
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(runnable);
        }
    }


    public boolean handleMuteAudio(boolean mute) {
        if (peer != null && peer.pc != null) {
            return localAudioTrack.setEnabled(!mute);
        }
        return false;
    }

    private class Peer implements SdpObserver, PeerConnection.Observer {
        private PeerConnection pc;

        Peer() {
            Log.d(UnhandledExceptionHandler.TAG, "peer constructor start");
            String token = Preferences.getCallToken();
            Log.d(TAG, "new Peer: " + token);
            if (factory != null) {
                Log.d(UnhandledExceptionHandler.TAG, "peer constructor, factory!=null");
                this.pc = factory.createPeerConnection(iceServers, this);
                if (this.pc != null) {
                    this.pc.addStream(localMS);
                }
                mListener.onStatusChanged(Status.CONNECTING);
                phoneState = LockManager.PhoneState.PROCESSING;
                lockManager.updatePhoneState(phoneState);
            }
            Log.d(UnhandledExceptionHandler.TAG, "peer constructor finish");
        }

        @Override
        public void onCreateSuccess(final SessionDescription sdp) {
            Log.d(UnhandledExceptionHandler.TAG, "onCreateSuccess start");

            runIfAlive(() -> {
                CallBody request = new CallBody();
                request.setToken(Preferences.getCallToken());
                request.setBookingId(bookingId);
                request.setSdp(sdp);
                pc.setLocalDescription(this, sdp);
                App.socket().callConnect(request);
                Log.d(UnhandledExceptionHandler.TAG, "onCreateSuccess finish");
            });


        }


        @Override
        public void onSetSuccess() {
            Log.d(UnhandledExceptionHandler.TAG, "onSetSuccess start");
            runIfAlive(this::drainRemoteCandidates);
            Log.d(UnhandledExceptionHandler.TAG, "onSetSuccess finish");
        }

        @Override
        public void onCreateFailure(String s) {
            Log.d(UnhandledExceptionHandler.TAG, "onCreateFailure start");
            throw new RuntimeException("createSDP error: " + s);
        }

        @Override
        public void onSetFailure(String s) {
            throw new RuntimeException("setSDP error: " + s);

        }

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(UnhandledExceptionHandler.TAG, "onSignalingChange");
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d(UnhandledExceptionHandler.TAG, "onIceConnectionChange");
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.d(UnhandledExceptionHandler.TAG, "onIceConnectionReceivingChange");
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d(UnhandledExceptionHandler.TAG, "onIceGatheringChange");
        }

        @Override
        public void onIceCandidate(final IceCandidate candidate) {
            Log.d(UnhandledExceptionHandler.TAG, "onIceCandidate start");
            runIfAlive(() -> {
                CallBody request = new CallBody();
                request.setCandidate(new AppIceCandidate(candidate));
                request.setBookingId(bookingId);
                request.setToken(Preferences.getCallToken());
                App.socket().callConnect(request);
                Log.d(UnhandledExceptionHandler.TAG, "onIceCandidate finish");
            });


        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.d(UnhandledExceptionHandler.TAG, "onIceCandidatesRemoved");

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.d(UnhandledExceptionHandler.TAG, "onAddStream " + mediaStream.getId());
            // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
            mListener.onAddRemoteStream(mediaStream);
            Log.d(UnhandledExceptionHandler.TAG, "onAddStream finish");

        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream " + mediaStream.getId());
            runIfAlive(() -> mediaStream.audioTracks.get(0).dispose());
            Log.d(UnhandledExceptionHandler.TAG, "onRemoveStream finish");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.d(UnhandledExceptionHandler.TAG, "onDataChannel");
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.d(UnhandledExceptionHandler.TAG, "onRenegotiationNeeded");
//            peer.pc.createAnswer(peer, pcConstraints);
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            Log.d(UnhandledExceptionHandler.TAG, "onAddTrack");
        }

        private void drainRemoteCandidates() {
            Log.d(UnhandledExceptionHandler.TAG, "drainRemoteCandidates start");
//            if (queuedRemoteCandidates != null) {
//                for (IceCandidate candidate : queuedRemoteCandidates) {
//                    pc.addIceCandidate(candidate);
//                }
//                queuedRemoteCandidates = null;
            mListener.onStatusChanged(Status.CONNECTED);
            phoneState = LockManager.PhoneState.INTERACTIVE;
            lockManager.updatePhoneState(phoneState);
            Log.d(UnhandledExceptionHandler.TAG, "drainRemoteCandidates finish");
//
//                audioManager.startCommunication(callState == CallState.STATE_REMOTE_RINGING);
//
//            }


        }
    }


}
