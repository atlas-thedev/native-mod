@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Native Mod - Git Push

echo ========================================================
echo               Native Mod - Quick Push
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

echo [1/4] Checking repository status on "!BRANCH!"...
git status --short
echo.

set "HAS_CHANGES="
for /f "delims=" %%i in ('git status --porcelain') do set "HAS_CHANGES=1"

if defined HAS_CHANGES (
    set "commit_msg="
    set /p "commit_msg=Commit message (Enter = update: sync changes): "
    if "!commit_msg!"=="" set "commit_msg=update: sync changes"
    echo.
    echo [2/4] Committing...
    git add -A
    git commit -m "!commit_msg!"
    if errorlevel 1 (
        echo [ERROR] Commit failed.
        goto :FAIL
    )
) else (
    echo [2/4] Nothing new to commit.
)

echo.
echo [3/4] Getting the latest from GitHub first...
%GIT% pull --rebase --autostash origin !BRANCH!
if errorlevel 1 (
    echo.
    echo [ERROR] Could not combine your commits with GitHub's. Nothing was pushed.
    echo         Undo with:  git rebase --abort   then ask for help, or run pull.bat.
    goto :FAIL
)

echo.
echo [4/4] Pushing to GitHub...
%GIT% push -u origin !BRANCH!
if errorlevel 1 (
    echo.
    echo [ERROR] Push failed. Check your internet connection and that you are logged in to GitHub.
    goto :FAIL
)

echo.
echo ========================================================
echo      SUCCESS: All changes pushed to GitHub!
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
