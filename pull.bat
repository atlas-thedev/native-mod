@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Noctra Mod - Git Pull

echo ========================================================
echo               Noctra Mod - Quick Pull
echo ========================================================
echo.

cd /d "%~dp0"
set "GIT=git -c gc.auto=0 -c maintenance.auto=false"

if not exist ".git" (
    echo [ERROR] No .git repository found in %~dp0
    goto :FAIL
)
where git >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Git is not installed or not in PATH.
    goto :FAIL
)
git config --global --add safe.directory "%CD%" >nul 2>&1

for /f "delims=" %%B in ('git branch --show-current') do set "BRANCH=%%B"
if "!BRANCH!"=="" (
    echo [ERROR] You are not on a branch ^(detached HEAD^). Run:  git checkout main
    goto :FAIL
)

if exist ".git\rebase-merge" goto :STUCK
if exist ".git\rebase-apply" goto :STUCK
if exist ".git\MERGE_HEAD" goto :STUCK

echo [1/2] Pulling latest changes into "!BRANCH!" ^(your local edits are kept^)...
%GIT% pull --rebase --autostash origin !BRANCH!
if errorlevel 1 (
    echo.
    echo [ERROR] Pull failed. Common causes:
    echo   - no internet / not logged in to GitHub
    echo   - the same lines were changed here and on GitHub ^(conflict^)
    echo.
    echo Undo the half-finished pull with:  git rebase --abort
    echo Or, if you do NOT need your local changes, make this folder match GitHub:
    echo     git fetch origin ^&^& git reset --hard origin/!BRANCH!
    goto :FAIL
)

echo.
echo [2/2] Now at:
git log -1 --oneline
echo.
echo ========================================================
echo      SUCCESS: Repository updated from GitHub!
echo ========================================================
goto :END

:STUCK
echo [ERROR] A previous pull/merge is still unfinished in this folder.
echo         Run  git rebase --abort  ^(or  git merge --abort^)  and try again.
goto :FAIL

:FAIL
echo.
pause
exit /b 1

:END
echo.
pause
exit /b 0
