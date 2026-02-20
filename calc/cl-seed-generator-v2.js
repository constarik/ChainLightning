#!/usr/bin/env node
/**
 * Chain Lightning v1.13 - Balanced Seed Generator
 * Usage: node cl-seed-generator-v2.js <count> <dfs|bfs> [output.json]
 * 
 * Балансирует RTP к 96.5% на лету
 */

const fs = require('fs');

// ============================================================
// Java-compatible Random (LCG)
// ============================================================

class JavaRandom {
  constructor(seed) {
    this.multiplier = 0x5DEECE66Dn;
    this.addend = 0xBn;
    this.mask = (1n << 48n) - 1n;
    this.seed = (BigInt(seed) ^ this.multiplier) & this.mask;
  }
  
  next(bits) {
    this.seed = (this.seed * this.multiplier + this.addend) & this.mask;
    return Number(this.seed >> (48n - BigInt(bits)));
  }
  
  nextInt(bound) {
    if (bound <= 0) throw new Error("bound must be positive");
    if ((bound & (bound - 1)) === 0) {
      return (bound * this.next(31)) >> 31;
    }
    let bits, val;
    do {
      bits = this.next(31);
      val = bits % bound;
    } while (bits - val + (bound - 1) < 0);
    return val;
  }
  
  nextDouble() {
    return ((this.next(26) * (1 << 27)) + this.next(27)) / (1 << 53);
  }
}

// ============================================================
// Game Config
// ============================================================

const CONFIG = {
  grid: { rows: 5, cols: 6 },
  symbols: {
    weights: [0, 7.0, 9.0, 11.0, 15.0, 17.0, 19.0, 22.0, 24.0, 26.0],
  },
  lightning: {
    strikesPerSpin: 3,
    wildProb: 0.0179,
    multipliers: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
  },
  wildMode: {
    minWilds: 3,
    multiplier: 5
  },
  paytable: {
    1: [15, 40, 100, 200, 600, 1500],
    2: [12, 30, 80, 150, 400, 1000],
    3: [10, 25, 60, 120, 300, 750],
    4: [8, 20, 50, 100, 250, 600],
    5: [6, 15, 40, 80, 200, 500],
    6: [5, 12, 30, 60, 150, 400],
    7: [4, 10, 25, 50, 120, 300],
    8: [3, 8, 20, 40, 100, 250],
    9: [2, 6, 15, 30, 80, 200]
  }
};

const BET = 100;
const TARGET_RTP = 96.5;

// ============================================================
// Game Logic
// ============================================================

let weightedPool = [];
let gameRng = null;
let grid = [];

function initWeights() {
  weightedPool = [];
  for (let sym = 1; sym < CONFIG.symbols.weights.length; sym++) {
    const weight = CONFIG.symbols.weights[sym];
    for (let i = 0; i < Math.round(weight * 10); i++) {
      weightedPool.push(sym);
    }
  }
}

function randomSymbol() {
  if (gameRng.nextDouble() < CONFIG.lightning.wildProb) return 0;
  return weightedPool[gameRng.nextInt(weightedPool.length)];
}

function generateGrid(seed) {
  gameRng = new JavaRandom(seed);
  grid = [];
  for (let r = 0; r < CONFIG.grid.rows; r++) {
    const row = [];
    for (let c = 0; c < CONFIG.grid.cols; c++) {
      row.push(randomSymbol());
    }
    grid.push(row);
  }
}

function getNeighbors(r, c) {
  const dirs = [[-1,-1], [-1,0], [-1,1], [0,-1], [0,1], [1,-1], [1,0], [1,1]];
  const neighbors = [];
  for (const [dr, dc] of dirs) {
    const nr = r + dr, nc = c + dc;
    if (nr >= 0 && nr < CONFIG.grid.rows && nc >= 0 && nc < CONFIG.grid.cols) {
      neighbors.push([nr, nc]);
    }
  }
  return neighbors;
}

