package app.nebo.core;

import java.io.*;
import java.security.*;
import java.util.Arrays;

public final class ProtocolSpec {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    interface Attempt { void run() throws Exception; }
    static void reject(Attempt action) throws Exception {
        checks++; try { action.run(); } catch (IllegalArgumentException | IOException expected) { return; }
        throw new AssertionError("Expected rejection " + checks);
    }
    public static void main(String[] args) throws Exception {
        byte[] nonce = new byte[32]; Arrays.fill(nonce, (byte) 3);
        String pin = "ab".repeat(32);
        String uri = "soracast://pair?v=1&host=192.168.31.42&port=48521&pin=" + pin + "&token=" + Protocol.hex(nonce);
        Protocol.Offer offer = Protocol.parseOffer(uri);
        check(offer.host.equals("192.168.31.42") && offer.port == 48521 && offer.pin.equals(pin));
        check(Arrays.equals(offer.token, nonce));
        check(Protocol.parseOffer(offer.uri()).uri().equals(uri));
        for (String host : new String[]{"8.8.8.8", "127.0.0.1", "169.254.1.1", "192.168.31.999", "192.168.31.01", "example.com", "0.0.0.0"})
            reject(() -> Protocol.parseOffer(uri.replace("192.168.31.42", host)));
        for (String host : new String[]{"10.0.1.2", "172.16.0.2", "172.31.255.1"}) check(Protocol.privateIpv4(host));
        reject(() -> Protocol.parseOffer(uri + "&port=1234"));
        reject(() -> Protocol.parseOffer(uri + "&x=1"));
        reject(() -> Protocol.parseOffer(uri.replace("v=1", "v=2")));
        reject(() -> Protocol.parseOffer(uri.replace("48521", "0")));
        reject(() -> Protocol.parseOffer(uri.replace("48521", "65536")));
        reject(() -> Protocol.parseOffer(uri.replace(pin, "abc")));
        reject(() -> Protocol.parseOffer("x".repeat(2049)));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Protocol.write(new DataOutputStream(bytes), "{\"name\":\"Приставка\"}");
        check(Protocol.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))).contains("Приставка"));
        reject(() -> Protocol.write(new DataOutputStream(new ByteArrayOutputStream()), "x".repeat(262145)));
        reject(() -> Protocol.read(new DataInputStream(new ByteArrayInputStream(new byte[]{-1,-1,-1,-1}))));
        reject(() -> Protocol.read(new DataInputStream(new ByteArrayInputStream(new byte[]{0,0,0,4,0}))));
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC"); gen.initialize(256);
        KeyPair key = gen.generateKeyPair();
        byte[] second = new byte[32]; Arrays.fill(second, (byte) 5);
        byte[] transcript = Protocol.transcript(pin, nonce, second);
        Signature signer = Signature.getInstance("SHA256withECDSA"); signer.initSign(key.getPrivate()); signer.update(transcript);
        byte[] sig = signer.sign();
        check(Protocol.verify(key.getPublic().getEncoded(), transcript, sig));
        check(!Protocol.verify(key.getPublic().getEncoded(), Protocol.transcript("cd".repeat(32), nonce, second), sig));
        check(!Protocol.verify(key.getPublic().getEncoded(), Protocol.transcript(pin, second, nonce), sig));
        check(!Protocol.verify(new byte[10], transcript, sig));
        reject(() -> Protocol.transcript(pin, new byte[1], second));
        System.out.println("PASS: " + checks + " protocol assertions (QR, LAN boundary, framing, signed challenge/replay)");
    }
}

