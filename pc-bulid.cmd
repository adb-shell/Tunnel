@echo off
rem Compatibility entry used by existing Windows build servers.
call "%~dp0new-build.cmd" %*
exit /b %errorlevel%
