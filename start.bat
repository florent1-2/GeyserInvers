@echo off
REM Compile les fichiers Java (ProxyMain.java et ProxyBridge.java)
echo Compilation des fichiers Java...
javac ProxyMain.java ProxyBridge.java
if errorlevel 1 (
    echo Erreur lors de la compilation. Verifie que javac est dans ton PATH.
    pause
    exit /b 1
)

REM Lancer le proxy
echo Demarrage du proxy Java -> Bedrock...
java ProxyMain

pause