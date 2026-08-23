@echo off
REM PostToolUse: compact task-history.md (UTF-8, dedupe, keep 25). Silent.
set "PROJECT_DIR=%CLAUDE_PROJECT_DIR%"
if "%PROJECT_DIR%"=="" set "PROJECT_DIR=%CD%"
set "SCRIPT=%~dp0compact-task-history.py"
if not exist "%PROJECT_DIR%\memory\task-history.md" exit /b 0
where python >nul 2>&1
if errorlevel 1 exit /b 0
python "%SCRIPT%"
exit /b 0
