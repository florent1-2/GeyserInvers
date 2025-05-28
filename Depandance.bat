@echo off
javac -version >nul 2>&1
if errorlevel 1 (
    echo Erreur : javac n'est pas reconnu.
    echo Assure-toi que le JDK est installe et que le dossier \bin est ajoute a la variable PATH.
    echo Voici un lien pour telecharger le JDK :
    echo https://adoptium.net/
    pause
    exit /b 1
) else (
    echo javac est installe et fonctionnel.
)
pause