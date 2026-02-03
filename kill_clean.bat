REM ========================================
REM  Gradle 项目清理脚本
REM  功能：停止 Gradle 进程并清理所有构建产物
REM ========================================

@echo off
REM 关闭命令回显

cd /d "%~dp0"
REM 切换到脚本所在目录

echo ========================================
echo   Stop Gradle and Clean Build Files
echo ========================================

echo.
echo [1/4] Stopping Gradle Daemon...
REM 停止 Gradle 守护进程，释放内存资源
call gradlew.bat --stop 2>nul

echo.
echo [2/4] Killing Java processes...
REM 强制终止所有 Java 相关进程
REM /F: 强制终止进程  /IM: 指定进程名称
taskkill /F /IM java.exe 2>nul
taskkill /F /IM javaw.exe 2>nul

echo.
echo [3/4] Deleting all build directories...
REM 递归搜索并删除所有 build 目录
REM /d: 只匹配目录  /r: 递归搜索
for /d /r . %%d in (build) do @(
    if exist "%%d" (
        echo Deleting: %%d
        REM /s: 删除目录及其所有内容  /q: 静默模式，不提示确认
        rd /s /q "%%d" 2>nul
    )
)

echo.
echo [4/4] Deleting .gradle cache...
REM 删除 Gradle 本地缓存目录
if exist ".gradle" (
    echo Deleting: .gradle
    rd /s /q ".gradle" 2>nul
)

echo.
echo All clean tasks completed!
echo ========================================
