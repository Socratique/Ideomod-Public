@echo off
REM Generate a file tree of only the src/ directory (the actual mod code)
REM Excludes build artifacts, gradle cache, world saves, etc.
tree /F src > FILETREE.txt
echo FILETREE.txt generated (src/ only)
