# CodeBook 构建环境变量（本会话专用，不污染系统 PATH）
# 用法：在项目根目录执行  . .\tools\env.ps1
$env:CODEBOOK_ROOT = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$env:JAVA_HOME = Join-Path $env:CODEBOOK_ROOT '.tools\jdk'
$env:ANDROID_HOME = Join-Path $env:CODEBOOK_ROOT '.tools\sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $env:CODEBOOK_ROOT '.tools\android-user'
$env:GRADLE_USER_HOME = Join-Path $env:CODEBOOK_ROOT '.tools\gradle-home'
$env:GRADLE_HOME = Join-Path $env:CODEBOOK_ROOT '.tools\gradle'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:GRADLE_HOME\bin;$env:PATH"
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
