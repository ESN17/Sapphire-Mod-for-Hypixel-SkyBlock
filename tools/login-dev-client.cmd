@echo off
setlocal
cd /d "%~dp0.."
echo Sapphire - Microsoft login for the development client
echo Complete authorization yourself on the Microsoft page shown below.
echo Do not share the device code or the account cache.
echo.
call gradlew.bat microsoftLogin --console=plain
if errorlevel 1 (
    echo.
    echo Login failed. Review the message above, then run this script again.
    exit /b 1
)
echo.
echo Login completed. Starting Minecraft 26.3 with Sapphire...
call gradlew.bat runClient --console=plain
exit /b %errorlevel%
