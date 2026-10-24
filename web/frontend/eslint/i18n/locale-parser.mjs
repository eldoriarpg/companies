/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {existsSync, readFileSync, statSync} from 'node:fs'
import {dirname, resolve} from 'node:path'
import * as typescriptParser from '@typescript-eslint/parser'
import * as jsonc from 'jsonc-eslint-parser'

/**
 * Reads a TypeScript locale module the way `@intlify/eslint-plugin-vue-i18n` reads a JSON one.
 *
 * <p>The plugin's rules for locale files walk the AST `jsonc-eslint-parser` defines and look for
 * `parserServices.isJSON`, and they take no other shape. The locales here are modules exporting one
 * object literal, so this parses the module as TypeScript and hands ESLint that object converted
 * node by node into the JSON AST, every node keeping the range of the source it came from. Reports
 * therefore land on the real line of the `.ts` file.
 *
 * <p>A value may be a string, a concatenation of strings, a template literal without substitutions,
 * a number, a boolean or a nested object. An identifier naming a constant declared in the module
 * stands for that constant's value, which is how messages share one sentence between keys. So
 * does a constant imported by name from a sibling module, which is how blocks kept in files of their
 * own share one sentence between files; it stands in as the plain string, at the identifier's place. An
 * identifier naming anything else, such as an imported block merged in under its key, becomes an
 * empty object: it is a block that lives in a file of its own and is read there.
 *
 * <p>ESLint gets nothing but this AST for such a file, so a config must hand the locale files to
 * this parser alone and run only rules written for the JSON AST on them.
 *
 * @param code the module's source
 * @param options the parser options, whose `filePath` the imports are resolved against
 * @returns what ESLint expects of a parser
 */
export function parseForESLint(code, options = {}) {
    const program = parseModule(code)
    const exported = program.body.find(statement => statement.type === 'ExportDefaultDeclaration')
    const constants = moduleConstants(program, options.filePath)
    const expression = exported
        ? convert(unwrap(exported.declaration), constants)
        : {type: 'JSONObjectExpression', properties: [], range: program.range, loc: program.loc}
    const statement = {type: 'JSONExpressionStatement', expression, range: expression.range, loc: expression.loc}
    return {
        ast: {
            type: 'Program',
            body: [statement],
            comments: program.comments,
            tokens: program.tokens,
            range: [0, code.length],
            loc: {start: {line: 1, column: 0}, end: program.loc.end},
        },
        visitorKeys: jsonc.VisitorKeys,
        services: {isJSON: true},
    }
}

/** Parser metadata ESLint prints and caches by. */
export const meta = {name: 'companies-locale-parser'}

const cache = new Map()

/**
 * The messages a locale module defines, as a plain object, read from disk and kept until the file
 * changes.
 *
 * @param file the absolute path of the module
 * @returns its messages
 */
export function localeMessagesOf(file) {
    const modified = statSync(file).mtimeMs
    const cached = cache.get(file)
    if (cached && cached.modified === modified) return cached.messages
    const messages = messagesOf(parseForESLint(readFileSync(file, 'utf-8'), {filePath: file}).ast)
    cache.set(file, {modified, messages})
    return messages
}

/**
 * The messages the JSON AST of a locale module describes.
 *
 * @param ast the program this parser produced
 * @returns the messages as a plain object
 */
export function messagesOf(ast) {
    return jsonc.getStaticJSONValue(ast.body[0].expression)
}

/**
 * Every leaf key of a message tree as a dotted path.
 *
 * @param messages the tree
 * @param prefix the path the tree sits under
 * @returns the dotted paths of its messages
 */
export function leafKeys(messages, prefix = '') {
    const keys = []
    for (const [name, value] of Object.entries(messages)) {
        const key = prefix ? `${prefix}.${name}` : name
        if (typeof value === 'object' && value !== null) keys.push(...leafKeys(value, key))
        else keys.push(key)
    }
    return keys
}

/**
 * A module's source as a TypeScript AST with ranges, locations, tokens and comments.
 *
 * @param code the source
 * @returns the program
 */
function parseModule(code) {
    return typescriptParser.parse(code, {range: true, loc: true, tokens: true, comment: true, sourceType: 'module'})
}

/**
 * The constants a module can name: those declared at its top, exported or not, with the expression
 * each is set to, and those it imports by name from a relative module, with the string each holds.
 *
 * @param program the parsed module
 * @param filePath the module's absolute path, without which imports are not followed
 * @returns the constants by name, as `{node}` for a declaration and `{value}` for an import
 */
function moduleConstants(program, filePath) {
    const constants = new Map()
    for (const statement of program.body) {
        const declaration = statement.type === 'ExportNamedDeclaration' ? statement.declaration : statement
        if (declaration?.type === 'VariableDeclaration' && declaration.kind === 'const') {
            for (const declarator of declaration.declarations) {
                if (declarator.id.type === 'Identifier' && declarator.init) constants.set(declarator.id.name, {node: declarator.init})
            }
        }
        if (statement.type === 'ImportDeclaration' && filePath) importConstants(statement, filePath, constants)
    }
    return constants
}

