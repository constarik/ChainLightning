package chainlightning;

import java.util.*;

public class ChainLightningGame {
    private Random random;
    private int[][] grid;
    private double totalWeight;
    private double[] symbolWeights;
    private Map<Integer, int[]> paytable;
    private int[] multipliers;
    
    public ChainLightningGame() {
        this.random = new Random();
        this.symbolWeights = Config.getSymbolWeights();
        this.totalWeight = 0;
        for (int i = 1; i < symbolWeights.length; i++) {
            totalWeight += symbolWeights[i];
        }
        this.paytable = Config.getPaytable();
        this.multipliers = Config.getMultipliers();
    }
    
    public ChainLightningGame(long seed) {
        this();
        this.random = new Random(seed);
    }
    
    private int pickSymbol() {
        double roll = random.nextDouble() * totalWeight;
        for (int i = 1; i < symbolWeights.length; i++) {
            roll -= symbolWeights[i];
            if (roll <= 0) return i;
        }
        return symbolWeights.length - 1;
    }
    
    public int[][] generateGrid() {
        int rows = Config.getRows();
        int cols = Config.getCols();
        double wildProb = Config.getWildProb();
        
        grid = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = random.nextDouble() < wildProb ? 0 : pickSymbol();
            }
        }
        return grid;
    }
    
    public List<int[]> findWilds() {
        List<int[]> wilds = new ArrayList<>();
        int rows = Config.getRows();
        int cols = Config.getCols();
        
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] == Config.getWildSymbol()) {
                    wilds.add(new int[]{r, c});
                }
            }
        }
        return wilds;
    }
    
    private List<int[]> getNeighbors(int r, int c) {
        List<int[]> neighbors = new ArrayList<>();
        int rows = Config.getRows();
        int cols = Config.getCols();
        
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    neighbors.add(new int[]{nr, nc});
                }
            }
        }
        return neighbors;
    }
    
    public static class Chain {
        public int length;
        public int symbol;
        public List<int[]> path;
        public int[] wildStart;
        
        public Chain() {
            this.length = 0;
            this.symbol = 0;
            this.path = new ArrayList<>();
            this.wildStart = null;
        }
    }
    
    public Chain traceChain(int startR, int startC, Set<String> usedCells) {
        Chain chain = new Chain();
        int symbol = grid[startR][startC];
        
        if (symbol == Config.getWildSymbol()) return chain;
        if (usedCells.contains(startR + "," + startC)) return chain;
        
        Set<String> visited = new HashSet<>();
        List<int[]> path = new ArrayList<>();
        path.add(new int[]{startR, startC});
        visited.add(startR + "," + startC);
        
        int[] current = new int[]{startR, startC};
        
        while (true) {
            List<int[]> neighbors = getNeighbors(current[0], current[1]);
            List<int[]> candidates = new ArrayList<>();
            
            for (int[] n : neighbors) {
                String key = n[0] + "," + n[1];
                int cell = grid[n[0]][n[1]];
                if (!visited.contains(key) && !usedCells.contains(key) && 
                    (cell == symbol || cell == Config.getWildSymbol())) {
                    candidates.add(n);
                }
            }
            
            if (candidates.isEmpty()) break;
            
            int[] next = candidates.get(random.nextInt(candidates.size()));
            visited.add(next[0] + "," + next[1]);
            path.add(next);
            current = next;
        }
        
        chain.length = path.size();
        chain.symbol = symbol;
        chain.path = path;
        return chain;
    }
    
    public Chain traceChainFromWild(int startR, int startC, Set<String> usedCells) {
        Chain bestChain = new Chain();
        
        List<int[]> neighbors = getNeighbors(startR, startC);
        for (int[] n : neighbors) {
            String key = n[0] + "," + n[1];
            if (usedCells.contains(key)) continue;
            int symbol = grid[n[0]][n[1]];
            if (symbol == Config.getWildSymbol()) continue;
            
            Chain chain = traceChain(n[0], n[1], usedCells);
            if (chain.length > bestChain.length) {
                bestChain = chain;
            }
        }
        
        bestChain.wildStart = new int[]{startR, startC};
        return bestChain;
    }
    
    public int getPayout(int symbol, int chainLength) {
        if (chainLength < 3) return 0;
        int[] pays = paytable.get(symbol);
        if (pays == null) return 0;
        int idx = Math.min(chainLength - 3, pays.length - 1);
        return pays[idx];
    }
    
    public int getMultiplier(int chainLength) {
        int idx = Math.min(chainLength - 1, multipliers.length - 1);
        return multipliers[idx];
    }
    
    public static class SpinResult {
        public int[][] grid;
        public long totalWin;
        public List<ChainResult> chains;
        public boolean wildMode;
        public int wildCount;
        public int wildModeMultiplier;
        
        public SpinResult() {
            this.chains = new ArrayList<>();
        }
    }
    
    public static class ChainResult {
        public int symbol;
        public int length;
        public int basePay;
        public int mult;
        public int wildMult;
        public long win;
        public List<int[]> path;
        public int[] wildStart;
    }
    
    public SpinResult spin() {
        SpinResult result = new SpinResult();
        generateGrid();
        result.grid = grid;
        
        Set<String> usedCells = new HashSet<>();
        List<int[]> wilds = findWilds();
        
        int minWilds = Config.getMinWildsForMode();
        int wildModeMult = Config.getWildModeMultiplier();
        
        result.wildMode = wilds.size() >= minWilds;
        result.wildCount = wilds.size();
        result.wildModeMultiplier = result.wildMode ? wildModeMult : 1;
        
        if (result.wildMode) {
            for (int[] wild : wilds) {
                String wildKey = wild[0] + "," + wild[1];
                if (usedCells.contains(wildKey)) continue;
                
                Chain chain = traceChainFromWild(wild[0], wild[1], usedCells);
                
                if (chain.length >= 3 && chain.symbol > 0) {
                    int basePay = getPayout(chain.symbol, chain.length);
                    int mult = getMultiplier(chain.length);
                    long win = (long) basePay * mult * wildModeMult;
                    
                    ChainResult cr = new ChainResult();
                    cr.symbol = chain.symbol;
                    cr.length = chain.length;
                    cr.basePay = basePay;
                    cr.mult = mult;
                    cr.wildMult = wildModeMult;
                    cr.win = win;
                    cr.path = chain.path;
                    cr.wildStart = chain.wildStart;
                    result.chains.add(cr);
                    
                    result.totalWin += win;
                    usedCells.add(wildKey);
                    for (int[] cell : chain.path) {
                        usedCells.add(cell[0] + "," + cell[1]);
                    }
                }
            }
        } else {
            int strikes = Config.getStrikesPerSpin();
            int rows = Config.getRows();
            int cols = Config.getCols();
            
            for (int strike = 0; strike < strikes; strike++) {
                int attempts = 0;
                int startR, startC;
                
                do {
                    startR = random.nextInt(rows);
                    startC = random.nextInt(cols);
                    attempts++;
                } while (usedCells.contains(startR + "," + startC) && attempts < 30);
                
                if (attempts >= 30) continue;
                
                Chain chain = traceChain(startR, startC, usedCells);
                
                if (chain.length >= 3 && chain.symbol > 0) {
                    int basePay = getPayout(chain.symbol, chain.length);
                    int mult = getMultiplier(chain.length);
                    long win = (long) basePay * mult;
                    
                    ChainResult cr = new ChainResult();
                    cr.symbol = chain.symbol;
                    cr.length = chain.length;
                    cr.basePay = basePay;
                    cr.mult = mult;
                    cr.wildMult = 1;
                    cr.win = win;
                    cr.path = chain.path;
                    result.chains.add(cr);
                    
                    result.totalWin += win;
                    for (int[] cell : chain.path) {
                        usedCells.add(cell[0] + "," + cell[1]);
                    }
                }
            }
        }
        
        return result;
    }
}
