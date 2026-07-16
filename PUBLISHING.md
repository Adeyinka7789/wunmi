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

## Cutting a release

1. Set a non-SNAPSHOT version across all modules:
   ```bash
   mvn versions:set -DnewVersion=0.1.0
   ```
2. Deploy (signs, builds sources + javadoc, uploads, and — with `autoPublish=true` — publishes):
   ```bash
   mvn -Prelease deploy -Dgpg.passphrase=YOUR_PASSPHRASE
   ```
3. Tag and bump to the next snapshot:
   ```bash
   git tag v0.1.0 && git push --tags
   mvn versions:set -DnewVersion=0.2.0-SNAPSHOT
   ```

Consumers then depend on it with no extra repository config:

```xml
<dependency>
    <groupId>io.github.adeyinka7789</groupId>
    <artifactId>wunmi-core</artifactId>
    <version>0.1.0</version>
</dependency>
```
