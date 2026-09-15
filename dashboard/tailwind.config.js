/** @type {import('tailwindcss').Config} */

export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    container: {
      center: true,
    },
    extend: {
      fontFamily: {
        display: ['Sora', 'PingFang SC', 'Microsoft YaHei', 'sans-serif'],
        mono: ['JetBrains Mono', 'SFMono-Regular', 'Menlo', 'monospace'],
        sans: [
          'Sora',
          'PingFang SC',
          'Hiragino Sans GB',
          'Microsoft YaHei',
          'sans-serif',
        ],
      },
      colors: {
        // 语义色随 html.light/.dark 切换（值见 src/index.css 的 CSS 变量）
        slate: {
          100: 'rgb(var(--slate-100) / <alpha-value>)',
          200: 'rgb(var(--slate-200) / <alpha-value>)',
          300: 'rgb(var(--slate-300) / <alpha-value>)',
          400: 'rgb(var(--slate-400) / <alpha-value>)',
          500: 'rgb(var(--slate-500) / <alpha-value>)',
          600: 'rgb(var(--slate-600) / <alpha-value>)',
        },
        white: 'rgb(var(--overlay) / <alpha-value>)',
        space: {
          DEFAULT: 'rgb(var(--space-900) / <alpha-value>)',
          950: 'rgb(var(--space-950) / <alpha-value>)',
          900: 'rgb(var(--space-900) / <alpha-value>)',
          850: 'rgb(var(--space-850) / <alpha-value>)',
          800: 'rgb(var(--space-800) / <alpha-value>)',
          700: 'rgb(var(--space-700) / <alpha-value>)',
          600: 'rgb(var(--space-600) / <alpha-value>)',
        },
        accent: {
          DEFAULT: 'rgb(var(--accent) / <alpha-value>)',
          dim: 'rgb(var(--accent-dim) / <alpha-value>)',
          soft: 'rgb(var(--accent-soft) / <alpha-value>)',
        },
        ok: '#34D399',
        warn: '#F59E0B',
        danger: '#FB7185',
      },
      boxShadow: {
        glow: '0 0 24px rgba(34, 211, 238, 0.18)',
        'glow-sm': '0 0 12px rgba(34, 211, 238, 0.14)',
        card: '0 8px 32px rgba(0, 0, 0, 0.45)',
      },
      backgroundImage: {
        'grid-faint':
          'linear-gradient(rgba(148,163,184,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(148,163,184,0.05) 1px, transparent 1px)',
      },
      backgroundSize: {
        grid: '32px 32px',
      },
      keyframes: {
        rise: {
          '0%': { opacity: '0', transform: 'translateY(14px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        pulseDot: {
          '0%, 100%': { opacity: '1', boxShadow: '0 0 0 0 rgba(52,211,153,0.5)' },
          '50%': { opacity: '0.75', boxShadow: '0 0 0 5px rgba(52,211,153,0)' },
        },
        breathe: {
          '0%, 100%': { opacity: '0.5' },
          '50%': { opacity: '1' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
      },
      animation: {
        rise: 'rise 0.55s cubic-bezier(0.22, 1, 0.36, 1) both',
        pulseDot: 'pulseDot 2s ease-in-out infinite',
        breathe: 'breathe 2.4s ease-in-out infinite',
        shimmer: 'shimmer 1.8s linear infinite',
      },
    },
  },
  plugins: [],
}
