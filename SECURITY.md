# Security Policy

## Reporting a Vulnerability

Please report security issues **privately** — do not open a public GitHub issue for a
vulnerability.

- Use GitHub's [private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
  ("Report a vulnerability" on the Security tab), or
- contact the maintainer directly.

Please include a description, reproduction steps and impact. We aim to acknowledge reports
within a few days.

## Operational hardening checklist

Because this bot processes payments and holds a Remnawave admin token, deployments should:

- Keep **all secrets out of git** — `BOT_TOKEN`, `REMNAWAVE_TOKEN`, `DASHBOARD_TOKEN`,
  `CRYPTOBOT_TOKEN`, `PLATEGA_SECRET`, `DB_PASSWORD`. They belong only in `.env` (git-ignored).
- Set a **strong, unique `DB_PASSWORD`** — there is no default; the stack refuses to start without it.
- Never expose the **admin dashboard** (port 8080) directly to the internet. It is bound to
  `127.0.0.1` by default — publish it only behind a TLS-terminating reverse proxy (nginx/caddy)
  and restrict access at the network level.
- Rotate the Remnawave API token periodically and scope it to the minimum permissions required.
- Keep dependencies current (Dependabot is enabled) and triage CodeQL alerts.
