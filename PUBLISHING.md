# Publishing wunmi to Maven Central

Releases go to the **Sonatype Central Portal** (central.sonatype.com) under the
`io.github.adeyinka7789` namespace. The build config lives in the `release` Maven profile.

## One-time setup

1. **Verify the namespace.** In the Central Portal, confirm `io.github.adeyinka7789` is a verified
   namespace (linked to your GitHub — you've done this).
2. **Generate a publishing token** (Central Portal → *Account* → *Generate User Token*) and add it
   to `~/.m2/settings.xml`:
   ```xml
   <settings>
     <servers>
       <server>
         <id>central</id>
         <username>YOUR_TOKEN_USERNAME</username>
         <password>YOUR_TOKEN_PASSWORD</password>
       </server>
     </servers>
   </settings>
   ```
3. **GPG key** — Central requires signed artifacts. Create one and publish it to a keyserver:
   ```bash
   gpg --gen-key
   gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
   ```
   Do **not** put `<gpg.passphrase>` in `settings.xml`. It does not work (see below), and it stores
   a secret on disk for no benefit — signing goes through gpg-agent instead.

## Cutting a release

1. Set a non-SNAPSHOT version across all modules:
   ```bash
   mvn versions:set -DnewVersion=0.2.0
   ```
2. **Unlock the GPG key first**, then deploy (signs, builds sources + javadoc, uploads, and —
   with `autoPublish=true` — publishes):
   ```bash
   echo x > .sigwarm && gpg --batch --yes --pinentry-mode loopback --detach-sign .sigwarm && rm .sigwarm*
   mvn -Prelease deploy
   ```

   > **Why the first line.** `maven-gpg-plugin` 3.2.4 does *not* successfully pass a passphrase to
   > gpg on Windows — it appends a newline, which becomes `\r\n`, so gpg reads the passphrase as
   > `secret\r` and fails with `gpg: signing failed: Bad passphrase`. This is true whether the
   > passphrase comes from `-Dgpg.passphrase`, `<gpg.passphrase>` in `settings.xml`, or
   > `MAVEN_GPG_PASSPHRASE` — all three are broken here, which is why the plugin itself warns
   > "rely on gpg-agent".
   >
   > Signing therefore only works when **gpg-agent already holds the unlocked key**. The command
   > above prompts once and caches it, so the deploy's signatures succeed. If you skip it, the
   > release fails at the *first* module (nothing is uploaded, so it's safe to retry).
   >
   > A release that "worked without this step" only did so because the agent was still warm from an
   > earlier `gpg` command — don't rely on that.
3. Tag and bump to the next snapshot:
   ```bash
   git tag v0.2.0 && git push --tags
   mvn versions:set -DnewVersion=0.3.0-SNAPSHOT
   ```

Consumers then depend on it with no extra repository config:

```xml
<dependency>
    <groupId>io.github.adeyinka7789</groupId>
    <artifactId>wunmi-core</artifactId>
    <version>0.2.0</version>
</dependency>
```
