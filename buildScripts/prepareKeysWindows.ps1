# The reason these build scripts are in the Ktor project is
# due to a limitation of TeamCity, in that you cannot reference
# auxiliary build files from the repository where settings are stored
# For more information see https://youtrack.jetbrains.com/issue/TW-68479

md $Env:SIGN_KEY_LOCATION -force
cd $Env:SIGN_KEY_LOCATION


# Hard-coding path for GPG since this fails on TeamCity
# $gpg=(get-command gpg.exe).Path
$gpg="C:\Program Files (x86)\Gpg4win\..\GnuPG\bin\gpg.exe"
Set-Alias -Name gpg2.exe -Value $gpg

echo "Exporting public key"
[System.IO.File]::WriteAllText("$pwd\keyfile", $Env:SIGN_KEY_PUBLIC)
& $gpg --batch --import keyfile
rm keyfile


echo "Exporting private key"

[System.IO.File]::WriteAllText("$pwd\keyfile", $Env:SIGN_KEY_PRIVATE)
& $gpg --allow-secret-key-import --batch --import keyfile
rm keyfile
