package app.nebo.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/** Offline JVM checks: no Android device, network or screen capture. */
public final class CoreSpec {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(new AdaptationPolicy().current() == MediaProfile.BALANCED,
                "automatic connection starts at 720p instead of blurry light mode");
        UpdateSpec.main(args); intervalAverage(); geometry(); qualitySelection(); adaptation(); frameSlot(); pairing(); sessionOwnership();
        System.out.println("PASS: " + checks + " assertions (geometry, adaptation, frames, pairing, ownership)");
    }
    static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    static void intervalAverage() {
        IntervalAverage a=new IntervalAverage();
        check(a.sample(1,100)==10,"first counter sample is available");
        check(a.sample(3,110)==200,"buffer growth is not hidden by lifetime average");
        check(a.sample(3.05,120)==5,"buffer recovery is visible immediately");
        check(a.sample(3.05,120)==-1,"zero activity has no made-up timing");
        check(a.sample(.5,5)==-1,"counter reset rebases without negative timing");
        check(a.sample(.6,10)==20,"sample after reset is valid");
        reject(()->a.sample(Double.NaN,20),"NaN counter rejected");
        reject(()->a.sample(Double.POSITIVE_INFINITY,20),"infinite counter rejected");
        reject(()->a.sample(-1,20),"negative time rejected");
        reject(()->a.sample(1,-1),"negative count rejected");
        check(a.sample(.8,20)==20,"invalid samples do not corrupt history");
        check(new IntervalAverage().sample(0,0)==-1,"empty counter has no average");
    }
    static void reject(Runnable action, String name) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
        check(rejected, name);
    }
    static void geometry() {
        MediaProfile.Size portrait = MediaProfile.LIGHT.fit(1216, 2640);
        check(portrait.width <= 540 && portrait.height <= 960, "portrait stays inside rotated light bounds");
        check(Math.abs(portrait.width / (double) portrait.height - 1216.0 / 2640) < .005, "portrait is not stretched");
        check(portrait.width % 2 == 0 && portrait.height % 2 == 0, "codec dimensions are even");
        MediaProfile.Size landscape = MediaProfile.BALANCED.fit(2640, 1216);
        check(landscape.width <= 1280 && landscape.height <= 720, "landscape bounded");
        MediaProfile.Size small = MediaProfile.LIGHT.fit(640, 360);
        check(small.width == 640 && small.height == 360, "never upscale a small source");
        MediaProfile.Size square = MediaProfile.LIGHT.fit(1000, 1000);
        check(square.width == square.height, "square stays square");
        reject(() -> MediaProfile.LIGHT.fit(0, 1920), "zero input rejected");
        reject(() -> MediaProfile.LIGHT.fit(Integer.MAX_VALUE, 1920), "unbounded size rejected");
    }
    static void adaptation() {
        AdaptationPolicy p = new AdaptationPolicy();
        check(p.current() == MediaProfile.BALANCED, "auto starts at 720p");
        for (int i = 0; i < 19; i++) p.sample(4, 4, 20, 0);
        check(p.current() == MediaProfile.BALANCED, "no premature upgrade");
        p.sample(4, 4, 20, 0);
        check(p.current() == MediaProfile.FULL_HD, "sustained good path upgrades to 1080p");
        p.sample(80, 80, 200, 10);
        check(p.current() == MediaProfile.FULL_HD, "one spike does not oscillate");
        p.sample(80, 80, 200, 10); p.sample(80, 80, 200, 10);
        check(p.current() == MediaProfile.BALANCED, "persistent overload lowers one step");
        for (int i=0;i<3;i++) p.sample(80,80,200,10);
        check(p.current() == MediaProfile.LIGHT, "weak receiver can reach light mode");
        for (int i = 0; i < 18; i++) p.sample(4, 4, 20, 0);
        check(p.current() == MediaProfile.LIGHT, "recovery has hysteresis");
        p.sample(4, 4, 20, 0); p.sample(4, 4, 20, 0);
        check(p.current() == MediaProfile.BALANCED, "recovery eventually allowed");
        reject(() -> p.sample(-1, 2, 3, 0), "negative timing rejected");
        reject(() -> p.sample(1, 2, 3, 101), "invalid loss rejected");
    }
    static void qualitySelection() {
        check(StreamQuality.fromId(null)==StreamQuality.AUTO, "missing preference defaults to auto");
        check(StreamQuality.fromId("unknown")==StreamQuality.AUTO, "unknown preference defaults to auto");
        for(StreamQuality q:StreamQuality.values()) {
            check(StreamQuality.fromId(q.name())==q, "saved quality round trip: "+q);
            AdaptationPolicy p=new AdaptationPolicy(q);
            check(p.current()==q.initial, "session starts at selected quality: "+q);
            if(q!=StreamQuality.AUTO) {
                for(int i=0;i<50;i++)p.sample(200,200,400,30);
                check(p.current()==q.initial, "manual quality not silently reset: "+q);
            }
            MediaProfile.Size portrait=q.initial.fit(1216,2640), landscape=q.initial.fit(2640,1216);
            check(portrait.width==landscape.height&&portrait.height==landscape.width, "rotation preserves quality: "+q);
            check(portrait.width<=1216&&portrait.height<=2640, "no source upscaling: "+q);
            check(portrait.width%2==0&&portrait.height%2==0, "codec dimensions even: "+q);
            check(Math.abs(portrait.width/(double)portrait.height-1216.0/2640)<.005, "aspect preserved: "+q);
        }
        check(StreamQuality.QHD60.initial.fps==60&&StreamQuality.QHD60.initial.height==1440, "2K60 reaches real media profile");
        AdaptationPolicy capped=new AdaptationPolicy();
        for(int i=0;i<100;i++)capped.sample(4,4,20,0);
        check(capped.current()==MediaProfile.FULL_HD, "auto never forces 2K on weak devices");
        for(int i=0;i<3;i++)capped.sample(4,0,20,0,true);
        check(capped.current()==MediaProfile.BALANCED, "active sender with stalled decoder falls back despite zero decode time");
        for(int i=0;i<3;i++)capped.sample(60,0,0,0,true);
        check(capped.current()==MediaProfile.LIGHT, "missing feedback never disables overload fallback");
    }
    static void frameSlot() {
        LatestFrameSlot<Integer> slot = new LatestFrameSlot<>();
        check(slot.replace(1) == null, "first frame has no displaced frame");
        check(slot.replace(2) == 1, "old frame returned for release");
        for (int i = 3; i <= 10000; i++) slot.replace(i);
        check(slot.take() == 10000, "slow receiver gets newest frame, not growing backlog");
        check(slot.take() == null, "taking empties slot");
        check(slot.displacedCount() == 9999, "drop count visible");
        reject(() -> slot.replace(null), "null frame rejected");
    }
    static void pairing() throws Exception {
        PairingWindow window = new PairingWindow();
        byte[] original = new byte[32]; original[0] = 42;
        long start = 1_000_000;
        window.open(original, start);
        original[0] = 0;
        byte[] proof = new byte[32]; proof[0] = 42;
        byte[] wrong = new byte[32];
        check(!window.claim(wrong, start + 1000), "wrong token does not claim");
        check(window.claim(proof, start + 119000), "token copied and usable before expiry");
        check(!window.claim(proof, start + 119000), "token is single use");
        window.open(proof, start);
        check(!window.claim(proof, start + 120000), "expiry boundary closed");
        window.open(proof, start);
        check(!window.claim(proof, start - 1000), "backwards clock is rejected");
        reject(() -> window.open(new byte[4], start), "short token rejected");
        window.open(proof, start);
        AtomicInteger wins = new AtomicInteger();
        CountDownLatch gate = new CountDownLatch(1);
        Thread[] threads = new Thread[12];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try { gate.await(); if (window.claim(proof, start + 1000)) wins.incrementAndGet(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }); threads[i].start();
        }
        gate.countDown(); for (Thread t : threads) t.join();
        check(wins.get() == 1, "concurrent requests cannot reuse pairing token");
    }
    static void sessionOwnership() {
        SessionPolicy policy = new SessionPolicy();
        check(policy.request("vivo") == SessionPolicy.Request.START, "first sender owns session");
        check(policy.request("oppo") == SessionPolicy.Request.NEEDS_TV_CONFIRMATION, "second sender needs approval");
        check("vivo".equals(policy.active()), "request cannot steal active session");
        check(!policy.confirmSwitch("stranger"), "unrequested switch refused");
        check(policy.confirmSwitch("oppo"), "TV can confirm pending sender");
        check("oppo".equals(policy.active()), "confirmed switch changes owner");
        check(!policy.stop("vivo"), "old owner cannot stop new owner");
        check(policy.stop("oppo") && policy.active() == null, "active owner can stop");
        check(policy.request("vivo") == SessionPolicy.Request.START, "next owner allowed after stop");
        policy.request("oppo"); policy.revoke("oppo");
        check(!policy.confirmSwitch("oppo"), "revoking pending sender cancels switch");
        policy.revoke("vivo"); check(policy.active() == null, "revoking active sender releases session");
        reject(() -> policy.request(""), "empty identity rejected");
    }
}
