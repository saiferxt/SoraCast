package app.gptcast;

import android.content.*;
import android.media.projection.MediaProjection;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import app.nebo.core.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.webrtc.*;
import org.webrtc.audio.*;

/** Native texture capture, DTLS-SRTP over LAN; no screen bitmaps, recording, STUN or cloud relay. */
public final class MediaEngine implements Link.Listener {
    public interface Events {void status(String text);void ended(String reason);default void diagnostics(String text){}}
    // Interactive mirroring: short playout hint and pacing queue; not an end-to-end latency guarantee.
    private static final String LOW_DELAY="WebRTC-ForceSendPlayoutDelay/min_ms:0,max_ms:80/WebRTC-Video-Pacing/max_delay:50ms/";
    private static EglBase egl;
    private static boolean initialized;
    public static synchronized EglBase.Context eglContext(){if(egl==null)egl=EglBase.create();return egl.getEglBaseContext();}
    private final Context context;
    private final Link link;
    private final Events events;
    private final ScheduledExecutorService rtc=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean stopped=new AtomicBoolean();
    private final boolean sender;
    private final SurfaceViewRenderer renderer;
    private PeerConnectionFactory factory;
    private JavaAudioDeviceModule audioModule;
    private PeerConnection peer;
    private ScreenCapturerAndroid capture;
    private MediaProjection ownedProjection;
    private SurfaceTextureHelper texture;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private VideoTrack localVideo, remoteVideo;
    private AudioTrack localAudio;
    private boolean remoteSet;
    private boolean remoteNegotiating;
    private final List<IceCandidate> candidates=new ArrayList<>();
    private final AdaptationPolicy adaptation;
    private final StreamQuality quality;
    private MediaProfile profile;
    private int sourceWidth=540,sourceHeight=960;
    private long previousFrames,previousPackets,previousLost,receivedFrames;
    private double previousTime;
    private volatile long remoteDecode=999,remoteJitter=999;
    private volatile int remoteLoss=100;
    private long remoteStatsAt,remoteFrames=-1,previousStatsAt,previousBytes;
    private String remoteDecoder="нет данных";
    private boolean remoteJitterKnown;
    private final IntervalAverage receiveBuffer=new IntervalAverage(),sendDelay=new IntervalAverage();
    private boolean connected;
    private final SessionDeadline deadline=new SessionDeadline(SystemClock.elapsedRealtime());
    private String endingReason="Трансляция завершена";

