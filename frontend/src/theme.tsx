import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { Icon } from './components/icons';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'it_theme';

function readSaved(): Theme | null {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved === 'light' || saved === 'dark' ? saved : null;
  } catch {
    return null;
  }
}

const systemTheme = (): Theme =>
  window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';

interface ThemeState { theme: Theme; toggle: () => void }
const ThemeContext = createContext<ThemeState | null>(null);

/**
 * Light/dark theme. The choice is saved per browser; until the user picks one we follow the OS setting.
 * The actual colours live in styles.css as CSS variables under [data-theme="light"] / [data-theme="dark"].
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() => readSaved() ?? systemTheme());

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  // Follow OS changes only while the user hasn't chosen explicitly
  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)');
    if (!mq) return;
    const onChange = () => { if (!readSaved()) setTheme(mq.matches ? 'dark' : 'light'); };
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);

  const toggle = useCallback(() => {
    setTheme((current) => {
      const next: Theme = current === 'dark' ? 'light' : 'dark';
      try { localStorage.setItem(STORAGE_KEY, next); } catch { /* private mode */ }
      return next;
    });
  }, []);

  return <ThemeContext.Provider value={{ theme, toggle }}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeState {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used inside <ThemeProvider>');
  return ctx;
}

export function ThemeToggle() {
  const { theme, toggle } = useTheme();
  const label = theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode';
  return (
    <button className="icon-btn theme-toggle" onClick={toggle} aria-label={label} title={label}>
      <span className={`theme-icon ${theme === 'dark' ? 'is-hidden' : ''}`}><Icon name="moon" size={17} /></span>
      <span className={`theme-icon ${theme === 'light' ? 'is-hidden' : ''}`}><Icon name="sun" size={17} /></span>
    </button>
  );
}
