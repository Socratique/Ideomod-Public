@echo off
REM Generates a file tree of the project directory
REM Run this from your project root folder

echo Generating file tree...
tree /F /A > FILETREE.txt
echo File tree saved to FILETREE.txt
pause