// BFS - находит всё облако
function traceChainBFS(startR, startC, usedCells) {
  const startSymbol = grid[startR][startC];
  const targetSymbol = startSymbol === 0 ? -1 : startSymbol;
  
  const path = [[startR, startC]];
  const visited = new Set([`${startR},${startC}`]);
  let queue = [[startR, startC]];
  let chainSymbol = targetSymbol;
  
  while (queue.length > 0) {
    const [r, c] = queue.shift();
    const neighbors = getNeighbors(r, c);
    
    for (const [nr, nc] of neighbors) {
      const key = `${nr},${nc}`;
      if (visited.has(key) || usedCells.has(key)) continue;
      
      const neighborSym = grid[nr][nc];
      
      if (neighborSym === 0) {
        visited.add(key);
        path.push([nr, nc]);
        queue.push([nr, nc]);
      } else if (chainSymbol === -1) {
        chainSymbol = neighborSym;
        visited.add(key);
        path.push([nr, nc]);
        queue.push([nr, nc]);
      } else if (neighborSym === chainSymbol) {
        visited.add(key);
        path.push([nr, nc]);
        queue.push([nr, nc]);
      }
    }
  }
  
  return { length: path.length, symbol: chainSymbol === -1 ? 0 : chainSymbol, path };
}

// DFS - находит самый длинный путь
function traceChainDFS(startR, startC, usedCells) {
  const startSymbol = grid[startR][startC];
  const targetSymbol = startSymbol === 0 ? -1 : startSymbol;
  
  let chainSymbol = targetSymbol;
  
  function dfs(r, c, visited, currentSymbol) {
    const path = [[r, c]];
    const neighbors = getNeighbors(r, c);
    
    let longestExtension = [];
    for (const [nr, nc] of neighbors) {
      const key = `${nr},${nc}`;
      if (visited.has(key) || usedCells.has(key)) continue;
      
      const neighborSym = grid[nr][nc];
      let newSymbol = currentSymbol;
      
      if (neighborSym === 0) {
        // Wild
      } else if (currentSymbol === -1) {
        newSymbol = neighborSym;
      } else if (neighborSym !== currentSymbol) {
        continue;
      }
      
      visited.add(key);
      const extension = dfs(nr, nc, visited, newSymbol);
      visited.delete(key);
      
      if (extension.path.length > longestExtension.length) {
        longestExtension = extension.path;
        if (currentSymbol === -1 && extension.symbol !== -1) {
          newSymbol = extension.symbol;
        }
        chainSymbol = newSymbol;
      }
    }
    
    return { path: path.concat(longestExtension), symbol: chainSymbol };
  }
  
  const visited = new Set([`${startR},${startC}`]);
  const result = dfs(startR, startC, visited, targetSymbol);
  
  return { length: result.path.length, symbol: result.symbol === -1 ? 0 : result.symbol, path: result.path };
}

function findWilds() {
  const wilds = [];
  for (let r = 0; r < CONFIG.grid.rows; r++) {
    for (let c = 0; c < CONFIG.grid.cols; c++) {
      if (grid[r][c] === 0) wilds.push([r, c]);
    }
  }
  return wilds;
}

function traceChainFromWild(startR, startC, usedCells, traceChain) {
  const neighbors = getNeighbors(startR, startC);
  let bestChain = { length: 0, symbol: 0, path: [] };
  
  for (const [nr, nc] of neighbors) {
    if (usedCells.has(`${nr},${nc}`)) continue;
    const symbol = grid[nr][nc];
    if (symbol === 0) continue;
    
    const chain = traceChain(nr, nc, usedCells);
    if (chain.length > bestChain.length) {
      bestChain = chain;
    }
  }
  
  return { ...bestChain, wildStart: [startR, startC] };
}

function getPayout(symbol, chainLength) {
  if (chainLength < 3) return 0;
  const idx = Math.min(chainLength - 3, 5);
  const multiplier = CONFIG.paytable[symbol][idx];
  return Math.floor(BET * multiplier / 100);
}

function getMultiplier(chainLength) {
  const idx = Math.min(chainLength - 1, CONFIG.lightning.multipliers.length - 1);
  return CONFIG.lightning.multipliers[idx];
}

