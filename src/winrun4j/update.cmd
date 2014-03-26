@echo off
rcedit /C davmail.exe
rcedit /I davmail.exe davmail.ico
rcedit /N davmail.exe davmail.ini

rcedit /C davmailservice.exe
rcedit /I davmailservice.exe davmail.ico
rcedit /N davmailservice.exe davmailservice.ini

rcedit64 /C davmail64.exe
rcedit64 /I davmail64.exe davmail.ico
rcedit64 /N davmail64.exe davmail64.ini

rcedit64 /C davmailservice64.exe
rcedit64 /I davmailservice64.exe davmail.ico
rcedit64 /N davmailservice64.exe davmailservice64.ini
