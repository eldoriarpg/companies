/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) EldoriaRPG Team and Contributor
 */
import {basename, extname} from 'node:path'

/** Vue's calls that make something reactive, which at module level is one value for every request. */
const REACTIVE_CALLS = new Set([
    'ref', 'shallowRef', 'reactive', 'shallowReactive', 'customRef', 'computed', 'readonly', 'shallowReadonly',
])

/** Where an imported reactivity call has to come from to count as Vue's own. */
const VUE_SOURCES = new Set(['vue', '#imports', '#app'])

/** The helpers for state only the browser writes, whose argument is the value every request starts at. */
const BROWSER_STATE_CALLS = new Set(['browserRef', 'browserShallowRef'])

/** The collections a module may hold only as constants, and the type that says they are. */
const COLLECTIONS = new Map([['Map', 'ReadonlyMap'], ['Set', 'ReadonlySet']])

/** The type wrappers TypeScript puts around an expression without changing its value. */
const TYPE_WRAPPERS = new Set(['TSAsExpression', 'TSSatisfiesExpression', 'TSNonNullExpression', 'TSTypeAssertion'])

/** Node types that open a scope of their own, so nothing below them runs when the module is loaded. */
const DEFERRED = new Set(['FunctionExpression', 'ArrowFunctionExpression', 'FunctionDeclaration', 'ClassBody'])

const GUIDE = 'Shared state belongs in useState or in browserRef from util/browserState.ts.'

/**
 * Mutable state at module level, and shared state set up so that a server render could leak it.
 *
 * <p>Nuxt evaluates a module once per server process, so whatever a module holds is one value for every
 * request the process answers. Request state lives in `useState`, browser machinery in `browserRef` or
 * `browserShallowRef`, and this rule holds the line between them:
 *
 * <ul>
 *   <li>no reactivity call (`ref`, `computed`, ...), no `let` or `var`, no `Map` or `Set` not typed
 *       `ReadonlyMap` or `ReadonlySet`, and no empty array or object not typed read-only, at module
 *       level;</li>
 *   <li>a `useState` key is a string literal starting with the file's base name, which keeps keys
 *       unique without a registry;</li>
 *   <li>a `useState` initialiser and a `browserRef` argument are literals, so the server and the browser
 *       start from the same value.</li>
 * </ul>
 *
 * <p>In a single file component only a `<script>` block without `setup` is module level. What it does
 * not see: a factory that hands out one shared object, such as `createI18n(...)` or `axios.create(...)`;
 * the two-renders SSR tests are the net for those.
 */
export default {
    meta: {
        type: 'problem',
        docs: {description: 'Forbid mutable module-level state, and keep useState and browserRef server-safe'},
        schema: [],
        messages: {
            reactive: `'{{name}}' at module level is one value for every request on the server. Hold request state in useState inside a function, browser machinery in browserRef. ${GUIDE}`,
            binding: `A module-level '{{kind}}' is shared by every request on the server. Make it a const, or a field of a browserShallowRef or a useState value. ${GUIDE}`,
            collection: `A module-level {{type}} is shared by every request on the server. Type it {{readonly}} if nothing ever changes it, otherwise move it into useState or browserRef. ${GUIDE}`,
            empty: `An empty {{what}} at module level is filled at runtime and shared by every request on the server. Type it read-only if it stays empty, otherwise move it into useState or browserRef. ${GUIDE}`,
            key: `A useState key is a string literal starting with '{{base}}', the file's name, which keeps keys unique. ${GUIDE}`,
            initial: `An initial value is a literal, so the server and the browser start alike. Apply what the browser knows in onMounted or a .client plugin. ${GUIDE}`,
        },
    },
    create(context) {
        const base = basename(context.filename, extname(context.filename))

        /**
         * Checks the calls that create shared state, wherever they are.
         *
         * @param node a call
         */
        function checkStateCall(node) {
            const name = node.callee.type === 'Identifier' ? node.callee.name : null
            if (name === 'useState') checkUseState(context, node, base)
            else if (BROWSER_STATE_CALLS.has(name) && node.arguments[0] && !isLiteralValue(context, node.arguments[0])) {
                context.report({node: node.arguments[0], messageId: 'initial'})
            }
        }

        return {
            Program(program) {
                const vueImports = importedNames(program)
                for (const statement of moduleStatements(context, program)) {
                    checkModuleStatement(context, unwrapExport(statement), vueImports)
                }
            },
            CallExpression: checkStateCall,
        }
    },
}

/**
 * The statements of a program that run when the module is loaded.
 *
 * @param context the rule context
 * @param program the program
 * @returns every top-level statement of a script, or those of a component's `<script>` block without
 *          `setup`
 */
function moduleStatements(context, program) {
    const fragment = context.sourceCode.parserServices?.getDocumentFragment?.()
    if (!fragment) return program.body
    const plain = fragment.children.find(child => child.type === 'VElement' && child.name === 'script'
        && !child.startTag.attributes.some(attribute => !attribute.directive && attribute.key.name === 'setup'))
    if (!plain) return []
    const [start, end] = plain.range
    return program.body.filter(statement => statement.range[0] >= start && statement.range[1] <= end)
}

