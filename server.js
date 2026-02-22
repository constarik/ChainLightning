const express = require('express');
const path = require('path');
const fs = require('fs');
const app = express();

// Load config
const CONFIG_RAW = JSON.parse(fs.readFileSync(path.join(__dirname, 'config.json'), 'utf8'));
const CONFIG = {
  grid: CONFIG_RAW.grid,
  bet: CONFIG_RAW.bet,
  symbols: {
    weights: CONFIG_RAW.symbols.weights,
    names: CONFIG_RAW.symbols.names,
    emojis: ['⚡', '🌩️', '🦅', '🐉', '🥁', '🎭', '🏮', '🌪️', '🌊', '☁️']
  },
  lightning: {
    strikesPerSpin: CONFIG_RAW.lightning.strikesPerSpin,
    wildProb: CONFIG_RAW.wildProb,
    multipliers: CONFIG_RAW.lightning.multipliers
  },
  wildMode: CONFIG_RAW.wildMode,
  paytable: CONFIG_RAW.paytable
};

// --- Engine ---
const totalWeight = CONFIG.symbols.weights.slice(1).reduce((a, b) => a + b, 0);

function pickSymbol() {
  let roll = Math.random() * totalWeight;
  for (let i = 1; i < CONFIG.symbols.weights.length; i++) {
    roll -= CONFIG.symbols.weights[i];
    if (roll <= 0) return i;
  }
  return CONFIG.symbols.weights.length - 1;
}

function generateGrid() {
  const grid = [];
  for (let r = 0; r < CONFIG.grid.rows; r++) {
    const row = [];
    for (let c = 0; c < CONFIG.grid.cols; c++) {
      row.push(Math.random() < CONFIG.lightning.wildProb ? 0 : pickSymbol());
    }
    grid.push(row);
  }
  return grid;
}

function getNeighbors(r, c) {
  const neighbors = [];
  for (let dr = -1; dr <= 1; dr++) {
    for (let dc = -1; dc <= 1; dc++) {
      if (dr === 0 && dc === 0) continue;
      const nr = r + dr, nc = c + dc;
      if (nr >= 0 && nr < CONFIG.grid.rows && nc >= 0 && nc < CONFIG.grid.cols)
        neighbors.push([nr, nc]);
    }
  }
  return neighbors;
}

function traceChain(grid, startR, startC, usedCells) {
  const symbol = grid[startR][startC];
  if (symbol === 0) return { length: 0, symbol: 0, path: [] };
  if (usedCells.has(`${startR},${startC}`)) return { length: 0, symbol: 0, path: [] };

  const visited = new Set();
  const path = [[startR, startC]];
  visited.add(`${startR},${startC}`);
  let current = [startR, startC];

  while (true) {
    const candidates = getNeighbors(current[0], current[1]).filter(([nr, nc]) => {
      const key = `${nr},${nc}`;
      const cell = grid[nr][nc];
      return !visited.has(key) && !usedCells.has(key) && (cell === symbol || cell === 0);
    });
    if (candidates.length === 0) break;
    const next = candidates[Math.floor(Math.random() * candidates.length)];
    visited.add(`${next[0]},${next[1]}`);
    path.push(next);
    current = next;
  }
  return { length: path.length, symbol, path };
}

function findWilds(grid) {
  const wilds = [];
  for (let r = 0; r < CONFIG.grid.rows; r++)
    for (let c = 0; c < CONFIG.grid.cols; c++)
      if (grid[r][c] === 0) wilds.push([r, c]);
  return wilds;
}

function traceChainFromWild(grid, startR, startC, usedCells) {
  let best = { length: 0, symbol: 0, path: [] };
  for (const [nr, nc] of getNeighbors(startR, startC)) {
    if (usedCells.has(`${nr},${nc}`)) continue;
    const sym = grid[nr][nc];
    if (sym === 0) continue;
    const chain = traceChain(grid, nr, nc, usedCells);
    if (chain.length > best.length) best = chain;
  }
  return { ...best, wildStart: [startR, startC] };
}

function getPayout(symbol, chainLength) {
  if (chainLength < 3) return 0;
  const idx = Math.min(chainLength - 3, 5);
  return (CONFIG.paytable[symbol] || [])[idx] || 0;
}

function getMultiplier(chainLength) {
  const idx = Math.min(chainLength - 1, CONFIG.lightning.multipliers.length - 1);
  return CONFIG.lightning.multipliers[idx];
}

function doSpin() {
  const grid = generateGrid();
  const usedCells = new Set();
  const chains = [];
  let totalWin = 0;

  const wilds = findWilds(grid);
  const isWildMode = wilds.length >= CONFIG.wildMode.minWilds;
  const wildMultiplier = isWildMode ? CONFIG.wildMode.multiplier : 1;

  if (isWildMode) {
    for (const [wr, wc] of wilds) {
      if (usedCells.has(`${wr},${wc}`)) continue;
      const chain = traceChainFromWild(grid, wr, wc, usedCells);
      if (chain.length >= 3 && chain.symbol > 0) {
        const basePay = getPayout(chain.symbol, chain.length);
        const mult = getMultiplier(chain.length);
        const win = basePay * mult * wildMultiplier;
        totalWin += win;
        chains.push({ ...chain, win, mult, basePay, wildMultiplier });
        usedCells.add(`${wr},${wc}`);
        chain.path.forEach(([r, c]) => usedCells.add(`${r},${c}`));
      }
    }
  } else {
    for (let strike = 0; strike < CONFIG.lightning.strikesPerSpin; strike++) {
      let startR, startC, attempts = 0;
      do {
        startR = Math.floor(Math.random() * CONFIG.grid.rows);
        startC = Math.floor(Math.random() * CONFIG.grid.cols);
        attempts++;
      } while (usedCells.has(`${startR},${startC}`) && attempts < 30);
      if (attempts >= 30) continue;

      const chain = traceChain(grid, startR, startC, usedCells);
      if (chain.length >= 3 && chain.symbol > 0) {
        const basePay = getPayout(chain.symbol, chain.length);
        const mult = getMultiplier(chain.length);
        const win = basePay * mult;
        totalWin += win;
        chains.push({ ...chain, win, mult, basePay, wildMultiplier: 1 });
        chain.path.forEach(([r, c]) => usedCells.add(`${r},${c}`));
      }
    }
  }

  return { grid, chains, totalWin, isWildMode, wildMultiplier, wilds };
}

// --- Routes ---
app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  next();
});
app.use(express.static(path.join(__dirname, 'web')));

app.get('/api/config', (req, res) => {
  res.json({
    rows: CONFIG.grid.rows,
    cols: CONFIG.grid.cols,
    bet: CONFIG.bet,
    symbolNames: CONFIG.symbols.names,
    symbolWeights: CONFIG.symbols.weights,
    wildProb: CONFIG.lightning.wildProb,
    strikesPerSpin: CONFIG.lightning.strikesPerSpin,
    multipliers: CONFIG.lightning.multipliers,
    minWildsForMode: CONFIG.wildMode.minWilds,
    wildModeMultiplier: CONFIG.wildMode.multiplier,
    paytable: CONFIG.paytable
  });
});

app.get('/api/spin', (req, res) => {
  const result = doSpin();
  res.json(result);
});

app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'web', 'index.html')));
app.get('/play', (req, res) => res.sendFile(path.join(__dirname, 'web', 'play.html')));

const PORT = CONFIG_RAW.server?.port || 8080;
app.listen(PORT, () => console.log(`Chain Lightning JS server running on http://localhost:${PORT}`));
