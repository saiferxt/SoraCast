package app.gptcast;

import java.io.IOException;

/** A phase code and exception types are safe to show; raw auth/JSON payloads are never displayed. */
public final class ConnectionIssue extends IOException {
    private ConnectionIssue(String phase,Exception cause){super(describe(phase,cause),cause);}
    public static ConnectionIssue at(String phase,Exception cause){return cause instanceof ConnectionIssue?(ConnectionIssue)cause:new ConnectionIssue(phase,cause);}
    public static String describe(String phase,Exception cause){
        Throwable root=cause;for(int i=0;i<6&&root.getCause()!=null&&root.getCause()!=root;i++)root=root.getCause();
        return "Сбой подключения ["+phase+"]: "+cause.getClass().getSimpleName()+(root==cause?"":" / "+root.getClass().getSimpleName());
    }
}
