@echo off
title Khoi dong Du an Thi Trac Nghiem
echo ==============================================
echo KHOI DONG HE THONG THI TRAC NGHIEM MICROSERVICES
echo ==============================================
echo.
echo Dang kiem tra Docker...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [LOI] Docker chua duoc bat. Vui long bat ung dung Docker Desktop len truoc!
    pause
    exit /b
)

echo.
echo Dang khoi dong cac dich vu (Backend, Database, Frontend)...
cd /d "%~dp0"
docker compose up -d

echo.
echo ==============================================
echo HOAN TAT! HE THONG DA DUOC KHOI DONG CHAY NGAM.
echo ==============================================
echo.
echo Cac duong dan truy cap:
echo - Giao dien (Frontend): http://localhost:3000
echo - API Gateway:          http://localhost:8080
echo.
echo De tat he thong sau khi khong su dung nua, chi can go: docker compose down
echo.
pause
