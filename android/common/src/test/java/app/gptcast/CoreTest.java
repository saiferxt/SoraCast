package app.gptcast;

import org.junit.Test;
import app.nebo.core.*;

public final class CoreTest {
    @Test public void corePolicies() throws Exception {CoreSpec.main(new String[0]);}
    @Test public void secureProtocol() throws Exception {ProtocolSpec.main(new String[0]);}
    @Test public void permissionDeadline(){DeadlineSpec.main(new String[0]);}
    @Test public void revocationDuringApproval() throws Exception {AdmissionSpec.main(new String[0]);}
}
