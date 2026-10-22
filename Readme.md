[![Wakatime_Badge](https://wakatime.com/badge/github/eldoriarpg/companies.svg)][wakatime]

[![Publish](https://img.shields.io/github/actions/workflow/status/eldoriarpg/companies/publish_to_nexus.yml?style=for-the-badge&label=Publish)][publish]
[![Build](https://img.shields.io/github/actions/workflow/status/eldoriarpg/companies/verify.yml?style=for-the-badge&label=Build)][verify]

[![Releases](https://img.shields.io/nexus/maven-releases/de.eldoria/companies-api?label=Release&logo=Release&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)][release]
[![Snapshots](https://img.shields.io/nexus/s/de.eldoria/companies-api?color=orange&label=Snapshot&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)][snapshot]

<!-- [![Text](image_link)][link_anchor] -->
<!-- [anchor]: link> -->


# Dependency
**Gradle**
``` kotlin
repositories {
    maven("https://eldonexus.de/repository/maven-public")
}

dependencies {
    compileOnly("de.eldoria", "companies-api", "version")
}
```

**Maven**
``` xml
<repository>
    <id>EldoNexus</id>
    <url>https://eldonexus.de/repository/maven-public/</url>
</repository>

<dependency>
    <groupId>de.eldoria</groupId>
    <artifactId>companies-api</artifactId>
    <version>version</version>
</dependency>
```

# Setup

The plugin is plug & play. However, you can do several configurations.

## Requirements
* [Paper](https://papermc.io/) 1.21.11 or newer, running on Java 25
* [Vault](https://github.com/MilkBowl/Vault) and an economy plugin

## Optional Dependencies
* [PAPI](https://www.spigotmc.org/resources/6245/) for Placeholders
* [ProtocolLib](https://www.spigotmc.org/resources/1997/) for the best experience with the text UIs. **This is highly recommended.**

## Permissions

| Permission                       | Default | Description                                                                    |
|----------------------------------|---------|--------------------------------------------------------------------------------|
| `companies.orders.create`        | all     | Create orders                                                                  |
| `companies.orders.limit.<n>`     | none    | Raise or lower the order limit to `n` active orders. The highest number counts |
| `companies.company.create`       | all     | Found a company                                                                |
| `companies.company.join`         | all     | Be invited to and join a company                                               |
| `companies.updates`              | op      | Hear about new versions on join and download them                              |
| `companies.admin`                | op      | Use `/companyadmin` and create debug reports                                   |
| `companies.admin.calcLevel`      | op      | Recalculate company levels                                                     |
| `companies.admin.delete`         | op      | Delete companies                                                               |
| `companies.admin.level`          | op      | Manage the company levels                                                      |
| `companies.admin.reload`         | op      | Reload the configuration                                                       |
| `companies.admin.rename`         | op      | Rename companies                                                               |
| `companies.admin.transferOwner`  | op      | Transfer the ownership of a company                                            |

`companies.orders.*`, `companies.company.*`, `companies.admin.*` and `companies.*` grant the permissions below them.
Without a `companies.orders.limit.<n>` permission a player may have `maxOrders` active orders, as set in `user.yml`.

## Configuration
A detailed guide on the configuration can be found at the [wiki](https://github.com/eldoriarpg/companies/wiki/Configuration)

### Menus
`menus` in `config.yml` sets how menus such as the order builder, the order search, order views and confirmations are
shown:

| Value    | Menus are shown as                                                                    |
|----------|---------------------------------------------------------------------------------------|
| `DIALOG` | Dialog windows with input fields and buttons. Needs a Minecraft 1.21.6 client or newer |
| `CHAT`   | Clickable chat messages                                                               |

New installations use `DIALOG`. A `config.yml` from an older version has no `menus` entry and keeps showing chat menus
until `menus: DIALOG` is added.

Players whose client is older than 1.21.6, for example when joining through ViaVersion, always get chat menus. While a
chat menu is open, other chat messages are held back and delivered once the menu is closed with `[x]`. This needs
ProtocolLib and can be turned off with `generalSettings.messageBlocking: false`. Dialogs never hold back chat.

[wakatime]: https://wakatime.com/badge/github/eldoriarpg/companies
[publish]: https://github.com/eldoriarpg/companies/actions/workflows/publish_to_nexus.yml
[verify]: https://github.com/eldoriarpg/companies/actions/workflows/verify.yml
[release]: https://eldonexus.de/#browse/browse:maven-releases:de%2Feldoria%2Fcompanies-api
[snapshot]: https://eldonexus.de/#browse/browse:maven-snapshots:de%2Feldoria%2Fcompanies-api
