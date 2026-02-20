package chainlightning;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Config {
    private static JsonObject config;
    
    public static void load(String path) throws IOException {
        Gson gson = new Gson();
        config = gson.fromJson(new FileReader(path), JsonObject.class);
    }
    
    public static int getRows() {
        return config.getAsJsonObject("grid").get("rows").getAsInt();
    }
    
    public static int getCols() {
        return config.getAsJsonObject("grid").get("cols").getAsInt();
    }
    
    public static int getBet() {
        return config.get("bet").getAsInt();
    }
    
    public static String[] getSymbolNames() {
        JsonArray arr = config.getAsJsonObject("symbols").getAsJsonArray("names");
        String[] names = new String[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            names[i] = arr.get(i).getAsString();
        }
        return names;
    }
    
    public static double[] getSymbolWeights() {
        JsonArray arr = config.getAsJsonObject("symbols").getAsJsonArray("weights");
        double[] weights = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            weights[i] = arr.get(i).getAsDouble();
        }
        return weights;
    }
    
    public static double getWildProb() {
        return config.get("wildProb").getAsDouble();
    }
    
    public static int getWildSymbol() {
        return config.get("wildSymbol").getAsInt();
    }
    
    public static int getStrikesPerSpin() {
        return config.getAsJsonObject("lightning").get("strikesPerSpin").getAsInt();
    }
    
    public static int[] getMultipliers() {
        JsonArray arr = config.getAsJsonObject("lightning").getAsJsonArray("multipliers");
        int[] mults = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            mults[i] = arr.get(i).getAsInt();
        }
        return mults;
    }
    
    public static int getMinWildsForMode() {
        return config.getAsJsonObject("wildMode").get("minWilds").getAsInt();
    }
    
    public static int getWildModeMultiplier() {
        return config.getAsJsonObject("wildMode").get("multiplier").getAsInt();
    }
    
    public static Map<Integer, int[]> getPaytable() {
        Map<Integer, int[]> paytable = new HashMap<>();
        JsonObject pt = config.getAsJsonObject("paytable");
        for (String key : pt.keySet()) {
            JsonArray arr = pt.getAsJsonArray(key);
            int[] pays = new int[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                pays[i] = arr.get(i).getAsInt();
            }
            paytable.put(Integer.parseInt(key), pays);
        }
        return paytable;
    }
    
    public static int getServerPort() {
        return config.getAsJsonObject("server").get("port").getAsInt();
    }
    
    public static String toJson() {
        return config.toString();
    }
}
