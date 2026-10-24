/**
 * Finds rows, cards and names that open a page without being links.
 *
 * <p>Pressing one of these opens a page, so each of them is a link. Written as a click handler on a
 * `div` it is not one: no middle click, no address to copy, nothing in the status bar, nothing in
 * the tab order, and nothing for a screen reader to announce or to list.
 *
 * <p>Three kinds of navigation have to be told apart, and only the first is a link: opening a page
 * the reader chose (a row, a card, a name), going on after doing something (saving and landing on
 * what was saved), and going back. So what is reported is narrow on purpose: a click handler whose
 * whole body is a navigation, on an element that is not a link and not a button. A handler that
 * saves first, or asks first, is the second kind and is left alone, and so is one written over
 * more than one line, which is never a bare push. A button that navigates is often the third kind,
 * and turning it into a link is a design decision, so buttons and menu entries are left alone too.
 */

const NOT_REPORTED = /^(a|button|NuxtLink|RouterLink|router-link|RowLink|AppLink|SidebarLink|HelpCenterLink|IconButton|[A-Za-z]*Button|[A-Za-z]*MenuItem)$/

/**
 * The identifier a call or member chain starts from, such as `router` in `router.push(…)`.
 *
 * @param expression the expression
 * @returns the identifier's name and whether the chain reaches past it, or null
 */
function chainStart(expression) {
    let node = expression
    let reached = false
    while (node.type === 'CallExpression' || node.type === 'MemberExpression' || node.type === 'ChainExpression') {
        node = node.type === 'CallExpression' ? node.callee : node.type === 'MemberExpression' ? node.object : node.expression
        reached = true
    }
    return node.type === 'Identifier' && reached ? node.name : null
}

/**
 * Whether an expression is a navigation, a push on the router or a `navigateTo`.
 *
 * @param expression the expression
 * @param routers the names the router goes by where the expression stands
 * @returns true for a navigation
 */
function isNavigation(expression, routers) {
    const start = chainStart(expression)
    return start !== null && (routers.includes(start) || start === 'navigateTo')
}

/**
 * The expression a statement consists of, seen through `return` and `void`.
 *
 * @param statement the statement
 * @returns the expression, or null
 */
function expressionOf(statement) {
    let expression = statement.type === 'ExpressionStatement' ? statement.expression
        : statement.type === 'ReturnStatement' ? statement.argument : null
    if (expression?.type === 'UnaryExpression' && expression.operator === 'void') expression = expression.argument
    return expression
}

/**
 * Whether a function declared in this file does nothing but navigate: one statement, written on
 * one line, that is a navigation.
 *
 * @param program the component's script program
 * @param name the handler's name
 * @returns true for a handler that only navigates
 */
function onlyNavigates(program, name) {
    const declaration = program.body
        .map(statement => (statement.type === 'ExportNamedDeclaration' ? statement.declaration : statement))
        .find(statement => statement?.type === 'FunctionDeclaration' && statement.id?.name === name)
    if (!declaration || declaration.body.body.length !== 1) return false
    const [statement] = declaration.body.body
    if (statement.loc.start.line !== statement.loc.end.line) return false
    const expression = expressionOf(statement)
    return expression !== null && isNavigation(expression, ['router'])
}

/**
 * Whether a click handler opens a page, written in the template or named from it.
 *
 * @param value the handler's expression
 * @param program the component's script program
 * @returns true for a handler whose whole body is a navigation
 */
function opensAPage(value, program) {
    if (!value) return false
    if (value.type === 'Identifier') return onlyNavigates(program, value.name)
    if (value.type === 'ArrowFunctionExpression') {
        return value.params.length === 0 && value.body.type !== 'BlockStatement'
            && isNavigation(value.body, ['router', '$router'])
    }
    if (value.type !== 'VOnExpression' || value.body.length === 0) return false
    const first = expressionOf(value.body[0])
    if (first === null) return false
    if (isNavigation(first, ['router', '$router'])) return true
    const named = value.body.length === 1 && first.type === 'CallExpression' && first.callee.type === 'Identifier'
    return named && onlyNavigates(program, first.callee.name)
}

export default {
    meta: {
        type: 'problem',
        docs: {description: 'A row, card or name that opens a page is a link, not a click handler.'},
        schema: [],
        messages: {
            link: '<{{element}}> opens a page from a click handler. A row that opens a page is a link: wrap it in RowLink (components/navigation/RowLink.vue).',
        },
    },
    create(context) {
        const program = context.sourceCode.ast
        const template = program.templateBody
        if (!template) return {}
        return context.sourceCode.parserServices.defineTemplateBodyVisitor({
            "VAttribute[directive=true][key.name.name='on']"(attribute) {
                const argument = attribute.key.argument
                if (argument?.type !== 'VIdentifier' || argument.name !== 'click') return
                const element = attribute.parent.parent
                if (NOT_REPORTED.test(element.rawName)) return
                const value = attribute.value?.type === 'VExpressionContainer' ? attribute.value.expression : null
                if (!opensAPage(value, program)) return
                context.report({node: attribute, messageId: 'link', data: {element: element.rawName}})
            },
        })
    },
}
