package app.nebo.core;

public final class AdmissionSpec {
    static int checks;
    static void check(boolean b){checks++;if(!b)throw new AssertionError("admission "+checks);}
    public static void main(String[] args) throws Exception {
        SessionAdmission gate=new SessionAdmission();
        SessionAdmission.Ticket known=gate.begin(true,false);check(!known.needsApproval);gate.revoke();check(!gate.accept(known));
        SessionAdmission.Ticket newPending=gate.begin(false,false);check(newPending.needsApproval);gate.revoke();check(!gate.accept(newPending));
        SessionAdmission.Ticket switching=gate.begin(false,true);check(switching.needsApproval);check(gate.accept(switching));check(!gate.accept(switching));
        SessionAdmission.Ticket pairedSwitch=gate.begin(true,true);check(pairedSwitch.needsApproval);check(gate.accept(pairedSwitch));
        SessionAdmission.Ticket concurrent=gate.begin(true,false);
        java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1),resume=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean accepted=new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread worker=new Thread(()->{ready.countDown();try{resume.await();accepted.set(gate.accept(concurrent));}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        worker.start();ready.await();gate.revoke();resume.countDown();worker.join(5000);check(!worker.isAlive());check(!accepted.get());
        System.out.println("PASS: "+checks+" authorization/revocation assertions");
    }
}
