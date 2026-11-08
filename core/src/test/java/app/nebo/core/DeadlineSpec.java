package app.nebo.core;

public final class DeadlineSpec {
    private static int checks;
    private static void check(boolean b){checks++;if(!b)throw new AssertionError("deadline "+checks);}
    public static void main(String[] args){
        SessionDeadline pending=new SessionDeadline(1000);
        check(!pending.expired(31001));check(!pending.expired(120999));check(pending.expired(121000));
        SessionDeadline negotiating=new SessionDeadline(1000);negotiating.mediaStarted(61000);
        check(!negotiating.expired(90000));check(negotiating.expired(91000));
        negotiating.mediaStarted(90000);check(negotiating.expired(91000));
        check(negotiating.expired(500));
        System.out.println("PASS: "+checks+" permission/negotiation deadline assertions");
    }
}
