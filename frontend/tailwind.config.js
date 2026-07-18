/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        neon: {
          cyan: "#22d3ee",
          magenta: "#f0f",
          pink: "#ff2e97",
          violet: "#a855f7",
          amber: "#f59e0b",
          lime: "#a3e635",
        },
        void: {
          900: "#05060f",
          800: "#0a0f1e",
          700: "#0f172a",
        },
      },
      fontFamily: {
        mono: ["'JetBrains Mono'", "'Fira Code'", "ui-monospace", "monospace"],
      },
      boxShadow: {
        neon: "0 0 12px rgba(34,211,238,0.6), 0 0 32px rgba(34,211,238,0.25)",
        "neon-pink": "0 0 12px rgba(255,46,151,0.6), 0 0 32px rgba(255,46,151,0.25)",
      },
      keyframes: {
        flicker: {
          "0%,100%": { opacity: "1" },
          "45%": { opacity: "0.85" },
          "50%": { opacity: "0.4" },
          "55%": { opacity: "0.9" },
        },
        scan: {
          "0%": { transform: "translateY(-100%)" },
          "100%": { transform: "translateY(100%)" },
        },
      },
      animation: {
        flicker: "flicker 4s infinite",
        scan: "scan 6s linear infinite",
      },
    },
  },
  plugins: [],
};
