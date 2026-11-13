// Eigenes Auswahlmenü (statt des Windows-Standard-Dropdowns) – passend zum Launcher-Design.

export interface DropdownOption {
  value: string;
  label: string;
  /** kleiner Zusatz rechts (z. B. "Fabric") */
  hint?: string;
  /** hervorgehobener Zusatz (z. B. ★ moonvibe) */
  badge?: string;
}

export class Dropdown {
  readonly element: HTMLDivElement;
  private readonly button: HTMLButtonElement;
  private readonly menu: HTMLDivElement;
  private options: DropdownOption[] = [];
  private current = '';
  private footer: HTMLElement | null = null;
  private onChange: (value: string) => void = () => {};

  constructor(
    private readonly host: HTMLElement,
    private readonly opts: { icon?: string; className?: string; align?: 'left' | 'right'; up?: boolean } = {},
  ) {
    this.element = document.createElement('div');
    this.element.className = `dropdown ${opts.className ?? ''}`;
    this.button = document.createElement('button');
    this.button.type = 'button';
    this.button.className = 'dropdown-button';
    this.menu = document.createElement('div');
    this.menu.className = `dropdown-menu ${opts.align === 'right' ? 'right' : ''} ${opts.up ? 'up' : ''}`;
    this.menu.hidden = true;
    this.element.append(this.button, this.menu);
    host.replaceChildren(this.element);

    this.button.addEventListener('click', (e) => {
      e.stopPropagation();
      this.toggle();
    });
    document.addEventListener('click', (e) => {
      if (!this.element.contains(e.target as Node)) this.close();
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') this.close();
    });
    window.addEventListener('resize', () => this.close());
  }

  change(cb: (value: string) => void): this {
    this.onChange = cb;
    return this;
  }

  /** Zusatzbereich unten im Menü (z. B. Schalter "Alle Versionen"). */
  setFooter(el: HTMLElement | null): void {
    this.footer = el;
    this.renderMenu();
  }

  setOptions(options: DropdownOption[], value?: string): void {
    this.options = options;
    if (value !== undefined) this.current = value;
    if (!options.some((o) => o.value === this.current)) this.current = options[0]?.value ?? '';
    this.renderButton();
    this.renderMenu();
  }

  get value(): string {
    return this.current;
  }

  set value(v: string) {
    this.current = v;
    this.renderButton();
    this.renderMenu();
  }

  private selected(): DropdownOption | undefined {
    return this.options.find((o) => o.value === this.current);
  }

  private renderButton(): void {
    const sel = this.selected();
    const parts: Node[] = [];
    if (this.opts.icon) {
      const icon = document.createElement('span');
      icon.className = 'dropdown-icon';
      icon.innerHTML = this.opts.icon;
      parts.push(icon);
    }
    const label = document.createElement('span');
    label.className = 'dropdown-label';
    label.textContent = sel?.label ?? '';
    parts.push(label);
    if (sel?.badge) parts.push(Object.assign(document.createElement('span'), { className: 'dropdown-badge', textContent: sel.badge }));
    else if (sel?.hint) parts.push(Object.assign(document.createElement('span'), { className: 'dropdown-hint', textContent: sel.hint }));
    const chevron = document.createElement('span');
    chevron.className = 'dropdown-chevron';
    chevron.innerHTML = '<svg viewBox="0 0 24 24"><path d="M6 9l6 6 6-6"/></svg>';
    parts.push(chevron);
    this.button.replaceChildren(...parts);
  }

  private renderMenu(): void {
    const list = document.createElement('div');
    list.className = 'dropdown-list';
    for (const option of this.options) {
      const item = document.createElement('button');
      item.type = 'button';
      item.className = `dropdown-item${option.value === this.current ? ' selected' : ''}`;
      item.append(Object.assign(document.createElement('span'), { className: 'dropdown-label', textContent: option.label }));
      if (option.hint) item.append(Object.assign(document.createElement('span'), { className: 'dropdown-hint', textContent: option.hint }));
      if (option.badge) item.append(Object.assign(document.createElement('span'), { className: 'dropdown-badge', textContent: option.badge }));
      item.addEventListener('click', () => {
        this.current = option.value;
        this.renderButton();
        this.renderMenu();
        this.close();
        this.onChange(option.value);
      });
      list.append(item);
    }
    this.menu.replaceChildren(list, ...(this.footer ? [this.footer] : []));
  }

  toggle(): void {
    if (this.menu.hidden) this.open();
    else this.close();
  }

  open(): void {
    document.querySelectorAll<HTMLElement>('.dropdown-menu').forEach((m) => (m.hidden = true));
    this.menu.hidden = false;
    this.element.classList.add('open');
    this.fit();
    this.menu.querySelector<HTMLElement>('.selected')?.scrollIntoView({ block: 'nearest' });
  }

  /** Menü nie abschneiden: Richtung und Höhe an den freien Platz im Fenster (unter der Titelleiste) anpassen. */
  private fit(): void {
    const rect = this.button.getBoundingClientRect();
    const top = document.querySelector('.topbar')?.getBoundingClientRect().bottom ?? 0;
    const gap = 18;
    const above = rect.top - top - gap;
    const below = window.innerHeight - rect.bottom - gap;
    const up = this.opts.up ? above >= 220 || above >= below : below < 220 && above > below;
    this.menu.classList.toggle('up', up);
    const list = this.menu.querySelector<HTMLElement>('.dropdown-list');
    if (!list) return;
    const extra = this.menu.offsetHeight - list.offsetHeight;
    list.style.maxHeight = `${Math.max(96, Math.min(300, (up ? above : below) - extra))}px`;
  }

  close(): void {
    this.menu.hidden = true;
    this.element.classList.remove('open');
  }
}