    public MediaEngine(Context c,Link l,SurfaceViewRenderer view,Intent grant,boolean audio,StreamQuality selected,Events e){
        context=c.getApplicationContext();link=l;renderer=view;sender=grant!=null;events=e;
        quality=Objects.requireNonNull(selected);adaptation=new AdaptationPolicy(quality);profile=adaptation.current();
        rtc.execute(()->{try{initialize(grant,audio);link.start(this);rtc.scheduleWithFixedDelay(this::stats,1,1,TimeUnit.SECONDS);}
            catch(Exception failure){fail("Не удалось запустить трансляцию: "+failure.getClass().getSimpleName());}});
    }
    private void initialize(Intent grant,boolean audio) throws Exception {
        if(stopped.get()||link.isClosed())throw new IllegalStateException("Connection closed");
        synchronized(MediaEngine.class){if(!initialized){PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).setEnableInternalTracer(false).createInitializationOptions());initialized=true;}}
        audioModule=JavaAudioDeviceModule.builder(context).setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false).setUseStereoInput(false).setUseStereoOutput(true)
            .setSampleRate(48000).setUseLowLatency(true).setEnableVolumeLogger(false).createAudioDeviceModule();
        factory=PeerConnectionFactory.builder().setFieldTrials(LOW_DELAY).setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglContext(),false,true))
            .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglContext())).createPeerConnectionFactory();
        PeerConnection.RTCConfiguration config=new PeerConnection.RTCConfiguration(Collections.emptyList());
        config.sdpSemantics=PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy=PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        config.iceCandidatePoolSize=0;
        peer=factory.createPeerConnection(config,new Observer());
        if(peer==null)throw new IllegalStateException("WebRTC unavailable");
        if(sender){
            DisplayMetrics d=new DisplayMetrics();((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(d);
            sourceWidth=d.widthPixels;sourceHeight=d.heightPixels;
            videoSource=factory.createVideoSource(true);
            texture=SurfaceTextureHelper.create("SoraCast-capture",eglContext());
            capture=new ScreenCapturerAndroid(grant,new MediaProjection.Callback(){
                @Override public void onStop(){close();}
                @Override public void onCapturedContentResize(int width,int height){execute(()->{sourceWidth=width;sourceHeight=height;resize();});}
            });
            capture.initialize(texture,context,videoSource.getCapturerObserver());
            MediaProfile.Size size=profile.fit(sourceWidth,sourceHeight);
            videoSource.adaptOutputFormat(size.width,size.height,profile.fps);
            capture.startCapture(size.width,size.height,profile.fps);
            localVideo=factory.createVideoTrack("screen",videoSource);
            RtpTransceiver video=peer.addTransceiver(localVideo,new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY));
            List<RtpCapabilities.CodecCapability> codecs=factory.getRtpSenderCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO).codecs;
            List<RtpCapabilities.CodecCapability> preferred=new ArrayList<>(codecs);
            preferred.sort(Comparator.comparingInt(codec->"H264".equalsIgnoreCase(codec.name)?0:1));
            video.setCodecPreferences(preferred);
            if(audio){
                ownedProjection=capture.getMediaProjection();PlaybackRecordFactory.configure(ownedProjection);
                MediaConstraints constraints=new MediaConstraints();
                for(String name:Arrays.asList("googEchoCancellation","googAutoGainControl","googNoiseSuppression","googHighpassFilter"))constraints.mandatory.add(new MediaConstraints.KeyValuePair(name,"false"));
                audioSource=factory.createAudioSource(constraints);localAudio=factory.createAudioTrack("playback",audioSource);
                peer.addTransceiver(localAudio,new RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY));
            }
            configureBitrate();
            peer.createOffer(new Sdp(){@Override public void onCreateSuccess(SessionDescription d){local(d);}},new MediaConstraints());
        }
        events.status("Подключение медиапотока…");
        if(sender)deadline.mediaStarted(SystemClock.elapsedRealtime());
        rtc.scheduleWithFixedDelay(()->{if(!connected&&deadline.expired(SystemClock.elapsedRealtime()))fail("Нет медиасоединения. Проверьте разрешение экрана, общую сеть и VPN.");},1,1,TimeUnit.SECONDS);
    }
    private void configureBitrate(){
        peer.setBitrate(250000,profile.bitrate,profile.bitrate);
        for(RtpSender s:peer.getSenders()){
            if(s.track() instanceof VideoTrack){RtpParameters p=s.getParameters();
                for(RtpParameters.Encoding enc:p.encodings){enc.maxBitrateBps=profile.bitrate;enc.maxFramerate=profile.fps;}
                p.degradationPreference=quality==StreamQuality.AUTO ? RtpParameters.DegradationPreference.BALANCED : RtpParameters.DegradationPreference.MAINTAIN_RESOLUTION;
                if(!p.encodings.isEmpty()&&!s.setParameters(p))throw new IllegalStateException("Video parameters rejected");}
        }
    }
    private void resize(){if(capture!=null&&!stopped.get()){
        MediaProfile.Size size=profile.fit(sourceWidth,sourceHeight);videoSource.adaptOutputFormat(size.width,size.height,profile.fps);
        capture.changeCaptureFormat(size.width,size.height,profile.fps);configureBitrate();}}
    public void displayChanged(int width,int height){execute(()->{sourceWidth=width;sourceHeight=height;resize();});}
    private void local(SessionDescription d){execute(()->peer.setLocalDescription(new Sdp(){@Override public void onSetSuccess(){
        try{if(sender)configureBitrate();JSONObject m=Link.json("sdp");m.put("kind",d.type.canonicalForm());m.put("sdp",d.description);link.send(m);}
        catch(Exception e){fail("Ошибка описания потока");}}},d));}
    @Override public void message(JSONObject m){execute(()->{try{
        switch(m.getString("type")){
            case "sdp":
                String kind=m.getString("kind"),body=m.getString("sdp");
                if(remoteNegotiating || body.length()>131072 || !body.contains("a=fingerprint:sha-256 ") || !(sender?"answer":"offer").equals(kind))throw new IllegalArgumentException("Invalid SDP");
                remoteNegotiating=true;if(!sender)deadline.mediaStarted(SystemClock.elapsedRealtime());
                peer.setRemoteDescription(new Sdp(){@Override public void onSetSuccess(){execute(()->{
                    remoteSet=true;if(sender)configureBitrate();for(IceCandidate i:candidates)peer.addIceCandidate(i);candidates.clear();
                    if(!sender)peer.createAnswer(new Sdp(){@Override public void onCreateSuccess(SessionDescription d){local(d);}},new MediaConstraints());
                });}},new SessionDescription(SessionDescription.Type.fromCanonicalForm(kind),body));break;
            case "ice":
                String candidate=m.getString("candidate"),mid=m.getString("mid");int line=m.getInt("line");
                if(candidate.length()>4096 || mid.length()>64 || line<0 || line>16)throw new IllegalArgumentException("Invalid ICE");
                IceCandidate i=new IceCandidate(mid,line,candidate);
                if(remoteSet)peer.addIceCandidate(i);else{if(candidates.size()>=128)throw new IllegalArgumentException("ICE overflow");candidates.add(i);}break;
            case "stats":
                if(!sender)throw new IllegalArgumentException("Unexpected stats");
                remoteDecode=m.getLong("decode");remoteJitter=m.getLong("jitter");remoteLoss=m.getInt("loss");remoteFrames=m.optLong("frames",-1);
                remoteDecoder=m.optString("decoder","нет данных").replace('\n',' ').replace('\r',' ');remoteJitterKnown=m.optBoolean("jitterKnown",true);
                if(remoteDecode<0||remoteDecode>60000||remoteJitter<0||remoteJitter>60000||remoteLoss<0||remoteLoss>100||remoteFrames< -1||remoteFrames>10000||remoteDecoder.length()>96)throw new IllegalArgumentException("Invalid stats");
                remoteStatsAt=SystemClock.elapsedRealtime();break;
            case "stop":close();break;
            default:throw new IllegalArgumentException("Unknown message");
        }
    }catch(Exception e){fail("Несовместимое сообщение от устройства");}});}
    private static double n(Map<String,Object> m,String key,double fallback){Object o=m.get(key);double value=o instanceof Number?((Number)o).doubleValue():fallback;return Double.isFinite(value)?value:fallback;}
    private static String implementation(Map<String,Object> m,String key){Object o=m.get(key);if(!(o instanceof String))return "нет данных";
        String text=((String)o).replace('\n',' ').replace('\r',' ');return text.substring(0,Math.min(80,text.length()));}
    private static String ms(long value){return value<0?"нет данных":value+" мс";}
    private void stats(){if(peer==null||stopped.get()||!connected)return;peer.getStats(report->execute(()->{
        if(stopped.get())return;
        long rtt=-1;
        for(RTCStats item:report.getStatsMap().values())if("transport".equals(item.getType())){
            Object id=item.getMembers().get("selectedCandidatePairId");RTCStats pair=id instanceof String?report.getStatsMap().get(id):null;
            if(pair!=null){double seconds=n(pair.getMembers(),"currentRoundTripTime",-1);if(seconds>=0)rtt=Math.round(seconds*1000);}
        }
        for(RTCStats s:report.getStatsMap().values()){
            Map<String,Object> m=s.getMembers();
            if(sender&&"outbound-rtp".equals(s.getType())&&"video".equals(m.get("kind"))){
                long frames=(long)n(m,"framesEncoded",0);double time=n(m,"totalEncodeTime",0);long delta=frames-previousFrames;
                long encode=delta>0?Math.max(0,Math.round((time-previousTime)*1000/delta)):-1;
                if(delta>0){
                    boolean fresh=remoteStatsAt>0&&SystemClock.elapsedRealtime()-remoteStatsAt<3000;
                    // Sender is active: absent decoder frames or expired feedback is a stall, not screen idle.
                    MediaProfile next=adaptation.sample(encode,fresh?remoteDecode:0,fresh?remoteJitter:0,fresh?remoteLoss:0,!fresh||remoteFrames==0);
                    if(next!=profile){profile=next;resize();}
                }
                int width=(int)n(m,"frameWidth",0),height=(int)n(m,"frameHeight",0);
                long now=SystemClock.elapsedRealtime(),bytes=(long)n(m,"bytesSent",0);
                double fps=n(m,"framesPerSecond",previousStatsAt>0?Math.max(0,delta)*1000.0/Math.max(1,now-previousStatsAt):0);
                String actual=width>0&&height>0?width+" × "+height:"разрешение уточняется";
                String rate=previousStatsAt>0?String.format(Locale.ROOT," · %.1f Мбит/с",Math.max(0,bytes-previousBytes)*.008/Math.max(1,now-previousStatsAt)):"";
                events.status(quality.title+" · поток "+actual+" · "+Math.round(fps)+" кадр/с"+rate);
                long outgoing=sendDelay.sample(Math.max(0,n(m,"totalPacketSendDelay",0)),Math.max(0,(long)n(m,"packetsSent",0)));
                boolean fresh=remoteStatsAt>0&&now-remoteStatsAt<3000;
                events.diagnostics("Режим: "+quality.title+"\nПоток: "+actual+" · "+Math.round(fps)+" кадр/с"+rate
                    +"\nКодирование кадра: "+ms(encode)+"\nОжидание отправки пакета (ср.): "+ms(outgoing)
                    +"\nTV · декодирование кадра: "+ms(fresh&&remoteFrames!=0?remoteDecode:-1)+"\nTV · буфер видео (ср.): "+ms(fresh&&remoteJitterKnown?remoteJitter:-1)
                    +"\nTV · кадров за интервал: "+(fresh&&remoteFrames>=0?remoteFrames:"нет данных")
                    +"\nПотери пакетов на TV: "+(fresh?remoteLoss+"%":"нет данных")+"\nСеть · RTT: "+ms(rtt)
                    +"\nКодировщик: "+implementation(m,"encoderImplementation")+"\nДекодер TV: "+remoteDecoder
                    +"\n\nRTT — путь туда и обратно; это не полная задержка экрана. Средние обновляются по последнему интервалу. При статичном экране FPS может снижаться.");
                previousStatsAt=now;previousBytes=bytes;
                previousFrames=frames;previousTime=time;
            }else if(!sender&&"inbound-rtp".equals(s.getType())&&"video".equals(m.get("kind"))){
                long frames=(long)n(m,"framesDecoded",0),packets=(long)n(m,"packetsReceived",0),lost=(long)n(m,"packetsLost",0);
                double time=n(m,"totalDecodeTime",0);long delta=frames-previousFrames;
                long decode=delta>0?Math.max(0,Math.round((time-previousTime)*1000/delta)):0;
                long jitter=receiveBuffer.sample(Math.max(0,n(m,"jitterBufferDelay",0)),Math.max(0,(long)n(m,"jitterBufferEmittedCount",0)));
                long count=(packets-previousPackets)+Math.max(0,lost-previousLost);
                int loss=count>0?(int)Math.min(100,100*Math.max(0,lost-previousLost)/count):0;
                // Report real transport feedback even when the decoder produced no new frame.
                try{JSONObject info=Link.json("stats");info.put("decode",decode).put("jitter",Math.max(0,jitter)).put("jitterKnown",jitter>=0).put("loss",loss).put("frames",Math.max(0,delta)).put("decoder",implementation(m,"decoderImplementation"));link.send(info);}catch(Exception e){close();}
                events.status("Приём · "+Math.max(0,delta)+" кадр/с · декодирование "+decode+" мс · буфер "+ms(jitter));
                previousFrames=frames;previousTime=time;previousPackets=packets;previousLost=lost;
            }
        }
    }));}
    private void execute(Runnable r){if(!stopped.get())try{rtc.execute(()->{if(!stopped.get())try{r.run();}catch(Exception e){fail("Ошибка медиапотока");}});}catch(RejectedExecutionException ignored){}}
    private void fail(String text){endingReason=text;events.status(text);close();}
    @Override public void closed(){close();}
    public void close(){if(!stopped.compareAndSet(false,true))return;link.close();rtc.execute(()->{
        try{if(capture!=null)capture.stopCapture();}catch(Exception ignored){}
        try{if(remoteVideo!=null&&renderer!=null)remoteVideo.removeSink(renderer);if(peer!=null)peer.dispose();
            if(localVideo!=null)localVideo.dispose();if(localAudio!=null)localAudio.dispose();
            if(capture!=null)capture.dispose();if(texture!=null)texture.dispose();if(videoSource!=null)videoSource.dispose();if(audioSource!=null)audioSource.dispose();
            if(factory!=null)factory.dispose();if(audioModule!=null)audioModule.release();if(ownedProjection!=null)PlaybackRecordFactory.clear(ownedProjection);
        }finally{events.ended(endingReason);rtc.shutdownNow();}
    });}
    private class Sdp implements SdpObserver {
        public void onCreateSuccess(SessionDescription d){}public void onSetSuccess(){}
        public void onCreateFailure(String error){fail("Не удалось согласовать кодек");}public void onSetFailure(String error){fail("Ошибка согласования потока");}
    }
    private class Observer implements PeerConnection.Observer {
        public void onSignalingChange(PeerConnection.SignalingState state){}
        public void onIceConnectionChange(PeerConnection.IceConnectionState state){}
        public void onIceConnectionReceivingChange(boolean value){}
        public void onIceGatheringChange(PeerConnection.IceGatheringState state){}
        public void onIceCandidatesRemoved(IceCandidate[] items){}
        public void onAddStream(MediaStream stream){}public void onRemoveStream(MediaStream stream){}
        public void onDataChannel(DataChannel channel){channel.close();channel.dispose();}public void onRenegotiationNeeded(){}
        @Override public void onConnectionChange(PeerConnection.PeerConnectionState state){execute(()->{
            if(state==PeerConnection.PeerConnectionState.CONNECTED){connected=true;events.status("Экран подключён");}
            if(state==PeerConnection.PeerConnectionState.FAILED||state==PeerConnection.PeerConnectionState.CLOSED)fail("Медиасоединение завершено");});}
        public void onIceCandidate(IceCandidate c){try{JSONObject j=Link.json("ice");j.put("candidate",c.sdp).put("mid",c.sdpMid).put("line",c.sdpMLineIndex);link.send(j);}catch(Exception e){close();}}
        @Override public void onTrack(RtpTransceiver t){if(!sender&&t.getReceiver().track() instanceof VideoTrack){
            remoteVideo=(VideoTrack)t.getReceiver().track();remoteVideo.addSink(renderer);}}
    }
}

