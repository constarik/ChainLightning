package chainlightning;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ChainLightningSimulation {
    
    public static void main(String[] args) {
        long rounds = 10_000_000;
        long logInterval = 100_000;
        String configPath = "config.json";
        
        // Parse args
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--rounds") && i + 1 < args.length) {
                rounds = Long.parseLong(args[++i]);
            } else if (args[i].equals("--log") && i + 1 < args.length) {
                logInterval = Long.parseLong(args[++i]);
            } else if (args[i].equals("--config") && i + 1 < args.length) {
                configPath = args[++i];
            }
        }
        
        try {
            Config.load(configPath);
        } catch (IOException e) {
            System.err.println("Error loading config: " + e.getMessage());
            return;
        }
        
        // Random file prefix
        long filePrefix = System.currentTimeMillis() % 100000000;
        String outFile = filePrefix + "_chain_lightning_out.txt";
        String logFile = filePrefix + "_chain_lightning_log.txt";
        
        System.out.println("Chain Lightning Simulation");
        System.out.println("==========================");
        System.out.println("Rounds: " + String.format("%,d", rounds));
        System.out.println("Log interval: " + String.format("%,d", logInterval));
        System.out.println("Output: " + outFile);
        System.out.println("Log: " + logFile);
        System.out.println();
        
        ChainLightningGame game = new ChainLightningGame();
        int bet = Config.getBet();
        
        // Stats
        long totalWagered = 0;
        long totalWon = 0;
        long hits = 0;
        long wildModeCount = 0;
        long wildModeWon = 0;
        long baseWon = 0;
        long maxWin = 0;
        double sumSq = 0;
        
        // Chain length distribution
        Map<Integer, Long> chainLengths = new HashMap<>();
        // Symbol wins
        Map<Integer, long[]> symbolStats = new HashMap<>(); // [count, totalWin]
        // Wilds per trigger
        Map<Integer, Long> wildsDistribution = new HashMap<>();
        
        long startTime = System.currentTimeMillis();
        
        try (PrintWriter logWriter = new PrintWriter(new FileWriter(logFile))) {
            logWriter.println("Round,RTP%,HitRate%,WildModeFreq%,Sigma");
            
            for (long i = 1; i <= rounds; i++) {
                totalWagered += bet;
                
                ChainLightningGame.SpinResult result = game.spin();
                long win = result.totalWin;
                
                totalWon += win;
                sumSq += (double) win * win;
                if (win > 0) hits++;
                if (win > maxWin) maxWin = win;
                
                if (result.wildMode) {
                    wildModeCount++;
                    wildModeWon += win;
                    wildsDistribution.merge(result.wildCount, 1L, Long::sum);
                } else {
                    baseWon += win;
                }
                
                // Chain stats
                for (ChainLightningGame.ChainResult chain : result.chains) {
                    chainLengths.merge(chain.length, 1L, Long::sum);
                    
                    long[] stats = symbolStats.computeIfAbsent(chain.symbol, k -> new long[2]);
                    stats[0]++;
                    stats[1] += chain.win;
                }
                
                // Log
                if (i % logInterval == 0) {
                    double rtp = (double) totalWon / totalWagered * 100;
                    double hitRate = (double) hits / i * 100;
                    double wildFreq = (double) wildModeCount / i * 100;
                    double meanWin = (double) totalWon / i;
                    double variance = sumSq / i - meanWin * meanWin;
                    double sigma = Math.sqrt(variance) / bet;
                    
                    logWriter.printf("%d,%.4f,%.4f,%.4f,%.4f%n", i, rtp, hitRate, wildFreq, sigma);
                    logWriter.flush();
                    
                    System.out.printf("\rProgress: %,d / %,d (%.1f%%) - RTP: %.2f%%", 
                        i, rounds, (double) i / rounds * 100, rtp);
                }
            }
            
        } catch (IOException e) {
            System.err.println("Error writing log: " + e.getMessage());
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        // Final stats
        double rtp = (double) totalWon / totalWagered * 100;
        double hitRate = (double) hits / rounds * 100;
        double baseRtp = (double) baseWon / totalWagered * 100;
        double wildRtp = (double) wildModeWon / totalWagered * 100;
        double wildFreq = (double) wildModeCount / rounds * 100;
        double meanWin = (double) totalWon / rounds;
        double variance = sumSq / rounds - meanWin * meanWin;
        double sigma = Math.sqrt(variance) / bet;
        
        StringBuilder output = new StringBuilder();
        output.append("\n==================================================\n");
        output.append("       CHAIN LIGHTNING - SIMULATION RESULTS\n");
        output.append("==================================================\n\n");
        
        output.append("--- GENERAL ---\n");
        output.append(String.format("Spins: %,d%n", rounds));
        output.append(String.format("RTP: %.2f%%%n", rtp));
        output.append(String.format("Hit Rate: %.2f%%%n", hitRate));
        output.append(String.format("Sigma: %.2f (%.2fx bet)%n", Math.sqrt(variance), sigma));
        output.append(String.format("Max Win: %,d (%dx bet)%n", maxWin, maxWin / bet));
        
        output.append("\n--- RTP BREAKDOWN ---\n");
        output.append(String.format("Base Mode: %.2f%%%n", baseRtp));
        output.append(String.format("Wild Mode: %.2f%%%n", wildRtp));
        
        output.append("\n--- WILD MODE (3+ Wilds) ---\n");
        output.append(String.format("Frequency: %.3f%% (1/%d)%n", wildFreq, wildFreq > 0 ? Math.round(100 / wildFreq) : 0));
        output.append("Wilds distribution:\n");
        wildsDistribution.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                double pct = (double) e.getValue() / wildModeCount * 100;
                output.append(String.format("  %d wilds: %,d (%.1f%%)%n", e.getKey(), e.getValue(), pct));
            });
        
        output.append("\n--- CHAIN LENGTHS ---\n");
        long totalChains = chainLengths.values().stream().mapToLong(Long::longValue).sum();
        chainLengths.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                double pct = (double) e.getValue() / totalChains * 100;
                output.append(String.format("  Length %d: %,d (%.1f%%)%n", e.getKey(), e.getValue(), pct));
            });
        
        output.append("\n--- SYMBOL WINS ---\n");
        String[] names = Config.getSymbolNames();
        symbolStats.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> {
                String name = e.getKey() < names.length ? names[e.getKey()] : "Symbol" + e.getKey();
                long count = e.getValue()[0];
                long total = e.getValue()[1];
                double rtpContrib = (double) total / totalWagered * 100;
                output.append(String.format("  %d. %-12s: %,d chains, RTP %.2f%%%n", 
                    e.getKey(), name, count, rtpContrib));
            });
        
        output.append("\n--- PERFORMANCE ---\n");
        output.append(String.format("Time: %.1fs%n", elapsed / 1000.0));
        output.append(String.format("Speed: %,d spins/sec%n", rounds * 1000 / elapsed));
        output.append("==================================================\n");
        
        // Write to file
        try (PrintWriter writer = new PrintWriter(new FileWriter(outFile))) {
            writer.print(output);
        } catch (IOException e) {
            System.err.println("Error writing output: " + e.getMessage());
        }
        
        // Print to console
        System.out.println(output);
    }
}
