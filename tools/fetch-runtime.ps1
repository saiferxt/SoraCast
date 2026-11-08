$ErrorActionPreference='Stop'
$revision='3e835c8c3cdf3e60aae1cc0de7d4d5e96003f26a'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../android/runtime/src/main/jniLibs'))
$hashes=@{
 'armeabi-v7a'='08fc563e06d308687bf207aee609dd3d8c663af67980bdb4bde0491642b47931'
 'arm64-v8a'='57784faf0d9fed384673b799f870f44818cec590c557883fd3abe696ab59847c'
 'x86'='9fd7ec63b8a1d62a940aaa931536c77cd86d7c9b6d353c70fe3c4c638759b3f9'
 'x86_64'='d5120ecc7f314a1573db61caf02a53e0dbae0666ad9cd6585c743d25758432fc'
}
foreach($abi in $hashes.Keys){
 $dir=Join-Path $root $abi
 New-Item -ItemType Directory -Path $dir -Force | Out-Null
 $file=Join-Path $dir 'libjingle_peerconnection_so.so'
 if(!(Test-Path -LiteralPath $file)){
  $url="https://raw.githubusercontent.com/dkrivoruchko/ScreenStream/$revision/webrtc-runtime/src/main/jniLibs/$abi/libjingle_peerconnection_so.so"
  try{Invoke-WebRequest -Uri $url -OutFile "$file.part" -TimeoutSec 180
   if((Get-FileHash -LiteralPath "$file.part" -Algorithm SHA256).Hash.ToLower() -ne $hashes[$abi]){throw "Runtime checksum mismatch: $abi"}
   Move-Item -LiteralPath "$file.part" -Destination $file
  }finally{if(Test-Path -LiteralPath "$file.part"){Remove-Item -LiteralPath "$file.part"}}
 }
 if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower() -ne $hashes[$abi]){throw "Runtime checksum mismatch: $abi"}
 Write-Output "$abi verified"
}