/**
 * The declaration an export statement carries, or the statement itself.
 *
 * @param statement a top-level statement
 * @returns what it declares
 */
function unwrapExport(statement) {
    const exported = statement.type === 'ExportNamedDeclaration' || statement.type === 'ExportDefaultDeclaration'
    return exported && statement.declaration ? statement.declaration : statement
}

/**
 * The local names imported from somewhere other than Vue, which a reactivity call may not be.
 *
 * @param program the program
 * @returns local name to import source
 */
function importedNames(program) {
    const names = new Map()
    for (const statement of program.body) {
        if (statement.type !== 'ImportDeclaration') continue
        for (const specifier of statement.specifiers) names.set(specifier.local.name, statement.source.value)
    }
    return names
}

/**
 * Reports what a statement at module level holds that is mutable.
 *
 * @param context the rule context
 * @param statement the statement, export unwrapped
 * @param imports local name to import source
 */
function checkModuleStatement(context, statement, imports) {
    if (statement.type === 'VariableDeclaration') {
        if (statement.kind !== 'const') context.report({node: statement, messageId: 'binding', data: {kind: statement.kind}})
        for (const declarator of statement.declarations) {
            if (!declarator.init) continue
            checkEmptyHolder(context, declarator)
            checkLoadTime(context, declarator.init, imports, declarator)
        }
        return
    }
    if (statement.type === 'ExpressionStatement') checkLoadTime(context, statement.expression, imports, null)
}

/**
 * Reports an empty array or object bound at module level that is not typed read-only.
 *
 * @param context the rule context
 * @param declarator the variable declarator
 */
function checkEmptyHolder(context, declarator) {
    const value = unwrapTypes(declarator.init)
    const empty = (value.type === 'ArrayExpression' && value.elements.length === 0)
        || (value.type === 'ObjectExpression' && value.properties.length === 0)
    if (!empty || isReadonlyAnnotated(declarator) || isConstAsserted(declarator.init)) return
    context.report({node: value, messageId: 'empty', data: {what: value.type === 'ArrayExpression' ? 'array' : 'object'}})
}

/**
 * Walks an expression that runs when the module is loaded, leaving out what runs later, and reports
 * the reactivity calls and the collections that are not typed read-only.
 *
 * @param context the rule context
 * @param root the expression
 * @param imports local name to import source
 * @param declarator the declarator the expression is bound by, or null
 */
function checkLoadTime(context, root, imports, declarator) {
    const pending = [root]
    while (pending.length) {
        const node = pending.pop()
        if (!node || typeof node.type !== 'string' || DEFERRED.has(node.type)) continue
        if (node.type === 'CallExpression' && isBrowserStateCall(node)) continue
        if (node.type === 'CallExpression' && isReactiveCall(node, imports)) {
            context.report({node, messageId: 'reactive', data: {name: node.callee.name}})
        }
        if (node.type === 'NewExpression') checkCollection(context, node, declarator)
        pending.push(...childrenOf(node))
    }
}

/**
 * Reports a `Map` or `Set` built at module level that its declaration does not type read-only.
 *
 * @param context the rule context
 * @param node the construction
 * @param declarator the declarator the surrounding expression is bound by, or null
 */
function checkCollection(context, node, declarator) {
    const type = node.callee.type === 'Identifier' ? node.callee.name : null
    const readonly = COLLECTIONS.get(type)
    if (!readonly) return
    if (declarator && unwrapTypes(declarator.init) === node && declaredTypeName(declarator) === readonly) return
    if (isAssertedAs(node, readonly)) return
    context.report({node, messageId: 'collection', data: {type, readonly}})
}

/**
 * The child nodes of a node, the keys that lead back up or into types left out.
 *
 * @param node the node
 * @returns its children
 */
function childrenOf(node) {
    const children = []
    for (const [key, value] of Object.entries(node)) {
        if (key === 'parent' || key === 'typeAnnotation' || key === 'typeArguments' || key === 'typeParameters') continue
        if (Array.isArray(value)) children.push(...value.filter(child => child && typeof child.type === 'string'))
        else if (value && typeof value.type === 'string') children.push(value)
    }
    return children
}

/**
 * Whether a call is one of Vue's reactivity calls, by name and, where it is imported, by where from.
 *
 * @param node the call
 * @param imports local name to import source
 * @returns true for `ref(...)`, `computed(...)` and the like
 */
function isReactiveCall(node, imports) {
    if (node.callee.type !== 'Identifier' || !REACTIVE_CALLS.has(node.callee.name)) return false
    const source = imports.get(node.callee.name)
    return source === undefined || VUE_SOURCES.has(source)
}

function isBrowserStateCall(node) {
    return node.callee.type === 'Identifier' && BROWSER_STATE_CALLS.has(node.callee.name)
}

/**
 * Checks a `useState` call: its key, and the value its initialiser starts from.
 *
 * @param context the rule context
 * @param node the call
 * @param base the file's base name
 */
