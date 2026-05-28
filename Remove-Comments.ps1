# Script to remove comments from Java, XML, properties, and markdown files in context-api project
# Processes files under src/main and src/test directories

$root = "c:\Users\mlcha\Documents\Projetos\Context\context-api"
$directories = @(
    "$root\src\main\java",
    "$root\src\test\java",
    "$root\src\main\resources",
    "$root\src\test\resources"
)

# Process each directory
foreach ($dir in $directories) {
    if (Test-Path $dir) {
        Get-ChildItem -Path $dir -Recurse -File | ForEach-Object {
            $file = $_.FullName
            $ext = $_.Extension.ToLower()
            
            try {
                $content = Get-Content $file -Raw -Encoding UTF8
                
                switch ($ext) {
                    ".java" {
                        # Remove multi-line comments /* ... */
                        $content = $content -replace '/\*.*?\*/', ''
                        # Remove single-line comments //
                        $content = $content -replace '//.*', ''
                    }
                    ".xml" {
                        # Remove XML comments <!-- ... -->
                        $content = $content -replace '<!--.*?-->', ''
                    }
                    ".properties" {
                        # Remove lines that start with # or ! (after optional whitespace)
                        $lines = $content -split "`n"
                        $newLines = $lines | ForEach-Object {
                            if ($_ -match '^\s*[#!]') {
                                # Skip comment lines
                                $null
                            } else {
                                $_
                            }
                        }
                        $content = $newLines -join "`n"
                    }
                    ".md" {
                        # Remove HTML comments <!-- ... -->
                        $content = $content -replace '<!--.*?-->', ''
                    }
                    default {
                        # For other file types, do nothing
                    }
                }
                
                # Only write back if content changed
                if ($content -ne (Get-Content $file -Raw -Encoding UTF8)) {
                    Set-Content -Path $file -Value $content -Encoding UTF8
                    Write-Host "Processed: $file"
                }
            } catch {
                Write-Warning "Error processing $file"
            }
        }
    }
}

Write-Host "Comment removal completed."