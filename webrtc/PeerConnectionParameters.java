package rent.auto.webrtc;

public class PeerConnectionParameters {
    public final boolean videoCallEnabled;
    public final boolean loopback;
    public final int audioStartBitrate;
    public final String audioCodec;
    public final boolean cpuOveruseDetection;

    public PeerConnectionParameters(boolean loopback,
                                    int audioStartBitrate, String audioCodec,
                                    boolean cpuOveruseDetection) {
        this.videoCallEnabled = false;
        this.loopback = loopback;
        this.audioStartBitrate = audioStartBitrate;
        this.audioCodec = audioCodec;
        this.cpuOveruseDetection = cpuOveruseDetection;
    }
}