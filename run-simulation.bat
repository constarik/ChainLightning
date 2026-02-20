@echo off
cd /d "%~dp0"
java -cp "target\*;target\dependency\*" chainlightning.ChainLightningSimulation %*
