@echo off
setlocal
cd /d "%~dp0"
echo ========================================
echo  KingdomSMP 0.4.6 - Build JAR
 echo Paper 1.21.11 / Java 21 / Maven
 echo ========================================
where java >nul 2>nul || (echo [ERREUR] Java introuvable. Installe Java 21. & pause & exit /b 1)
where mvn >nul 2>nul || (echo [ERREUR] Maven introuvable. Installe Apache Maven 3.9+ puis relance. & pause & exit /b 1)
java -version
mvn -U clean package
if errorlevel 1 (
  echo.
  echo [ECHEC] La compilation a echoue. Copie la sortie Maven pour diagnostiquer.
  pause
  exit /b 1
)
echo.
echo [OK] JAR genere dans target\KingdomSMP-0.4.6.jar
pause
