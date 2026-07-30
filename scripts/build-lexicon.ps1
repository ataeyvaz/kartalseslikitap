# Turkce sozcuk dagarcigini iki kaynaktan uretir:
#  - tdd-ai/hunspell-tr kok listesi (MPL-2.0)  -> "bu gercek bir kelime" OTORITESI
#  - hermitdave/FrequencyWords tr_50k (MIT)    -> "bu kelime ne kadar yaygin" bilgisi
#
# NOT: Bu dosyada bilerek ASCII disi karakter YOK. PowerShell 5.1 .ps1 dosyalarini
# ANSI okudugu icin kaynaktaki Turkce harfler bozulur ve sessizce yanlis sonuc uretir.
# Turkce harfler asagida kod noktasiyla tanimlaniyor.
#
# ONEMLI TEMIZLIK ADIMI: altyazi derlemi Turkce klavyesiz yazilmis bicimler icerir
# ("cocuk", "kisi", "degil"). Bunlar sozlukte kalirsa duzeltici onlari gecerli kelime
# sayar ve isaret geri yukleme calismaz. Kok listesinde OLMAYAN ve isaretli karsiligi
# cok daha yaygin olan ASCII bicimler bu yuzden atilir.
$ErrorActionPreference = "Stop"
$scratch = "C:\Users\Ata\AppData\Local\Temp\claude\C--Users-Ata-Desktop-KartalSesliKitap\a27c1cf6-068c-4f6f-ab53-da02c82d6471\scratchpad"
$outDir = "C:\Users\Ata\Desktop\KartalSesliKitap\app\src\main\assets\dictionaries"
New-Item -ItemType Directory -Force $outDir | Out-Null

$tr = [System.Globalization.CultureInfo]::GetCultureInfo("tr-TR")

$C_CEDILLA = [char]0x00E7; $G_BREVE = [char]0x011F; $DOTLESS_I = [char]0x0131
$O_DIAERESIS = [char]0x00F6; $S_CEDILLA = [char]0x015F; $U_DIAERESIS = [char]0x00FC
$A_CIRCUM = [char]0x00E2; $I_CIRCUM = [char]0x00EE; $U_CIRCUM = [char]0x00FB

$allowedChars = New-Object 'System.Collections.Generic.HashSet[char]'
foreach ($c in [char[]]"abcdefghijklmnopqrstuvwxyz") { [void]$allowedChars.Add($c) }
foreach ($c in @($C_CEDILLA,$G_BREVE,$DOTLESS_I,$O_DIAERESIS,$S_CEDILLA,$U_DIAERESIS,$A_CIRCUM,$I_CIRCUM,$U_CIRCUM)) {
  [void]$allowedChars.Add($c)
}

$foldMap = @{}
$foldMap[$C_CEDILLA]='c'; $foldMap[$G_BREVE]='g'; $foldMap[$DOTLESS_I]='i'
$foldMap[$O_DIAERESIS]='o'; $foldMap[$S_CEDILLA]='s'; $foldMap[$U_DIAERESIS]='u'
$foldMap[$A_CIRCUM]='a'; $foldMap[$I_CIRCUM]='i'; $foldMap[$U_CIRCUM]='u'

function Test-Word([string]$w) {
  if ($w.Length -lt 2) { return $false }
  foreach ($ch in $w.ToCharArray()) { if (-not $allowedChars.Contains($ch)) { return $false } }
  return $true
}

function Get-Folded([string]$w) {
  $sb = New-Object System.Text.StringBuilder
  foreach ($ch in $w.ToCharArray()) {
    if ($foldMap.ContainsKey($ch)) { [void]$sb.Append($foldMap[$ch]) } else { [void]$sb.Append($ch) }
  }
  return $sb.ToString()
}

$freqPath = Join-Path $scratch "tr_50k.txt"
$dicPath = Join-Path $scratch "tr_TR.dic"
if (-not (Test-Path $freqPath)) {
  Invoke-WebRequest "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/tr/tr_50k.txt" -OutFile $freqPath -UseBasicParsing
}
if (-not (Test-Path $dicPath)) {
  Invoke-WebRequest "https://raw.githubusercontent.com/LibreOffice/dictionaries/master/tr_TR/tr_TR.dic" -OutFile $dicPath -UseBasicParsing
}

# 1) Siklik listesi
$freq = New-Object 'System.Collections.Generic.Dictionary[string,long]'
foreach ($line in [System.IO.File]::ReadLines($freqPath, [System.Text.Encoding]::UTF8)) {
  $parts = $line.Split(' ')
  if ($parts.Count -lt 2) { continue }
  $w = $parts[0].ToLower($tr)
  if (-not (Test-Word $w)) { continue }
  $c = 0L
  if ([long]::TryParse($parts[1], [ref]$c)) {
    if ($freq.ContainsKey($w)) { $freq[$w] = $freq[$w] + $c } else { $freq[$w] = $c }
  }
}
Write-Output "siklik listesi: $($freq.Count)"

# 2) Hunspell kokleri (otorite)
$roots = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($line in [System.IO.File]::ReadLines($dicPath, [System.Text.Encoding]::UTF8)) {
  $w = $line.Split('/')[0].Trim().ToLower($tr)
  if (Test-Word $w) { [void]$roots.Add($w) }
}
Write-Output "kok listesi: $($roots.Count)"

# 3) Katlanmis bicim indeksi: "kisi" -> { kisi, kişi }
$byFolded = @{}
foreach ($w in $freq.Keys) {
  $f = Get-Folded $w
  if (-not $byFolded.ContainsKey($f)) { $byFolded[$f] = New-Object 'System.Collections.Generic.List[string]' }
  [void]$byFolded[$f].Add($w)
}

# 4) Temizlik: kokte olmayan, isaretli karsiligi >=20 kat yaygin ASCII bicimleri at
$dropped = New-Object 'System.Collections.Generic.List[string]'
foreach ($w in $freq.Keys) {
  if ($roots.Contains($w)) { continue }
  if ((Get-Folded $w) -ne $w) { continue }   # zaten isaretli bir kelime
  $rivals = $byFolded[$w]
  if ($rivals -eq $null) { continue }
  foreach ($rival in $rivals) {
    if ($rival -eq $w) { continue }
    if ($freq[$rival] -ge (20 * [math]::Max($freq[$w], 1))) { [void]$dropped.Add($w); break }
  }
}
foreach ($w in $dropped) { [void]$freq.Remove($w) }
Write-Output "atilan ASCII bicim: $($dropped.Count)"

# 5) Birlestir
$lexicon = New-Object 'System.Collections.Generic.Dictionary[string,long]'
foreach ($kv in $freq.GetEnumerator()) { $lexicon[$kv.Key] = $kv.Value }
$rootOnly = 0
foreach ($w in $roots) { if (-not $lexicon.ContainsKey($w)) { $lexicon[$w] = 0L; $rootOnly++ } }
Write-Output "yalnizca kokten gelen: $rootOnly"
Write-Output "toplam: $($lexicon.Count)"

$sb = New-Object System.Text.StringBuilder
foreach ($k in ($lexicon.Keys | Sort-Object)) {
  [void]$sb.Append($k).Append("`t").Append($lexicon[$k]).Append("`n")
}
$outFile = Join-Path $outDir "tr_lexicon.tsv"
[System.IO.File]::WriteAllText($outFile, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Output "yazildi: $outFile ($([math]::Round((Get-Item $outFile).Length/1MB,2)) MB)"
