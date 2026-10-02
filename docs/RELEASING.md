# GitHub release procedure

1. Freeze features; update versionName and strictly increase versionCode.
2. Install the pinned scanner SDK; run CI quality and Android emulator jobs on the exact release commit. Review failures, do not silently waive checks.
3. Build release with empty development credentials. Sign locally with the permanent release key using scripts/package-release-apk.ps1. Private keys/passwords are never committed or uploaded to Actions.
4. Verify APK signature, certificate fingerprint, package/version and 16-KiB ZIP/native alignment. Attach the signed APK and SHA256SUMS to a GitHub prerelease tag pointing at the validated commit.
5. Include release notes, known limitations, notices, and migration warnings. Retain the same signing identity for future updates.

## Maintainer key custody (Windows)

The signing directory defaults to `%USERPROFILE%/.android/opencloud-next-release`, outside the repository. `release.p12` holds the password-encrypted private key. `password.clixml` holds the password protected by Windows DPAPI for the current user/machine. The public certificate may be published; private key and password must not be published.

Back up the keystore to secure offline storage and save its password in a password manager. A copied DPAPI file alone is **not** portable disaster recovery. Use PowerShell Import-Clixml under the creating Windows user when transferring the password into your chosen secret manager; never paste it into an issue, chat or build log. Losing the key/password prevents signing compatible updates. No remote backup is configured by this repository.

## Test-build migration

Old test APKs were signed with an Android debug certificate. The dedicated release certificate cannot update them in place. Export local-only files/drafts and complete transfers before uninstalling; uninstall removes local app data. This release does not automatically migrate the debug signature or erase the old installation.
