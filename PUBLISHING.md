# Publishing wunmi to Maven Central

Releases go to the **Sonatype Central Portal** (central.sonatype.com) under the
`io.github.adeyinka7789` namespace. The build config lives in the `release` Maven profile.

## One-time setup

1. **Verify the namespace.** In the Central Portal, confirm `io.github.adeyinka7789` is a verified
   namespace (linked to your GitHub — done).
2. **Publishing token** (Central Portal → *Account* → *Generate User Token*), in `~/.m2/settings.xml`:
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
   Treat the token as a secret: if it ever leaks (e.g. pasted into a chat/log), regenerate it and
   update this file's server entry.

## Signing key

Central requires GPG-signed artifacts. The active signing key is **passphrase-less**, so signing
needs no prompt, no `gpg-agent` warm-up, and no passphrase plumbing:

```
fingerprint  4F390E4EF870D278BBC2AB804EA5B7105D147C0C
uid          Adeyinka <Dotunm85@gmail.com>
```

Its public half is on `keyserver.ubuntu.com` and `keys.openpgp.org`. Confirm it's retrievable
(Central validates against the keyservers) before a release:

```bash
curl -s "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x4F390E4EF870D278BBC2AB804EA5B7105D147C0C" | head -1
# => -----BEGIN PGP PUBLIC KEY BLOCK-----
```

> **History / gotcha.** An earlier key (`861828064CD96347`) was passphrase-protected, and the
> passphrase was lost. Worse, `maven-gpg-plugin` 3.2.4 can't reliably pass a passphrase on Windows
> — it appends a newline (`\r\n`), so gpg reads `secret\r` and fails with *Bad passphrase*, whether
> the passphrase comes from `-Dgpg.passphrase`, `<gpg.passphrase>`, or `MAVEN_GPG_PASSPHRASE`. The
> passphrase-less key sidesteps all of that. If you ever add a passphrase back (`gpg --passwd`),
> you must unlock the agent before deploying — see this file's git history for that dance.

### Regenerating the key (only if lost/expired)

```bash
cat > key.conf <<'EOF'
%no-protection
Key-Type: EDDSA
Key-Curve: ed25519
Subkey-Type: ECDH
Subkey-Curve: cv25519
Name-Real: Adeyinka
Name-Email: Dotunm85@gmail.com
Expire-Date: 3y
%commit
EOF
gpg --batch --gen-key key.conf && rm key.conf
gpg --keyserver keyserver.ubuntu.com --send-keys <NEW_FINGERPRINT>
gpg --keyserver keys.openpgp.org   --send-keys <NEW_FINGERPRINT>
```

Then update the fingerprint in this file and in the deploy command below.

## Cutting a release

1. Set a non-SNAPSHOT version across all modules (edit the `<version>` in each `pom.xml`, or):
   ```bash
   mvn versions:set -DnewVersion=0.4.0
   ```
2. Build once to confirm green, then deploy. `-Dgpg.keyname` is **required** — it forces signing
   with the passphrase-less key; without it the plugin may pick a different default secret key.
   `deploy` signs, builds sources + javadoc, uploads the bundle, and (with `autoPublish=true`)
   publishes automatically:
   ```bash
   mvn -B -Prelease deploy -DskipTests \
       -Dgpg.keyname=4F390E4EF870D278BBC2AB804EA5B7105D147C0C
   ```
   The `examples/spring-boot-demo` module is a sample, not a library; it's kept out of the Central
   bundle by `<excludeArtifacts>` on the central-publishing plugin (in the `release` profile).
   `maven.deploy.skip` alone does **not** exclude it — the plugin stages from its own directory.
3. Tag and bump to the next snapshot:
   ```bash
   git tag v0.4.0 && git push --tags
   mvn versions:set -DnewVersion=0.5.0-SNAPSHOT   # then commit + push
   ```

### Watching the deployment

`deploy` prints a `deploymentId`. Its state goes `VALIDATING → VALIDATED → PUBLISHING → PUBLISHED`
automatically (a deployment can only be manually *dropped* while `VALIDATED`, not once publishing):

```bash
curl -s -X POST "https://central.sonatype.com/api/v1/publisher/status?id=<DEPLOYMENT_ID>" \
     -H "Authorization: Bearer $(printf '%s:%s' USER PASS | base64)"
```

Artifacts appear in Central search within ~15 min to a few hours; they're resolvable as soon as
they're indexed.

Consumers then depend on wunmi with no extra repository config:

```xml
<dependency>
    <groupId>io.github.adeyinka7789</groupId>
    <artifactId>wunmi-core</artifactId>
    <version>0.3.0</version>
</dependency>
```

## Released versions

| Version | Notes |
|---------|-------|
| 0.3.0 | Metrics SPI, SpEL `@RequiresFlag`, admin role gate, example app. First release signed with the passphrase-less key `4F39…147C0C`. |
| 0.2.0 | Cross-instance cache invalidation. |
| 0.1.0 | Initial extraction from `hr.admtechub.com`. |
