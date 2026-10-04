@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title Native Client Mod - Release

REM ------------------------------------------------------------------
REM  One click release:
REM    bump version -> commit -> tag -> push
REM  GitHub Actions then builds the jar, boots real Minecraft servers
REM  (1.16.5 ... 26.3) to test it, and publishes the GitHub Release.
REM  The Native launcher picks the new jar up on its own.
REM
REM  Usage:  release.bat            (asks what to bump)
REM          release.bat patch      (patch ^| minor ^| major ^| none)
REM ------------------------------------------------------------------

set "REPO_URL=https://github.com/atlas-thedev/native-mod"
set "BUMP_PS=powershell -NoProfile -ExecutionPolicy Bypass -File scripts\bump-version.ps1"

echo ========================================================
echo     Native Client Mod - Build and Publish Release
echo ========================================================
echo.

cd /d "%~dp0"

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

echo [1/6] Checking branch...
for /f "delims=" %%B in ('git branch --show-current') do set "BRANCH=%%B"
if not "!BRANCH!"=="main" (
    echo [ERROR] You are on "!BRANCH!". Releases must be made from main.
    echo         Run:  git checkout main
    goto :FAIL
)

echo [2/6] Syncing with GitHub...
git fetch origin --tags --force
if errorlevel 1 (
    echo [ERROR] Fetch failed. Check your internet or GitHub login.
    goto :FAIL
)
for /f %%C in ('git rev-list --count HEAD..origin/main') do set "BEHIND=%%C"
if not "!BEHIND!"=="0" (
    echo [ERROR] origin/main has !BEHIND! commit^(s^) you do not have yet.
    echo         Run pull.bat first, then run this again.
    goto :FAIL
)

echo.
echo [3/6] Choose version bump
set "BUMP=%~1"
if defined BUMP goto :CHECK_BUMP

for /f %%V in ('%BUMP_PS% none -DryRun') do set "CUR=%%V"
for /f %%V in ('%BUMP_PS% patch -DryRun') do set "V_PATCH=%%V"
for /f %%V in ('%BUMP_PS% minor -DryRun') do set "V_MINOR=%%V"
for /f %%V in ('%BUMP_PS% major -DryRun') do set "V_MAJOR=%%V"
echo   Current version: !CUR!
echo   [1] patch  -^> !V_PATCH!   (bug fixes)         [default]
echo   [2] minor  -^> !V_MINOR!   (new features)
echo   [3] major  -^> !V_MAJOR!   (big changes)
echo   [4] none   -^> !CUR!   (release the current version)
set "CHOICE="
set /p "CHOICE=Select 1-4 (Enter = 1): "
if "!CHOICE!"=="" set "CHOICE=1"
set "BUMP=patch"
if "!CHOICE!"=="2" set "BUMP=minor"
if "!CHOICE!"=="3" set "BUMP=major"
if "!CHOICE!"=="4" set "BUMP=none"

:CHECK_BUMP
if /i "!BUMP!"=="patch" goto :BUMP_OK
if /i "!BUMP!"=="minor" goto :BUMP_OK
if /i "!BUMP!"=="major" goto :BUMP_OK
if /i "!BUMP!"=="none" goto :BUMP_OK
echo [ERROR] Unknown bump "!BUMP!". Use patch, minor, major or none.
goto :FAIL

:BUMP_OK
for /f %%V in ('%BUMP_PS% none -DryRun') do set "CUR=%%V"
for /f %%V in ('%BUMP_PS% !BUMP! -DryRun') do set "NEWVER=%%V"
if "!NEWVER!"=="" (
    echo [ERROR] Could not work out the next version.
    goto :FAIL
)
set "TAG=v!NEWVER!"

git rev-parse -q --verify "refs/tags/!TAG!" >nul 2>&1
if not errorlevel 1 (
    echo [ERROR] Tag !TAG! already exists locally. Pick a different bump.
    goto :FAIL
)
git ls-remote --exit-code --tags origin "refs/tags/!TAG!" >nul 2>&1
if not errorlevel 1 (
    echo [ERROR] Tag !TAG! already exists on GitHub. Pick a different bump.
    goto :FAIL
)

echo.
echo [4/6] Ready to release
echo   Version : !CUR!  -^>  !NEWVER!
echo   Tag     : !TAG!
echo   Tests   : unit tests + real servers from 1.16.5 to 26.3
echo.
echo Uncommitted changes that will be included:
git status --short
echo.
set "GO="
set /p "GO=Start the release? (y/N): "
if /i not "!GO!"=="y" (
    echo.
    echo Cancelled. Nothing was changed.
    goto :END
)

echo.
echo [5/6] Bumping version and committing...
if /i not "!BUMP!"=="none" (
    %BUMP_PS% !BUMP! >nul
    if errorlevel 1 (
        echo [ERROR] Version bump failed.
        goto :FAIL
    )
)

set "HAS_CHANGES="
for /f "delims=" %%i in ('git status --porcelain') do set "HAS_CHANGES=1"

if defined HAS_CHANGES (
    set "MSG="
    set /p "MSG=Commit message (Enter = release: !TAG!): "
    if "!MSG!"=="" set "MSG=release: !TAG!"
    git add -A
    git commit -m "!MSG!"
    if errorlevel 1 (
        echo [ERROR] Commit failed.
        goto :FAIL
    )
) else (
    echo [INFO] Nothing to commit, tagging the current commit.
)

git tag -a "!TAG!" -m "Native Client Mod !TAG!"
if errorlevel 1 (
    echo [ERROR] Could not create tag !TAG!.
    goto :FAIL
)

echo.
echo [6/6] Pushing to GitHub...
git push --atomic origin HEAD:main "refs/tags/!TAG!"
if errorlevel 1 (
    echo.
    echo [ERROR] Push failed. Removing the local tag so you can retry.
    git tag -d "!TAG!" >nul 2>&1
    echo Possible causes: not logged in to GitHub, branch protection, or new commits on origin.
    goto :FAIL
)

echo.
echo ========================================================
echo   SUCCESS: !TAG! pushed. GitHub is building it now.
echo ========================================================
echo.
echo   Progress : %REPO_URL%/actions
echo   Release  : %REPO_URL%/releases
echo.
echo About 5-10 minutes. When every test is green the release goes public
echo and the Native launcher starts using it automatically.
echo.
start "" "%REPO_URL%/actions"
goto :END

:FAIL
echo.
pause
exit /b 1

:END
echo.
pause
exit /b 0
