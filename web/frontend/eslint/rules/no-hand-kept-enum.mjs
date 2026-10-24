/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C EldoriaRPG Team and Contributor
 */
import {existsSync, readFileSync} from 'node:fs'
import {join} from 'node:path'
import {SRC} from './vue-template.mjs'

/** The file the API generator writes, which carries a constant for every string enum of the protocol. */
const SCHEMA = join(SRC, 'api', 'generated', 'protocol.ts')

const GUIDE = "Enums from the protocol come generated: import the constant from '@/api/generated/protocol'."

let generated = null

/**
 * The names of the enum constants the generator writes, read once per process. Until the generator
 * has written its file there are none, and the rule has nothing to compare against.
 *
 * @returns the constant names
 */
function generatedConstants() {
    if (generated) return generated
    generated = existsSync(SCHEMA)
        ? new Set([...readFileSync(SCHEMA, 'utf-8').matchAll(/^export const (\w+) = \{/gm)].map(match => match[1]))
        : new Set()
    return generated
}

/**
 * Hand-kept copies of the enum constants the API generator already writes.
 *
 * <p>For every string enum of the protocol, `src/api/generated/protocol.ts` exports the union type and a
 * constant of its values under one name. A copy kept by hand drifts from the protocol the day a value
 * is added, so the rule refuses the two shapes such a copy takes:
 *
 * <ul>
 *   <li>`const X = {...} as const satisfies Record<...>` where the generator writes a constant `X`;</li>
 *   <li>a type alias `XName` of a generated enum (`components['schemas']['...']` or `Schemas['...']`)
 *       in a file that declares a constant `X` of its own.</li>
 * </ul>
 *
 * <p>A constant that has to stay, because the generated one carries a name that says nothing,
 * is named in the `allowed` option. `generated` replaces the names read from the schema, which is
 * how the rule is put to a test.
 */
export default {
    meta: {
        type: 'problem',
        docs: {description: 'Forbid hand-kept copies of the generated enum constants'},
        schema: [{
            type: 'object',
            properties: {
                generated: {type: 'array', items: {type: 'string'}},
                allowed: {type: 'array', items: {type: 'string'}},
            },
            additionalProperties: false,
        }],
        messages: {
            copy: `'{{name}}' is generated: import it from '@/api/generated/protocol' instead of keeping a copy. ${GUIDE}`,
            alias: `'{{alias}}' renames the generated enum '{{schema}}' for a hand-kept '{{name}}'. Import the generated constant and type from '@/api/generated/protocol' instead. ${GUIDE}`,
        },
    },
    create(context) {
        const options = context.options[0] ?? {}
        const known = options.generated ? new Set(options.generated) : generatedConstants()
        const allowed = new Set(options.allowed ?? [])
        const constants = new Set()
        const aliases = []
        return {
            VariableDeclarator(node) {
                if (node.id.type !== 'Identifier' || node.parent.kind !== 'const') return
                constants.add(node.id.name)
                if (isConstRecord(node.init) && known.has(node.id.name) && !allowed.has(node.id.name)) {
                    context.report({node: node.id, messageId: 'copy', data: {name: node.id.name}})
                }
            },
            TSTypeAliasDeclaration(node) {
                const schema = schemaEnumOf(node.typeAnnotation)
                if (node.id.name.endsWith('Name') && schema && known.has(schema)) aliases.push({node, schema})
            },
            'Program:exit'() {
                for (const {node, schema} of aliases) {
                    const name = node.id.name.slice(0, -'Name'.length)
                    if (!constants.has(name) || allowed.has(name)) continue
                    context.report({node: node.id, messageId: 'alias', data: {alias: node.id.name, schema, name}})
                }
            },
        }
    },
}

/**
 * Whether an initialiser is an object asserted `as const` and checked against a `Record`.
 *
 * @param init the initialiser
 * @returns true for `{...} as const satisfies Record<...>`
 */
function isConstRecord(init) {
    if (init?.type !== 'TSSatisfiesExpression' || referenceName(init.typeAnnotation) !== 'Record') return false
    const asserted = init.expression
    return asserted.type === 'TSAsExpression' && referenceName(asserted.typeAnnotation) === 'const'
        && asserted.expression.type === 'ObjectExpression'
}

/**
 * The schema name an indexed type reads, such as `FormStatus` for `components['schemas']['FormStatus']`.
 *
 * @param type a type node
 * @returns the name, or null for any other type
 */
function schemaEnumOf(type) {
    if (type.type !== 'TSIndexedAccessType' || type.indexType.type !== 'TSLiteralType') return null
    const value = type.indexType.literal.value
    return typeof value === 'string' && readsSchemas(type.objectType) ? value : null
}

/**
 * Whether a type is the generated schema table, either `components['schemas']` or a `Schemas` alias.
 *
 * @param type a type node
 * @returns true for either form
 */
function readsSchemas(type) {
    if (referenceName(type) === 'Schemas') return true
    return type.type === 'TSIndexedAccessType' && referenceName(type.objectType) === 'components'
        && type.indexType.type === 'TSLiteralType' && type.indexType.literal.value === 'schemas'
}

/**
 * The name of a type reference, such as `Record` for `Record<A, B>`.
 *
 * @param type a type node
 * @returns the name, or null for any other type
 */
function referenceName(type) {
    return type?.type === 'TSTypeReference' && type.typeName.type === 'Identifier' ? type.typeName.name : null
}
