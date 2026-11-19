@echo off
rem Sao luu CSDL ctv_labor (Form 03 VI.3). Lap lich bang Windows Task Scheduler. Can mysqldump trong PATH.
rem Bien: DB_HOST DB_PORT DB_NAME DB_USER DB_PASS BACKUP_DIR KEEP_DAYS (mac dinh 30), COPY_TO (o ngoai / thu muc dong bo dam may, tuy chon)
setlocal
if "%DB_HOST%"=="" set DB_HOST=localhost
if "%DB_PORT%"=="" set DB_PORT=3306
if "%DB_NAME%"=="" set DB_NAME=ctv_labor
if "%DB_USER%"=="" set DB_USER=root
if "%DB_PASS%"=="" set DB_PASS=root
if "%KEEP_DAYS%"=="" set KEEP_DAYS=30
if "%BACKUP_DIR%"=="" set BACKUP_DIR=%~dp0..\backups
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set TS=%%i
set MYSQL_PWD=%DB_PASS%
mysqldump -h %DB_HOST% -P %DB_PORT% -u %DB_USER% --single-transaction --routines --default-character-set=utf8mb4 --result-file="%BACKUP_DIR%\%DB_NAME%_%TS%.sql" %DB_NAME%
if errorlevel 1 (echo Sao luu that bai & exit /b 1)
if not "%COPY_TO%"=="" (
  if not exist "%COPY_TO%" mkdir "%COPY_TO%"
  copy /y "%BACKUP_DIR%\%DB_NAME%_%TS%.sql" "%COPY_TO%\" >nul
)
forfiles /p "%BACKUP_DIR%" /m %DB_NAME%_*.sql /d -%KEEP_DAYS% /c "cmd /c del @path" >nul 2>&1
echo Da sao luu: %BACKUP_DIR%\%DB_NAME%_%TS%.sql
