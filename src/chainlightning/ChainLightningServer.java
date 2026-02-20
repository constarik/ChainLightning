package chainlightning;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ChainLightningServer {
    private static ChainLightningGame game;
    private static Gson gson = new GsonBuilder().create();
    
    public static void main(String[] args) {
        String configPath = "config.json";
        
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--config") && i + 1 < args.length) {
                configPath = args[++i];
            }
        }
        
        try {
            Config.load(configPath);
        } catch (IOException e) {
            System.err.println("Error loading config: " + e.getMessage());
            return;
        }
        
        game = new ChainLightningGame();
        int port = Config.getServerPort();
        
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            
            server.createContext("/api/spin", new SpinHandler());
            server.createContext("/api/config", new ConfigHandler());
            server.createContext("/", new StaticHandler());
            
            server.setExecutor(null);
            server.start();
            
            System.out.println("Chain Lightning Server started on port " + port);
            System.out.println("Open http://localhost:" + port + " in browser");
            
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }
    
    static class SpinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            
            ChainLightningGame.SpinResult result = game.spin();
            
            Map<String, Object> response = new HashMap<>();
            response.put("grid", result.grid);
            response.put("totalWin", result.totalWin);
            response.put("wildMode", result.wildMode);
            response.put("wildCount", result.wildCount);
            response.put("wildModeMultiplier", result.wildModeMultiplier);
            
            List<Map<String, Object>> chains = new ArrayList<>();
            String[] names = Config.getSymbolNames();
            for (ChainLightningGame.ChainResult chain : result.chains) {
                Map<String, Object> c = new HashMap<>();
                c.put("symbol", chain.symbol);
                c.put("symbolName", chain.symbol < names.length ? names[chain.symbol] : "Symbol" + chain.symbol);
                c.put("length", chain.length);
                c.put("basePay", chain.basePay);
                c.put("mult", chain.mult);
                c.put("wildMult", chain.wildMult);
                c.put("win", chain.win);
                c.put("path", chain.path);
                if (chain.wildStart != null) {
                    c.put("wildStart", chain.wildStart);
                }
                chains.add(c);
            }
            response.put("chains", chains);
            
            String json = gson.toJson(response);
            byte[] bytes = json.getBytes("UTF-8");
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
    
    static class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            
            Map<String, Object> config = new HashMap<>();
            config.put("rows", Config.getRows());
            config.put("cols", Config.getCols());
            config.put("bet", Config.getBet());
            config.put("symbolNames", Config.getSymbolNames());
            config.put("symbolWeights", Config.getSymbolWeights());
            config.put("wildProb", Config.getWildProb());
            config.put("wildSymbol", Config.getWildSymbol());
            config.put("strikesPerSpin", Config.getStrikesPerSpin());
            config.put("multipliers", Config.getMultipliers());
            config.put("minWildsForMode", Config.getMinWildsForMode());
            config.put("wildModeMultiplier", Config.getWildModeMultiplier());
            config.put("paytable", Config.getPaytable());
            
            String json = gson.toJson(config);
            byte[] bytes = json.getBytes("UTF-8");
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
    
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            if (path.equals("/play")) path = "/play.html";
            
            String filePath = "web" + path;
            File file = new File(filePath);
            
            if (!file.exists()) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }
            
            String contentType = "text/html";
            if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".json")) contentType = "application/json";
            
            byte[] bytes = Files.readAllBytes(file.toPath());
            
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
