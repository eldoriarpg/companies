[![Wakatime_Badge](https://wakatime.com/badge/github/eldoriarpg/companies.svg)][wakatime]

[![Publish](https://img.shields.io/github/actions/workflow/status/eldoriarpg/companies/publish_to_nexus.yml?style=for-the-badge&label=Publish)][publish]
[![Build](https://img.shields.io/github/actions/workflow/status/eldoriarpg/companies/verify.yml?style=for-the-badge&label=Build)][verify]

[![Releases](https://img.shields.io/nexus/maven-releases/de.eldoria/companies-api?label=Release&logo=Release&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)][release]
[![Snapshots](https://img.shields.io/nexus/s/de.eldoria/companies-api?color=orange&label=Snapshot&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)][snapshot]

# Companies

Players place orders for items. Companies of players claim and deliver them.

Companies is a Paper plugin that turns gathering and crafting into a player-run economy. Players order items and pay
up front. Companies claim the orders, their members deliver the items, and every worker is paid for what they brought.
Orders stay anonymous. Companies level up, keep a treasury and compete in rankings.

**Documentation:** https://docs.eldoria.de/companies

Installation, commands, permissions, configuration, the API and its events are all described there.

# Dependency

The API is published to the EldoNexus repository as `de.eldoria:companies-api`. Companies brings it at runtime, so add
it as `compileOnly` (Gradle) or `provided` (Maven) and do not shade it. The API is compiled with Java 25, so your
plugin needs Java 25 or newer to compile against it.

**Gradle**
```kotlin
repositories {
    maven("https://eldonexus.de/repository/maven-public/")
}

dependencies {
    compileOnly("de.eldoria", "companies-api", "2.0.0")
}
```

**Maven**
```xml
<repository>
    <id>EldoNexus</id>
    <url>https://eldonexus.de/repository/maven-public/</url>
</repository>

<dependency>
    <groupId>de.eldoria</groupId>
    <artifactId>companies-api</artifactId>
    <version>2.0.0</version>
    <scope>provided</scope>
</dependency>
```

See the [API documentation](https://docs.eldoria.de/companies/developers/api) for how to use it.

[wakatime]: https://wakatime.com/badge/github/eldoriarpg/companies
[publish]: https://github.com/eldoriarpg/companies/actions/workflows/publish_to_nexus.yml
[verify]: https://github.com/eldoriarpg/companies/actions/workflows/verify.yml
[release]: https://eldonexus.de/#browse/browse:maven-releases:de%2Feldoria%2Fcompanies-api
[snapshot]: https://eldonexus.de/#browse/browse:maven-snapshots:de%2Feldoria%2Fcompanies-api