function checkUseState(context, node, base) {
    const [key, init] = node.arguments
    const text = staticString(key)
    if (text === null || (text !== base && !text.startsWith(`${base}.`))) {
        context.report({node: key ?? node, messageId: 'key', data: {base}})
    }
    if (!init) return
    const value = initialiserValue(init)
    if (!value || !isLiteralValue(context, value)) context.report({node: init, messageId: 'initial'})
}

/**
 * The value an initialiser returns, where it is a function returning one expression.
 *
 * @param init the initialiser
 * @returns the expression, or null for any other initialiser
 */
function initialiserValue(init) {
    if (init.type !== 'ArrowFunctionExpression' && init.type !== 'FunctionExpression') return null
    if (init.body.type !== 'BlockStatement') return init.body
    const [only] = init.body.body
    return init.body.body.length === 1 && only.type === 'ReturnStatement' ? only.argument : null
}

/**
 * Whether a value is a literal in the sense of an initial value: a literal, an empty `Map` or `Set`, a
 * constant bound to one, or arrays and objects of those.
 *
 * @param context the rule context
 * @param node the value
 * @param depth how many constants have been followed to get here
 * @returns true where nothing the browser knows can reach the value
 */
function isLiteralValue(context, node, depth = 0) {
    const value = unwrapTypes(node)
    switch (value.type) {
        case 'Literal':
            return true
        case 'TemplateLiteral':
            return value.expressions.length === 0
        case 'UnaryExpression':
            return (value.operator === '-' || value.operator === '+') && value.argument.type === 'Literal'
        case 'NewExpression':
            return COLLECTIONS.has(value.callee.name) && value.arguments.length === 0
        case 'ArrayExpression':
            return value.elements.every(element => element && element.type !== 'SpreadElement'
                && isLiteralValue(context, element, depth))
        case 'ObjectExpression':
            return value.properties.every(property => property.type === 'Property' && !property.computed
                && isLiteralValue(context, property.value, depth))
        case 'Identifier':
            return value.name === 'undefined' || isLiteralConstant(context, value, depth)
        default:
            return false
    }
}

/**
 * Whether an identifier names a `const` of the same file that is bound to a literal value.
 *
 * @param context the rule context
 * @param identifier the identifier
 * @param depth how many constants have been followed to get here
 * @returns true for such a constant
 */
function isLiteralConstant(context, identifier, depth) {
    if (depth > 4) return false
    let scope = context.sourceCode.getScope(identifier)
    while (scope) {
        const variable = scope.set.get(identifier.name)
        if (variable) {
            const definition = variable.defs[0]
            return definition?.type === 'Variable' && definition.parent.kind === 'const' && definition.node.init != null
                && isLiteralValue(context, definition.node.init, depth + 1)
        }
        scope = scope.upper
    }
    return false
}

/**
 * The text of a string literal, or of a template literal without expressions.
 *
 * @param node the expression
 * @returns its text, or null where it is not fixed
 */
function staticString(node) {
    if (!node) return null
    if (node.type === 'Literal' && typeof node.value === 'string') return node.value
    if (node.type === 'TemplateLiteral' && node.expressions.length === 0) return node.quasis[0].value.cooked
    return null
}

function unwrapTypes(node) {
    let value = node
    while (TYPE_WRAPPERS.has(value.type)) value = value.expression
    return value
}

/**
 * The name of a type reference, such as `ReadonlySet` for `ReadonlySet<string>`.
 *
 * @param type a type node
 * @returns the name, or null for any other type
 */
function referenceName(type) {
    return type?.type === 'TSTypeReference' && type.typeName.type === 'Identifier' ? type.typeName.name : null
}

/**
 * The name of the type a declarator is annotated with.
 *
 * @param declarator the variable declarator
 * @returns the name, or null
 */
function declaredTypeName(declarator) {
    return referenceName(declarator.id.typeAnnotation?.typeAnnotation)
}

/**
 * Whether an expression is cast to the given type by a wrapper right around it.
 *
 * @param node the expression
 * @param name the type's name
 * @returns true for `new Set(...) as ReadonlySet<...>` and the like
 */
function isAssertedAs(node, name) {
    const parent = node.parent
    return TYPE_WRAPPERS.has(parent?.type) && referenceName(parent.typeAnnotation) === name
}

/**
 * Whether a declarator's annotation is read-only: `readonly T[]`, `ReadonlyArray<T>` or `Readonly<T>`.
 *
 * @param declarator the variable declarator
 * @returns true for a read-only annotation
 */
function isReadonlyAnnotated(declarator) {
    const type = declarator.id.typeAnnotation?.typeAnnotation
    if (type?.type === 'TSTypeOperator' && type.operator === 'readonly') return true
    const name = declaredTypeName(declarator)
    return name === 'ReadonlyArray' || name === 'Readonly'
}

/**
 * Whether an expression is asserted `as const`.
 *
 * @param node the expression
 * @returns true for a const assertion
 */
function isConstAsserted(node) {
    return node.type === 'TSAsExpression' && referenceName(node.typeAnnotation) === 'const'
}
