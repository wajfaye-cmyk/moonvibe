// Minecraft schreibt seine Logs als XML (log4j). Das hier macht daraus lesbare Zeilen.
export class LogParser {
  private level = 'INFO';
  private buffer: string[] | null = null;

  /** Nimmt eine Rohzeile und gibt 0..n lesbare Zeilen zurück. */
  push(raw: string): string[] {
    const event = raw.match(/<log4j:Event [^>]*level="(\w+)"/);
    if (event) {
      this.level = event[1];
      return [];
    }

    // Mehrzeilige CDATA-Blöcke (z. B. Stacktraces) einsammeln
    if (this.buffer) {
      const end = raw.indexOf(']]>');
      if (end === -1) {
        this.buffer.push(raw);
        return [];
      }
      this.buffer.push(raw.slice(0, end));
      return this.flush();
    }

    const start = raw.match(/<log4j:(Message|Throwable)><!\[CDATA\[(.*)$/);
    if (start) {
      const rest = start[2];
      const end = rest.indexOf(']]>');
      this.buffer = [end === -1 ? rest : rest.slice(0, end)];
      return end === -1 ? [] : this.flush();
    }

    if (/^\s*<\/?log4j:/.test(raw)) return [];
    return [raw];
  }

  private flush(): string[] {
    const lines = this.buffer ?? [];
    this.buffer = null;
    const prefix = this.level === 'INFO' ? '' : `[${this.level}] `;
    return lines.map((l, i) => (i === 0 ? prefix + l : l));
  }
}
