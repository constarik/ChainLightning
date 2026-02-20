# Chain Lightning

Original slot game concept with chain mechanics.

## Project Structure

```
ChainLightning/
├── src/chainlightning/
│   ├── Config.java              - Configuration loader
│   ├── ChainLightningGame.java  - Game logic
│   ├── ChainLightningSimulation.java - Monte Carlo simulation
│   └── ChainLightningServer.java     - HTTP server for JS client
├── web/
│   ├── index.html               - Landing page
│   └── play.html                - Game (Local/Server modes)
├── config.json                  - All game parameters
├── pom.xml                      - Maven build
├── run-simulation.bat           - Windows simulation script
├── run-simulation.sh            - Linux simulation script
├── run-server.bat               - Windows server script
└── run-server.sh                - Linux server script
```

## Build

```bash
mvn clean package
```

## Run Simulation

```bash
# Default: 10M rounds, log every 100K
java -cp "target/*:target/dependency/*" chainlightning.ChainLightningSimulation

# Custom parameters
java -cp "target/*:target/dependency/*" chainlightning.ChainLightningSimulation --rounds 100000000 --log 1000

# Or use scripts
./run-simulation.sh --rounds 100000000 --log 1000
```

### Simulation Output Files

- `{random}_chain_lightning_out.txt` - Final statistics
- `{random}_chain_lightning_log.txt` - Progress log (Round, RTP%, HitRate%, WildModeFreq%, Sigma)

## Run Server

```bash
java -cp "target/*:target/dependency/*" chainlightning.ChainLightningServer

# Or use scripts
./run-server.sh
```

Server starts on port 8080 (configurable in config.json).

Open http://localhost:8080 in browser.

## Game Modes (JS Client)

- **Local** - Game logic runs in browser (JS)
- **Server** - Game logic runs on Java server

## API Endpoints

- `GET /api/spin` - Execute spin, returns grid and results
- `GET /api/config` - Get game configuration
- `GET /` - Landing page
- `GET /play` - Game page

## Configuration (config.json)

All game parameters are configurable:

- Grid size
- Bet amount
- Symbol weights
- Wild probability
- Paytable
- Chain multipliers
- Wild mode settings
- Server port

## Game Stats

- RTP: 96.78%
- Volatility: 6.38σ (Medium-High)
- Max Win: 675×
- Wild Mode: 1/62 spins
- Hit Rate: 58%
