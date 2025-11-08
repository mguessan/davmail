@echo off
rcedit64 /C davmail64.exe
rcedit64 /I davmail64.exe davmail.ico
@rem rcedit64 /N davmail64.exe davmail64.ini

rcedit64 /C davmailservice64.exe
rcedit64 /I davmailservice64.exe davmail.ico
@rem rcedit64 /N davmailservice64.exe davmailservice64.ini

ie4uinit.exe -show
