package com.sqmusicplus.v3.utils;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.security.cert.CertificateException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLPeerUnverifiedException;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Retry only QQ login connection failures before request headers start. */
public final class QqLoginTransport {
    private static final Logger LOG=LoggerFactory.getLogger(QqLoginTransport.class);
    private static final Set<String> HOSTS=Set.of("u.y.qq.com","u6.y.qq.com","ssl.ptlogin2.qq.com","ssl.ptlogin2.graph.qq.com","graph.qq.com");
    private static final ConcurrentHashMap<Call,Trace> TRACES=new ConcurrentHashMap<>();
    private static final OkHttpClient EXCHANGE=new OkHttpClient.Builder()
        .connectTimeout(8,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
        .writeTimeout(8,TimeUnit.SECONDS).callTimeout(60,TimeUnit.SECONDS)
        .retryOnConnectionFailure(false).followRedirects(false)
        .eventListenerFactory(QqLoginTransport::listener)
        .addInterceptor(QqLoginTransport::intercept).build();

    private static final OkHttpClient READ_ONLY=EXCHANGE.newBuilder().callTimeout(18,TimeUnit.SECONDS).build();

    public static final class Trace extends EventListener {
        volatile boolean headersStarted;
        @Override public void requestHeadersStart(Call call){headersStarted=true;}
        @Override public void callEnd(Call call){TRACES.remove(call);}
        @Override public void callFailed(Call call,IOException error){TRACES.remove(call);}
    }
    public static EventListener listener(Call call){
        Trace trace=new Trace();TRACES.put(call,trace);return trace;
    }
    public static Response intercept(Interceptor.Chain chain) throws IOException {
        Request request=chain.request();Trace trace=TRACES.get(chain.call());
        if(!HOSTS.contains(request.url().host()) || trace==null)return chain.proceed(request);
        Interceptor.Chain bounded=chain.withConnectTimeout(8,TimeUnit.SECONDS)
            .withReadTimeout(20,TimeUnit.SECONDS).withWriteTimeout(8,TimeUnit.SECONDS);
        for(int attempt=1;;attempt++){
            trace.headersStarted=false;
            try{return bounded.proceed(request);}
            catch(IOException error){
                if(attempt>=3 || trace.headersStarted || chain.call().isCanceled()
                    || Thread.currentThread().isInterrupted() || certificateFailure(error))throw error;
                LOG.warn("QQ_LOGIN_RETRY phase=connect attempt={} error_type={}",attempt+1,error.getClass().getSimpleName());
                try{Thread.sleep(attempt==1?300:900);}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new InterruptedIOException("QQ login canceled");}
            }
        }
    }
    private static boolean certificateFailure(Throwable error){
        for(Throwable t=error;t!=null;t=t.getCause())
            if(t instanceof CertificateException || t instanceof SSLPeerUnverifiedException)return true;
        return false;
    }
    public static String exchange(String url,String payload) throws IOException { return execute(EXCHANGE,url,payload); }
    public static String readOnlyApi(String url,String payload) throws IOException { return execute(READ_ONLY,url,payload); }
    private static String execute(OkHttpClient client,String url,String payload) throws IOException {
        Request request=new Request.Builder().url(url)
            .header("Referer","https://y.qq.com/")
            .header("User-Agent","QQ%E9%9F%B3%E4%B9%90/73222 CFNetwork/1406.0.3 Darwin/22.4.0")
            .post(RequestBody.create(payload,MediaType.get("application/json; charset=utf-8"))).build();
        try(Response response=client.newCall(request).execute()){
            if(!response.isSuccessful() || response.body()==null)throw new IOException("QQ login HTTP failure status="+response.code());
            return response.body().string();
        }
    }
}