/**
 * Adds the string constants one import names to a module's constants.
 *
 * <p>Only a relative module that exists as a `.ts` file is followed, and only a name it declares as
 * a constant with a static string value is taken. Anything else stays unknown, as it was before.
 *
 * @param statement the import declaration
 * @param filePath the importing module's absolute path
 * @param constants the constants to add to
 */
function importConstants(statement, filePath, constants) {
    const source = statement.source.value
    if (!source.startsWith('.')) return
    const file = resolve(dirname(filePath), source.endsWith('.ts') ? source : `${source}.ts`)
    if (!existsSync(file)) return
    const imported = moduleConstants(parseModule(readFileSync(file, 'utf-8')), file)
    for (const specifier of statement.specifiers) {
        if (specifier.type !== 'ImportSpecifier') continue
        const constant = imported.get(specifier.imported.name)
        const value = constant && staticValue(constant, imported)
        if (typeof value === 'string') constants.set(specifier.local.name, {value})
    }
}

/**
 * The value a constant holds, read without running anything.
 *
 * @param constant the constant, as `moduleConstants` records it
 * @param constants the constants of the module declaring it
 * @returns the value, or undefined where it is not static
 */
function staticValue(constant, constants) {
    if ('value' in constant) return constant.value
    try {
        return jsonc.getStaticJSONValue(convert(unwrap(constant.node), constants))
    } catch {
        return undefined
    }
}

/**
 * An expression without the type assertions around it, which carry no message.
 *
 * @param node the expression
 * @returns the expression inside
 */
function unwrap(node) {
    let current = node
    while (current.type === 'TSAsExpression' || current.type === 'TSSatisfiesExpression') current = current.expression
    return current
}

/**
 * One TypeScript expression as the JSON AST node that stands for it.
 *
 * @param node the expression
 * @param constants the module's constants, for identifiers that name one
 * @returns a new JSON AST node carrying the expression's range
 */
function convert(node, constants) {
    const at = {range: node.range, loc: node.loc}
    switch (node.type) {
        case 'ObjectExpression':
            return {type: 'JSONObjectExpression', properties: node.properties.filter(isPlainProperty).map(property => convertProperty(property, constants)), ...at}
        case 'ArrayExpression':
            return {type: 'JSONArrayExpression', elements: node.elements.map(element => element && convert(element, constants)), ...at}
        case 'Literal':
            return {type: 'JSONLiteral', value: node.value, raw: node.raw, ...at}
        case 'TemplateLiteral':
            return node.expressions.length === 0
                ? {type: 'JSONTemplateLiteral', quasis: node.quasis.map(quasi => ({type: 'JSONTemplateElement', value: quasi.value, tail: quasi.tail, range: quasi.range, loc: quasi.loc})), expressions: [], ...at}
                : emptyObject(at)
        case 'BinaryExpression':
            return {type: 'JSONBinaryExpression', operator: node.operator, left: convert(node.left, constants), right: convert(node.right, constants), ...at}
        case 'Identifier':
            return constantNode(constants.get(node.name), constants, at)
        case 'TSAsExpression':
        case 'TSSatisfiesExpression':
            return convert(unwrap(node), constants)
        default:
            return emptyObject(at)
    }
}

/**
 * The JSON AST node an identifier stands for: a declared constant's expression, an imported
 * constant's string at the identifier's place, or an empty object for anything else.
 *
 * @param constant the constant the identifier names, if any
 * @param constants the module's constants
 * @param at the identifier's range and location
 * @returns the node
 */
function constantNode(constant, constants, at) {
    if (!constant) return emptyObject(at)
    if ('value' in constant) return {type: 'JSONLiteral', value: constant.value, raw: JSON.stringify(constant.value), ...at}
    return convert(unwrap(constant.node), constants)
}

/**
 * Whether an object member is a plain `key: value` pair, which is all a message tree holds.
 *
 * @param property the member
 * @returns false for spreads, methods and computed keys
 */
function isPlainProperty(property) {
    return property.type === 'Property' && !property.computed && property.kind === 'init' && !property.method
}

/**
 * One property as a JSON AST property.
 *
 * @param property the TypeScript property
 * @param constants the module's constants
 * @returns the JSON AST property
 */
function convertProperty(property, constants) {
    const key = property.key.type === 'Identifier'
        ? {type: 'JSONIdentifier', name: property.key.name, range: property.key.range, loc: property.key.loc}
        : {type: 'JSONLiteral', value: property.key.value, raw: property.key.raw, range: property.key.range, loc: property.key.loc}
    return {
        type: 'JSONProperty',
        kind: 'init',
        computed: false,
        method: false,
        shorthand: false,
        key,
        value: convert(property.value, constants),
        range: property.range,
        loc: property.loc,
    }
}

/**
 * An empty object standing where a value is not a message this file defines.
 *
 * @param at the range and location it takes
 * @returns the node
 */
function emptyObject(at) {
    return {type: 'JSONObjectExpression', properties: [], ...at}
}
