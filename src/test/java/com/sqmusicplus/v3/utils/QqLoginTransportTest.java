package com.sqmusicplus.v3.utils;
import java.io.IOException;
import java.net.ConnectException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLHandshakeException;
import okhttp3.*;
import okio.Timeout;

public class QqLoginTransportTest {
    static class FakeCall implements Call {
        final Request request;boolean canceled;
        FakeCall(String host){request=new Request.Builder().url("https://"+host+"/test").build();}
        public Request request(){return request;}
        public Response execute(){throw new UnsupportedOperationException();}
        public void enqueue(Callback callback){throw new UnsupportedOperationException();}
        public void cancel(){canceled=true;}
        public boolean isExecuted(){return true;}
        public boolean isCanceled(){return canceled;}
        public Timeout timeout(){return Timeout.NONE;}
        public Call clone(){return new FakeCall(request.url().host());}
    }
    static class FakeChain implements Interceptor.Chain {
        final FakeCall call;final EventListener listener;final int failures;int calls;boolean headers;int status=200;IOException failure=new SSLHandshakeException("test transport reset");
        FakeChain(String host,int failures){call=new FakeCall(host);this.failures=failures;listener=QqLoginTransport.listener(call);}
        public Request request(){return call.request;}
        public Call call(){return call;}
        public Connection connection(){return null;}
        public int connectTimeoutMillis(){return 1000;}
        public int readTimeoutMillis(){return 1000;}
        public int writeTimeoutMillis(){return 1000;}
        public Interceptor.Chain withConnectTimeout(int t,TimeUnit u){return this;}
        public Interceptor.Chain withReadTimeout(int t,TimeUnit u){return this;}
        public Interceptor.Chain withWriteTimeout(int t,TimeUnit u){return this;}
        public Response proceed(Request request) throws IOException {
            calls++;
            if(headers)listener.requestHeadersStart(call);
            if(calls<=failures)throw failure;
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("test")
                .body(ResponseBody.create("{\"code\":500}",MediaType.get("application/json"))).build();
        }
    }
    static int passed;
    static void check(FakeChain chain,int count,boolean success) throws Exception {
        boolean returned=false;
        try(Response r=QqLoginTransport.intercept(chain)){returned=true;}
        catch(IOException expected){if(success)throw expected;}
        finally{chain.listener.callEnd(chain.call);}
        if(chain.calls!=count || returned!=success)throw new AssertionError("wrong retry boundary");
        passed++;
    }
    @org.junit.jupiter.api.Test
    public void retrySafety() throws Exception { main(new String[0]); }
    public static void main(String[] args) throws Exception {
        check(new FakeChain("u.y.qq.com",2),3,true);
        FakeChain sent=new FakeChain("u.y.qq.com",2);sent.headers=true;check(sent,1,false);
        FakeChain denied=new FakeChain("u.y.qq.com",0);denied.status=403;check(denied,1,true);
        FakeChain cert=new FakeChain("u.y.qq.com",3);cert.failure.initCause(new CertificateException("untrusted"));check(cert,1,false);
        FakeChain canceled=new FakeChain("u.y.qq.com",3);canceled.call.cancel();check(canceled,1,false);
        check(new FakeChain("u.y.qq.com",10),3,false);
        check(new FakeChain("example.com",3),1,false);
        FakeChain interrupted=new FakeChain("u.y.qq.com",3);Thread.currentThread().interrupt();try{check(interrupted,1,false);}finally{Thread.interrupted();}
        var f=QqLoginTransport.class.getDeclaredField("TRACES");f.setAccessible(true);
        if(!((java.util.Map<?,?>)f.get(null)).isEmpty())throw new AssertionError("trace leak");
        System.out.println("QQ_LOGIN_FAULT_TESTS passed="+passed);
        if(args.length>0 && args[0].equals("--live"))for(int i=0;i<8;i++){
            String text=QqLoginTransport.exchange("https://u.y.qq.com/cgi-bin/musicu.fcg","{}");
            if(!text.stripLeading().startsWith("{"))throw new AssertionError("Tencent returned non-JSON");
            System.out.println("QQ_ANONYMOUS_TLS_PROBE passed="+(i+1));
        }
    }
}
