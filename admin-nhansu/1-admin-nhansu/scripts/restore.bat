@echo off
rem Phuc hoi CSDL: restore.bat <tep.sql>. Tu sao luu ban hien tai truoc khi ghi de.
setlocal
if "%~1"=="" (echo Dung: restore.bat tep_sao_luu.sql & exit /b 1)
if "%DB_HOST%"=="" set DB_HOST=localhost
if "%DB_PORT%"=="" set DB_PORT=3306
if "%DB_NAME%"=="" set DB_NAME=ctv_labor
if "%DB_USER%"=="" set DB_USER=root
if "%DB_PASS%"=="" set DB_PASS=root
call "%~dp0backup.bat" || exit /b 1
set MYSQL_PWD=%DB_PASS%
mysql -h %DB_HOST% -P %DB_PORT% -u %DB_USER% --default-character-set=utf8mb4 %DB_NAME% < "%~1"
if errorlevel 1 (echo Phuc hoi that bai & exit /b 1)
echo Da phuc hoi tu %~1