function calculateRound(seed, traceChain) {
  generateGrid(seed);
  
  let totalWin = 0;
  const usedCells = new Set();
  
  const wilds = findWilds();
  const isWildMode = wilds.length >= CONFIG.wildMode.minWilds;
  const wildMultiplier = isWildMode ? CONFIG.wildMode.multiplier : 1;
  
  if (isWildMode) {
    for (const [wr, wc] of wilds) {
      if (usedCells.has(`${wr},${wc}`)) continue;
      const chain = traceChainFromWild(wr, wc, usedCells, traceChain);
      if (chain.length >= 3 && chain.symbol > 0) {
        const basePay = getPayout(chain.symbol, chain.length);
        const mult = getMultiplier(chain.length);
        const win = basePay * mult * wildMultiplier;
        totalWin += win;
        usedCells.add(`${wr},${wc}`);
        chain.path.forEach(([r, c]) => usedCells.add(`${r},${c}`));
      }
    }
  } else {
    for (let strike = 0; strike < CONFIG.lightning.strikesPerSpin; strike++) {
      let attempts = 0;
      let startR, startC;
      do {
        startR = gameRng.nextInt(CONFIG.grid.rows);
        startC = gameRng.nextInt(CONFIG.grid.cols);
        attempts++;
      } while (usedCells.has(`${startR},${startC}`) && attempts < 30);
      if (attempts >= 30) continue;
      
      const chain = traceChain(startR, startC, usedCells);
      if (chain.length >= 3 && chain.symbol > 0) {
        const basePay = getPayout(chain.symbol, chain.length);
        const mult = getMultiplier(chain.length);
        const win = basePay * mult;
        totalWin += win;
        chain.path.forEach(([r, c]) => usedCells.add(`${r},${c}`));
      }
    }
  }
  
  return totalWin;
}

// ============================================================
// Main
// ============================================================

const count = parseInt(process.argv[2]) || 10000;
const mode = (process.argv[3] || 'bfs').toLowerCase();
const outputFile = process.argv[4] || 'cl-seeds-balanced.json';

if (mode !== 'bfs' && mode !== 'dfs') {
  console.error('Usage: node cl-seed-generator-v2.js <count> <dfs|bfs> [output.json]');
  process.exit(1);
}

const traceChain = mode === 'bfs' ? traceChainBFS : traceChainDFS;

console.log(`Mode: ${mode.toUpperCase()}, Target: ${count} seeds, RTP: ${TARGET_RTP}%`);

initWeights();
const results = [];
const baseSeed = Math.floor(Date.now() / 1000);
const winCounts = {};
const maxWinPct = 0.01; // 1% max frequency

let totalWin = 0;
let i = 0;
let lastPercent = -1;

while (results.length < count) {
  const seed = baseSeed + i;
  const win = calculateRound(seed, traceChain);
  i++;
  
  // Проверяем лимит частоты для этого win
  const currentWinCount = winCounts[win] || 0;
  const maxWinCount = Math.floor(count * maxWinPct);
  if (currentWinCount >= maxWinCount) {
    if (i % 10000 === 0) process.stdout.write(`\rSkipping win=${win} (limit reached), i=${i}`);
    continue;
  }
  
  const currentRTP = results.length > 0 ? (totalWin / (results.length * BET) * 100) : 0;
  
  // Балансировка: берём спин если он двигает RTP к цели
  let take = false;
  if (results.length === 0) {
    take = true;
  } else if (currentRTP < TARGET_RTP && win > currentRTP) {
    // RTP низкий - берём выше текущего
    take = true;
  } else if (currentRTP > TARGET_RTP && win < currentRTP) {
    // RTP высокий - берём ниже текущего
    take = true;
  } else if (Math.abs(currentRTP - TARGET_RTP) < 1) {
    // Близко к цели - берём всё что в диапазоне ±50% от target
    take = (win >= TARGET_RTP - 50 && win <= TARGET_RTP + 50);
  }
  
  if (take) {
    results.push({ seed, win });
    totalWin += win;
    winCounts[win] = currentWinCount + 1;
    
    const percent = Math.floor(results.length / count * 100);
    if (percent !== lastPercent && percent % 5 === 0) {
      const rtp = (totalWin / (results.length * BET) * 100).toFixed(2);
      process.stdout.write(`\r${percent}% (RTP: ${rtp}%, seeds: ${i})      `);
      lastPercent = percent;
    }
  } else {
    if (i % 10000 === 0) process.stdout.write(`\rRTP balance skip: win=${win}, RTP=${currentRTP.toFixed(1)}%, i=${i}`);
  }
}

const finalRTP = (totalWin / (results.length * BET) * 100).toFixed(2);
process.stdout.write(`\r100% done                                      \n`);
console.log(`Seeds tested: ${i}`);
console.log(`Final RTP: ${finalRTP}%`);

fs.writeFileSync(outputFile, JSON.stringify(results));
console.log(`Saved ${results.length} seeds to ${outputFile}`);
