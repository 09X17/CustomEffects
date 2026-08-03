package com.customeffects.editor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.customeffects.CustomEffects;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

public class WebEditorServer {

    private final CustomEffects plugin;
    private HttpServer server;
    private final Map<String, Long> activeTokens = new ConcurrentHashMap<>();
    private byte[] cachedHtml;
    private boolean running = false;
    private boolean useHttps = false;
    private int actualPort;

    public WebEditorServer(CustomEffects plugin) {
        this.plugin = plugin;
    }

    public void start(int port, String bindAddress, boolean https) {
        if (running)
            return;

        try {
            cachedHtml = loadEditorHtml();
            if (cachedHtml == null) {
                plugin.getLogger().severe("No se pudo cargar editor/index.html desde los recursos.");
                return;
            }

            if (https) {
                try {
                    startHttps(port, bindAddress);
                    this.useHttps = true;
                } catch (Exception e) {
                    plugin.getLogger().warning("HTTPS fallo: " + e.getMessage());
                    plugin.getLogger().warning("Intentando HTTP como alternativa...");
                    try {
                        startHttp(port, bindAddress);
                        this.useHttps = false;
                    } catch (Exception e2) {
                        plugin.getLogger().severe("HTTP tambien fallo: " + e2.getMessage());
                        return;
                    }
                }
            } else {
                startHttp(port, bindAddress);
                this.useHttps = false;
            }

            server.createContext("/", new EditorHandler());
            server.setExecutor(Executors.newFixedThreadPool(2));
            server.start();
            running = true;
            actualPort = server.getAddress().getPort();
            String proto = useHttps ? "https" : "http";
            plugin.getLogger().info("Editor de rangos (" + proto + ") iniciado en puerto " + actualPort);
        } catch (BindException e) {
            plugin.getLogger().severe(
                    "El puerto " + port + " ya esta en uso. Cambia el puerto en config.yml o libera el puerto.");
        } catch (Exception e) {
            plugin.getLogger().severe("Error al iniciar el editor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startHttp(int port, String bindAddress) throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
    }

    private void startHttps(int port, String bindAddress) throws Exception {
        File keystoreFile = new File(plugin.getDataFolder(), "editor-keystore.jks");
        char[] password = "customeffects".toCharArray();

        if (!keystoreFile.exists()) {
            generateKeystore(keystoreFile, password);
        }

        if (!keystoreFile.exists()) {
            throw new IOException("No se pudo crear el archivo keystore.");
        }

        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystoreFile)) {
            ks.load(fis, password);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(bindAddress, port), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server = httpsServer;
    }

    private void generateKeystore(File keystoreFile, char[] password) throws Exception {
        plugin.getLogger().info("Generando certificado SSL...");

        String javaHome = System.getProperty("java.home");
        String keytoolPath = javaHome + File.separator + "bin" + File.separator + "keytool";

        File keytoolFile = new File(keytoolPath);
        if (!keytoolFile.exists()) {
            keytoolPath = keytoolPath + ".exe";
            keytoolFile = new File(keytoolPath);
        }
        if (!keytoolFile.exists()) {
            throw new IOException("No se encontro keytool en: " + keytoolPath);
        }

        ProcessBuilder pb = new ProcessBuilder(
                keytoolPath, "-genkeypair",
                "-alias", "customeffects",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "365",
                "-keystore", keystoreFile.getAbsolutePath(),
                "-storepass", new String(password),
                "-keypass", new String(password),
                "-dname", "CN=CustomEffects, OU=Server, O=CustomEffects, L=Server, ST=Server, C=MC");
        pb.redirectErrorStream(true);
        Process p = pb.start();

        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = p.waitFor();

        if (exit != 0) {
            throw new IOException("keytool retorno codigo " + exit + ": " + output);
        }

        plugin.getLogger().info("Certificado SSL generado correctamente.");
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            running = false;
            activeTokens.clear();
            plugin.getLogger().info("Servidor del editor detenido.");
        }
    }

    public String generateToken() {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        activeTokens.put(token, System.currentTimeMillis());
        cleanExpiredTokens();
        return token;
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return actualPort;
    }

    public boolean isHttps() {
        return useHttps;
    }

    private boolean isValidToken(String token) {
        if (token == null || token.isEmpty())
            return false;
        Long created = activeTokens.get(token);
        if (created == null)
            return false;
        long elapsed = System.currentTimeMillis() - created;
        if (elapsed > 30 * 60 * 1000L) {
            activeTokens.remove(token);
            return false;
        }
        return true;
    }

    private void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        activeTokens.entrySet().removeIf(entry -> now - entry.getValue() > 30 * 60 * 1000L);
    }

    private byte[] loadEditorHtml() {
        try (InputStream is = plugin.getResource("editor/index.html")) {
            if (is == null)
                return null;
            return is.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private class EditorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String token = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if (kv.length == 2 && "token".equals(kv[0])) {
                        token = kv[1];
                        break;
                    }
                }
            }

            if (!isValidToken(token)) {
                String errorHtml = """
                        <!DOCTYPE html>
                        <html><head><meta charset="UTF-8"><title>Acceso denegado</title>
                        <style>
                        body{background:#0a0a12;color:#e8e8f0;font-family:sans-serif;display:flex;justify-content:center;align-items:center;height:100vh;margin:0}
                        .box{text-align:center;background:#12121f;padding:48px;border-radius:16px;border:1px solid #2a2a4a}
                        h1{font-size:2em;color:#a855f7;margin-bottom:12px}
                        p{color:#8888aa;font-size:14px}
                        code{background:#1a1a30;padding:2px 8px;border-radius:4px;color:#a855f7}
                        </style></head>
                        <body><div class="box"><h1>Acceso denegado</h1>
                        <p>Token invalido o expirado.<br>Usa <code>/rankeditor</code> en el servidor.</p>
                        </div></body></html>
                        """;
                byte[] bytes = errorHtml.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(403, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, cachedHtml.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(cachedHtml);
            }
        }
    }
}
