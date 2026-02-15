@echo off
REM Generate a file tree of only the src/ directory
REM Uses PowerShell to avoid Windows tree command encoding issues
powershell -NoProfile -Command ^
  "function Get-Tree($Path, $Prefix='') {" ^
  "  $items = Get-ChildItem $Path | Sort-Object { !$_.PSIsContainer }, Name;" ^
  "  for ($i=0; $i -lt $items.Count; $i++) {" ^
  "    $item = $items[$i];" ^
  "    $isLast = ($i -eq $items.Count-1);" ^
  "    $connector = if ($isLast) {'  '} else {'| '};" ^
  "    $branch = if ($isLast) {'\\--'} else {'+--'};" ^
  "    Write-Output ('{0}{1} {2}' -f $Prefix, $branch, $item.Name);" ^
  "    if ($item.PSIsContainer) { Get-Tree $item.FullName ($Prefix + $connector + ' ') }" ^
  "  }" ^
  "}" ^
  "Write-Output 'src'; Get-Tree 'src'" ^
  " | Set-Content -Encoding UTF8 'FILETREE.txt'"
echo FILETREE.txt generated (src/ only)
```