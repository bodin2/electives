const createLogMethod =
    (method: 'log' | 'debug' | 'warn' | 'error' | 'trace') =>
    (tag: string, ...args: unknown[]) =>
        console[method](
            `%c ${tag} %c`,
            'font-weight: bold; background-color: MidnightBlue; border-radius: 0.5em; color: white',
            '',
            ...args,
        )

export default class Logger {
    static info = createLogMethod('log')
    static debug = createLogMethod('debug')
    static warn = createLogMethod('warn')
    static error = createLogMethod('error')
    static trace = createLogMethod('trace')

    constructor(public tag: string) {}

    info(...args: unknown[]) {
        Logger.info(this.tag, ...args)
    }

    debug(...args: unknown[]) {
        Logger.debug(this.tag, ...args)
    }

    warn(...args: unknown[]) {
        Logger.warn(this.tag, ...args)
    }

    error(...args: unknown[]) {
        Logger.error(this.tag, ...args)
    }

    trace(...args: unknown[]) {
        Logger.trace(this.tag, ...args)
    }
}
