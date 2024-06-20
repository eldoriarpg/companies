`builder\(.*?\)\s*\.` -> ` `
`\.(setInt|setString|setLong|setDouble|setEnum)\(` -> `.bind(`
`\.parameter\(stmt -> stmt` -> `.single(call()`
`\.setUuidAsBytes\((.+?)\)` -> `.bind($1, UUID_BYTES)`
`getUuidFromBytes\((.*?)\)` -> `get($1, UUID_BYTES)`
`\s*\.sendSync\(\)` -> ` `
`readRow` -> `map`
`firstSync` -> `first`
`allSync` -> `all`
`queryWithoutParams\((.+?)\)` -> `query($1).single()`
`de.chojo.sadu.wrapper.util.Row` -> `de.chojo.sadu.mapper.wrapper.Row`
`emptyParams\(\)` -> `single()`
