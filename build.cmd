@echo off
setlocal DisableDelayedExpansion
rem Compatibility entry: one build/download/packaging implementation for all callers.
call "%~dp0new-build.cmd" %*
exit /b %errorlevel%
