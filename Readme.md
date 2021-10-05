[![wakatime](https://wakatime.com/badge/github/eldoriarpg/companies.svg)](https://wakatime.com/badge/github/eldoriarpg/companies)

![GitHub Workflow Status](https://img.shields.io/github/workflow/status/eldoriarpg/companies/Publish%20to%20Nexus?style=for-the-badge&label=Publish)
![GitHub Workflow Status](https://img.shields.io/github/workflow/status/eldoriarpg/companies/Verify%20state?style=for-the-badge&label=Building)

![Sonatype Nexus (Releases)](https://img.shields.io/nexus/maven-releases/de.eldoria/companies-api?label=Release&logo=Release&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)
![Sonatype Nexus (Development)](https://img.shields.io/nexus/maven-dev/de.eldoria/companies-api?label=DEV&logo=Release&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)
![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/de.eldoria/companies-api?color=orange&label=Snapshot&server=https%3A%2F%2Feldonexus.de&style=for-the-badge)

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

The plugin is plug & play. However you can do several configurations.

## Optional Dependencies
* [PAPI](https://www.spigotmc.org/resources/6245/) for Placeholders
* [ProtocolLib](https://www.spigotmc.org/resources/1997/) for the best experience with the text UIs. **This is highly recommended.**

## Configuration
The configuration needs to be done in the `config.yml`
### Company Settings
**DeliveryHours**\
Amount of hours after a order was claimed. After this time the order will be claimable again.

**FoundingPrice**\
Price to found a company

**RenamePrice**\
Price to rename a existing companies (Does not count for the admin commands)

**ExpiredOrdersPenalty**\
The penalty which is applied to the order count in the company statistic when a order is expired.

**AbortedOrdersPenalty**\
The penalty which is applied to the order count in the company statistic when a order is aborted.

**Level**\
Definition of company levels. One level needs to be defined all the time and will be regenerated.\
Please use the ingame command to modify this.

### User Settings
**MaxOrders**\
Max amount of orders one user can have. This countrs for claimed and unclaimed orders.

### Order Settings
**MaxItems**\
Max amount of items in one order.

**MaxMaterials**\
Amount of materials in one orders.

**MaxUnclaimedHours**\
The amount of time after an unclaimed order gets removed and the money is refunded.

### General Settings
**OrderCheckInterval**\
The interval in minutes which is used to check for expired orders

**Language**\
The language which should be used. Needs to be one of the languages in the messages directory.

### Database Settings

**StorageType**\
The choosen storage type.\
One of: `postgres`, `sqlite` or `mariadb`. **Postgres is recommended for best performance**.

**Host**\
Host of your database. Should be localhost all the time.\
Required for `postgres`, `mariadb`

**Port**\
Port of the database\
Required for `postgres`, `mariadb`

**User**\
User to log in.\
Required for `postgres`, `mariadb`

**Password**\
Password to log in.\
Required for `postgres`, `mariadb`

**Database**\
Database to use. This database needs to exist and will not be created by the plugin.\
Required for `postgres`, `mariadb`

**Schema**\
Schema to use. This schema needs to exists and will not be created by the plugin.\
Required for `postgres`
