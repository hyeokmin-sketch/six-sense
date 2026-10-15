# Windows 내장 한국어 TTS(Microsoft Heami)로 16kHz 모노 wav 일괄 생성.
# 입력 목록: UTF-8, 한 줄에 "key<TAB>rate(-10~10)<TAB>문장"
# 실행: powershell -ExecutionPolicy Bypass -File stt\sapi_tts.ps1 -List list.tsv -OutDir wav\sapi
param([string]$List, [string]$OutDir)
Add-Type -AssemblyName System.Speech
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$s.SelectVoice("Microsoft Heami Desktop")
$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
New-Item -ItemType Directory -Force $OutDir | Out-Null
$n = 0
foreach ($line in [IO.File]::ReadAllLines($List, [Text.Encoding]::UTF8)) {
    $key, $rate, $text = $line -split "`t", 3
    $path = Join-Path $OutDir "$key.wav"
    if (Test-Path $path) { continue }
    $s.Rate = [int]$rate
    $s.SetOutputToWaveFile($path, $fmt)
    $s.Speak($text)
    $n++
}
$s.SetOutputToNull()
Write-Output "sapi $n files"
