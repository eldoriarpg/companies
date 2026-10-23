---
title: base_configuration.yml
sidebar_position: 8
---

# base_configuration.yml

The log level of the plugin, and some bookkeeping.

This file belongs to one server and is never shared. It comes from the EldoUtilities library, which other Eldoria plugins use too.

## Example

The key you may want to change:

```yaml
logLevel: INFO
```

## Keys

| Key | Type | Default | Meaning |
|---|---|---|---|
| `logLevel` | log level | `INFO` | How much Companies writes to the server log. |
| `version` | number | `0` | Used by the library. Do not change it. |
| `lastInstalledVersion` | text | not set | The plugin version that ran last. Do not change it. |

`logLevel` takes these values, from least to most output: `OFF`, `SEVERE`, `WARNING`, `INFO`, `DEBUG`, `FINE`, `FINER`, `FINEST`, `ALL`. `DEBUG` (or `TRUE`) shows debug messages. Any other value counts as `INFO`.

Set `logLevel: DEBUG` when you report a problem, together with a `/companyadmin debug` report.

:::warning TODO
Confirm whether a new `logLevel` applies on `/companyadmin reload`, or only after a restart.
:::
