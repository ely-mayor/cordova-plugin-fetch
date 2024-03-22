package com.adobe.phonegap.fetch;

import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLSocketFactory;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Call;
import okhttp3.ConnectionPool;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;

import java.util.concurrent.TimeUnit;

public class FetchPlugin extends CordovaPlugin {

    public static final String LOG_TAG = "FetchPlugin";
    private static CallbackContext callbackContext;

    private OkHttpClient mClient = new OkHttpClient();
    public static final MediaType MEDIA_TYPE_MARKDOWN = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8");

    private static final long DEFAULT_TIMEOUT = 10;


@Override
    protected void pluginInitialize() {
        super.pluginInitialize();
        
	// Initialize   
        
    }

	
@Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {
        if (action.equals("fetch")) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    fetchOperation(data, callbackContext);
                }
            });
            return true;
        } else if (action.equals("setTimeout")) {
            this.setTimeout(data.optLong(0, DEFAULT_TIMEOUT));
            return true;
        } else {
            Log.e(LOG_TAG, "Invalid action: " + action);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }
    }

    private void fetchOperation(JSONArray data, CallbackContext callbackContext) {
        try {
            String method = data.getString(0);
            Log.v(LOG_TAG, "execute: http method  = " + method.toString());

            String urlString = data.getString(1);
            Log.v(LOG_TAG, "execute: urlString = " + urlString.toString());

            String postBody = data.getString(2);
            Log.v(LOG_TAG, "execute: postBody = " + postBody.toString());

            JSONObject headers = data.getJSONObject(3);
            if (headers.has("map") && headers.getJSONObject("map") != null) {
                headers = headers.getJSONObject("map");
            }

            Log.v(LOG_TAG, "execute: headers = " + headers.toString());

            Request.Builder requestBuilder = new Request.Builder();

            // method + postBody
            if (postBody != null && !postBody.equals("null")) {
                String contentType;
                if (headers.has("content-type")) {
                    JSONArray contentTypeHeaders = headers.getJSONArray("content-type");
                    contentType = contentTypeHeaders.getString(0);
                } else {
                    contentType = "application/json";
                }
                requestBuilder.post(RequestBody.create(MediaType.parse(contentType), postBody.toString()));
            } else {
                requestBuilder.method(method, null);
            }

            // url
            requestBuilder.url(urlString);

            // headers
            if (headers != null && headers.names() != null && headers.names().length() > 0) {
                for (int i = 0; i < headers.names().length(); i++) {
                    String headerName = headers.names().getString(i);
                    JSONArray headerValues = headers.getJSONArray(headers.names().getString(i));

                    if (headerValues.length() > 0) {
                        String headerValue = headerValues.getString(0);
                        Log.v(LOG_TAG, "key = " + headerName + " value = " + headerValue);
                        requestBuilder.addHeader(headerName, headerValue);
                    }
                }
            }

            Request request = requestBuilder.build();

            mClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException throwable) {
                    throwable.printStackTrace();
                    callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, throwable.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    handleResponse(response, callbackContext);
                }
            });

        } catch (JSONException e) {
            Log.e(LOG_TAG, "execute: Got JSON Exception " + e.getMessage());
            callbackContext.error(e.getMessage());
        }
    }

    private void handleResponse(Response response, CallbackContext callbackContext) throws IOException {
        JSONObject result = new JSONObject();
        try {
            Headers responseHeaders = response.headers();
            JSONObject allHeaders = new JSONObject();

            if (responseHeaders != null) {
                for (int i = 0; i < responseHeaders.size(); i++) {
                    if (responseHeaders.name(i).compareToIgnoreCase("set-cookie") == 0 &&
                            allHeaders.has(responseHeaders.name(i))) {
                        allHeaders.put(responseHeaders.name(i), allHeaders.get(responseHeaders.name(i)) + ",\n" + responseHeaders.value(i));
                        continue;
                    }
                    allHeaders.put(responseHeaders.name(i), responseHeaders.value(i));
                }
            }

            result.put("headers", allHeaders);

            if (response.body().contentType() != null && response.body().contentType().type().equals("image")) {
                result.put("isBlob", true);
                result.put("body", Base64.encodeToString(response.body().bytes(), Base64.DEFAULT));
            } else {
                result.put("body", response.body().string());
            }

            result.put("statusText", response.message());
            result.put("status", response.code());
            result.put("url", response.request().url().toString());

        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.v(LOG_TAG, "HTTP code: " + response.code());
        Log.v(LOG_TAG, "returning: " + result.toString());

        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
    }

  
private void setTimeout(long seconds) {
    try {
        Log.v(LOG_TAG, "setTimeout: " + seconds);
        SSLContext sslcontext = SSLContext.getInstance("TLSv1.2");
        sslcontext.init(null, null, null);
        SSLSocketFactory noSSLv3Factory = new NoSSLFactory(sslcontext.getSocketFactory());
        mClient = mClient.newBuilder()
                .sslSocketFactory(noSSLv3Factory)
                .connectionPool(new ConnectionPool(5, seconds, TimeUnit.SECONDS))
                .connectTimeout(seconds, TimeUnit.SECONDS)
                .readTimeout(seconds, TimeUnit.SECONDS)
                .writeTimeout(seconds, TimeUnit.SECONDS)
                .build();
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
        Log.e(LOG_TAG, "Error while setting timeout: " + e.getMessage());
    }
}

}
class NoSSLFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;

    public NoSSLFactory() {
        this.delegate = HttpsURLConnection.getDefaultSSLSocketFactory();
    }

    public NoSSLFactory(SSLSocketFactory delegate) {
        this.delegate = delegate;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    private Socket makeSocketSafe(Socket socket) {
        if (socket instanceof SSLSocket) {
            socket = new NoSSLv3SSLSocket((SSLSocket) socket);
        }
        return socket;
    }

    @Override
    public Socket createSocket(Socket s, String host, int port,
            boolean autoClose) throws IOException {
        return makeSocketSafe(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return makeSocketSafe(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost,
            int localPort) throws IOException {
        return makeSocketSafe(delegate.createSocket(host, port, localHost,
                localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return makeSocketSafe(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port,
            InetAddress localAddress, int localPort) throws IOException {
        return makeSocketSafe(delegate.createSocket(address, port,
                localAddress, localPort));
    }

    private class NoSSLv3SSLSocket extends DelegateSSLSocket {

        private NoSSLv3SSLSocket(SSLSocket delegate) {
            super(delegate);

        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
            if (protocols != null && protocols.length == 1
                    && "SSLv3".equals(protocols[0])) {

                List<String> enabledProtocols = new ArrayList<String>(
                        Arrays.asList(delegate.getEnabledProtocols()));
                if (enabledProtocols.size() > 1) {
                    enabledProtocols.remove("SSLv3");
                    System.out.println("Removed SSLv3 from enabled protocols");
                } else {
                    System.out.println("SSL stuck with protocol available for "
                            + String.valueOf(enabledProtocols));
                }
                protocols = enabledProtocols
                        .toArray(new String[enabledProtocols.size()]);
            }

//          super.setEnabledProtocols(protocols);
            super.setEnabledProtocols(new String[]{"TLSv1.2"});
        }
    }

  class DelegateSSLSocket extends SSLSocket {

        protected final SSLSocket delegate;

        DelegateSSLSocket(SSLSocket delegate) {
            this.delegate = delegate;
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return delegate.getEnabledCipherSuites();
        }

        @Override
        public void setEnabledCipherSuites(String[] suites) {
            delegate.setEnabledCipherSuites(suites);
        }

        @Override
        public String[] getSupportedProtocols() {
            return delegate.getSupportedProtocols();
        }

        @Override
        public String[] getEnabledProtocols() {
            return delegate.getEnabledProtocols();
        }

        @Override
        public void setEnabledProtocols(String[] protocols) {
            delegate.setEnabledProtocols(protocols);
        }

        @Override
        public SSLSession getSession() {
            return delegate.getSession();
        }

        @Override
        public void addHandshakeCompletedListener(
                HandshakeCompletedListener listener) {
            delegate.addHandshakeCompletedListener(listener);
        }

        @Override
        public void removeHandshakeCompletedListener(
                HandshakeCompletedListener listener) {
            delegate.removeHandshakeCompletedListener(listener);
        }

        @Override
        public void startHandshake() throws IOException {
            delegate.startHandshake();
        }

        @Override
        public void setUseClientMode(boolean mode) {
            delegate.setUseClientMode(mode);
        }

        @Override
        public boolean getUseClientMode() {
            return delegate.getUseClientMode();
        }

        @Override
        public void setNeedClientAuth(boolean need) {
            delegate.setNeedClientAuth(need);
        }

        @Override
        public void setWantClientAuth(boolean want) {
            delegate.setWantClientAuth(want);
        }

        @Override
        public boolean getNeedClientAuth() {
            return delegate.getNeedClientAuth();
        }

        @Override
        public boolean getWantClientAuth() {
            return delegate.getWantClientAuth();
        }

        @Override
        public void setEnableSessionCreation(boolean flag) {
            delegate.setEnableSessionCreation(flag);
        }

        @Override
        public boolean getEnableSessionCreation() {
            return delegate.getEnableSessionCreation();
        }

        @Override
        public void bind(SocketAddress localAddr) throws IOException {
            delegate.bind(localAddr);
        }

        @Override
        public synchronized void close() throws IOException {
            delegate.close();
        }

        @Override
        public void connect(SocketAddress remoteAddr) throws IOException {
            delegate.connect(remoteAddr);
        }

        @Override
        public void connect(SocketAddress remoteAddr, int timeout)
                throws IOException {
            delegate.connect(remoteAddr, timeout);
        }

        @Override
        public SocketChannel getChannel() {
            return delegate.getChannel();
        }

        @Override
        public InetAddress getInetAddress() {
            return delegate.getInetAddress();
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return delegate.getInputStream();
        }

        @Override
        public boolean getKeepAlive() throws SocketException {
            return delegate.getKeepAlive();
        }

        @Override
        public InetAddress getLocalAddress() {
            return delegate.getLocalAddress();
        }

        @Override
        public int getLocalPort() {
            return delegate.getLocalPort();
        }

        @Override
        public SocketAddress getLocalSocketAddress() {
            return delegate.getLocalSocketAddress();
        }

        @Override
        public boolean getOOBInline() throws SocketException {
            return delegate.getOOBInline();
        }

        @Override
        public OutputStream getOutputStream() throws IOException {
            return delegate.getOutputStream();
        }

        @Override
        public int getPort() {
            return delegate.getPort();
        }

        @Override
        public synchronized int getReceiveBufferSize() throws SocketException {
            return delegate.getReceiveBufferSize();
        }

        @Override
        public SocketAddress getRemoteSocketAddress() {
            return delegate.getRemoteSocketAddress();
        }

        @Override
        public boolean getReuseAddress() throws SocketException {
            return delegate.getReuseAddress();
        }

        @Override
        public synchronized int getSendBufferSize() throws SocketException {
            return delegate.getSendBufferSize();
        }

        @Override
        public int getSoLinger() throws SocketException {
            return delegate.getSoLinger();
        }

        @Override
        public synchronized int getSoTimeout() throws SocketException {
            return delegate.getSoTimeout();
        }

        @Override
        public boolean getTcpNoDelay() throws SocketException {
            return delegate.getTcpNoDelay();
        }

        @Override
        public int getTrafficClass() throws SocketException {
            return delegate.getTrafficClass();
        }

        @Override
        public boolean isBound() {
            return delegate.isBound();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public boolean isConnected() {
            return delegate.isConnected();
        }

        @Override
        public boolean isInputShutdown() {
            return delegate.isInputShutdown();
        }

        @Override
        public boolean isOutputShutdown() {
            return delegate.isOutputShutdown();
        }

        @Override
        public void sendUrgentData(int value) throws IOException {
            delegate.sendUrgentData(value);
        }

        @Override
        public void setKeepAlive(boolean keepAlive) throws SocketException {
            delegate.setKeepAlive(keepAlive);
        }

        @Override
        public void setOOBInline(boolean oobinline) throws SocketException {
            delegate.setOOBInline(oobinline);
        }

        @Override
        public void setPerformancePreferences(int connectionTime, int latency,
                int bandwidth) {
            delegate.setPerformancePreferences(connectionTime, latency,
                    bandwidth);
        }

        @Override
        public synchronized void setReceiveBufferSize(int size)
                throws SocketException {
            delegate.setReceiveBufferSize(size);
        }

        @Override
        public void setReuseAddress(boolean reuse) throws SocketException {
            delegate.setReuseAddress(reuse);
        }

        @Override
        public synchronized void setSendBufferSize(int size)
                throws SocketException {
            delegate.setSendBufferSize(size);
        }

        @Override
        public void setSoLinger(boolean on, int timeout) throws SocketException {
            delegate.setSoLinger(on, timeout);
        }

        @Override
        public synchronized void setSoTimeout(int timeout)
                throws SocketException {
            delegate.setSoTimeout(timeout);
        }

        @Override
        public void setTcpNoDelay(boolean on) throws SocketException {
            delegate.setTcpNoDelay(on);
        }

        @Override
        public void setTrafficClass(int value) throws SocketException {
            delegate.setTrafficClass(value);
        }

        @Override
        public void shutdownInput() throws IOException {
            delegate.shutdownInput();
        }

        @Override
        public void shutdownOutput() throws IOException {
            delegate.shutdownOutput();
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        @Override
        public boolean equals(Object o) {
            return delegate.equals(o);
        }
    }
}
